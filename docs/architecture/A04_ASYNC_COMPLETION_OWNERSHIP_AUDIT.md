# A-04 — Аудит владения асинхронными результатами

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Базовый HEAD: `fe853ba9b21579c740820c2220dcff5d06a2d991`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed audit + реализация выбранного C3; экономика и формулы наград не меняются.
- Связанные материалы: `A03_STORAGE_COMPLETION_CALLBACK_AUDIT.md`, `A03_REMAINING_ONE_SHOT_SCHEDULER_AUDIT.md`, `A03_REMAINING_LIFECYCLE_OWNERSHIP_INVENTORY.md`.

Этот этап расширяет ранее выполненный аудит инфраструктурных storage acknowledgements на пользовательские операции, где асинхронный результат влияет на экономику, предметы, уведомления или административную команду. Он не объявляет все callbacks репозитория полностью инвентаризированными.

## 1. Базовый execution-context контракт

`AsyncStorageWriter` исполняет файловые операции в одном FIFO worker. Поток завершения future не фиксирован:

- штатное завершение обычно вызывает callbacks на storage worker;
- отказ постановки задачи может завершить future синхронно на вызывающем потоке;
- submit после начала закрытия возвращает уже завершённый future со значением `false`;
- timeout/interruption drain завершает futures отменённых queued writes в потоке shutdown;
- уже выполняющаяся файловая операция может продолжить работу после timeout.

Следовательно:

1. Нельзя обращаться к Bukkit API из completion callback без явного перехода на Paper main thread.
2. Нельзя считать, что callback всегда будет асинхронным: он может выполниться inline до возврата из метода submit.
3. Durable/domain result и пользовательское уведомление — разные обязательства. Отказ main-thread dispatch не должен отменять уже завершённую покупку/запись.
4. Writer остаётся инфраструктурным: он не получает Bukkit scheduler, Player, CommandSender или доменные зависимости.

## 2. Подтверждённые пользовательские completion paths

### 2.1 PlusGui — покупка подписки

Источник: `gui/PlusGui.handlePurchase(Player, PlusSubscription)`.

- `buyAsync(UUID, 1)` вызывается с UUID, но completion замыкает исходный Bukkit Player.
- Результат переносится через `runTask`; callback проверяет `player.isOnline()`.
- Если игрок вышел или dispatch отклонён, уведомление и повторное открытие GUI теряются.
- Сама покупка не отменяется: `PlusSubscription.buyAsync` уже выполняет списание и сохранение по UUID.

Классификация: операция покупки независима от сессии; отображение результата сейчас привязано к исходному объекту Player. Reconnect не получает результат автоматически.

### 2.2 PlusSub — покупка, подарок, grant/revoke, export

Источник: `command/sub/PlusSub.java`.

- Покупка и подарок используют `whenComplete → runOnMain`, захватывают Player и проверяют `isOnline()`.
- Подарок дополнительно захватывает объект получателя. Если получатель был online при старте, но вышел до обработки результата, отдельная offline note не создавалась, а online notification уже не отправляется.
- `lastGiftMs` обновляется только в main-thread completion после успешного подарка. Если отправитель вышел до callback, cooldown не фиксируется в этом map.
- Административные grant/revoke и export захватывают `CommandSender`; grant/revoke также используют объект target Player для отображаемого имени. Completion переносится на main thread, но не имеет session-aware delivery policy.
- `runOnMain` ловит `IllegalStateException` и пишет warning; результат операции при этом уже может быть durable и не повторяется.

Классификация:
- покупка/подарок/grant/revoke — доменные операции, которые должны завершать persistence независимо от online-сессии;
- ответы инициатору — уведомления, для которых нужен отдельный reconnect/session contract;
- подарок получателю — самостоятельная доставка, которую нельзя считать успешной только потому, что срок подписки сохранён;
- cooldown подарка — бизнес-состояние, сейчас обновляемое слишком поздно, в UI completion callback.

### 2.3 PlusSubscription — consume gift note on join

Источник: `spec/build/PlusSubscription.onJoin(PlayerJoinEvent)`, `PlusStorage.consumeGiftNoteAsync(UUID)`.

- На join вызывается асинхронное удаление gift note из хранилища.
- После успешной записи note уже удалена из persistent store и memory cache.
- Затем completion планирует Bukkit task, захватывая исходный Player; внутри проверяется `player.isOnline()`.
- Если игрок успел выйти/перезайти или scheduler отверг задачу после успешного consume, note уже удалена, но сообщение не доставлено. Повторного восстановления note нет.

**P1 — подтверждённый риск потери подарочного уведомления** между durable consume и фактической доставкой. Подписка получателя остаётся сохранённой; теряется именно уведомление.

### 2.4 SalvageService — резерв дневного лимита и разбор предмета

Источник: `item/craft/SalvageService.salvage(Player, Consumer)`, `SalvageDailyLimitStore.tryConsume(...)`.

- Сначала асинхронно сохраняется увеличенный дневной счётчик.
- Только после успешной записи планируется main-thread callback, который проверяет исходный Player, исходный предмет и выполняет списание предмета/выдачу фрагментов.
- Если игрок offline к моменту callback, метод просто возвращается: дневной лимит уже увеличен, но разбор не выполнен.
- Если main-thread dispatch отвергнут, исключение логируется; reservation также остаётся сохранённым.
- Если предмет изменился, лимит остаётся потреблённым; пользовательское сообщение явно говорит «Попытка сохранена». Это может быть намеренной anti-abuse политикой и не должно меняться без решения.
- Если callback выполнился, но inventory/grant operation выбросила исключение посередине, отдельного rollback reservation/item transaction здесь не видно.

**P1 — контракт не определён.** При disconnect или rejected dispatch возникает несогласованность «лимит потрачен, предмет не разобран». Нужна явная политика: попытка считается использованной сразу после reservation либо reservation должна быть подтверждена/освобождена после main-thread commit.

### 2.5 BuildTemplates export

Источник: `spec/build/BuildTemplates.exportNormalizedAsync()`, caller в `PlusSub.cmdExport`.

- YAML snapshot формируется до передачи в writer.
- Completion команды возвращается через `runOnMain` и захватывает CommandSender.
- Сама запись файла не зависит от отправителя; при shutdown/rejected dispatch теряется только подтверждающее сообщение.
- Изменение загруженной in-memory модели происходит до экспорта и не требует worker-side Bukkit API.

Классификация: persistence operation независима от sender session; сообщение — best effort.

## 3. Связанные storage callbacks, уже покрытые A-03

Предыдущий аудит `A03_STORAGE_COMPLETION_CALLBACK_AUDIT.md` остаётся действующим для:

- `HpBarService`: generation-based dirty acknowledgement; main-thread dispatch failure оставляет dirty state.
- `FoliantService`: generation-based acknowledgement; producer закрывается до финального snapshot; acknowledgement после disable не требуется для durable write.
- `ResourceService`: main-thread completion consumers и per-player reset generation; сохранять domain-specific reset protocol.
- `CooldownManager`: log-only completion, без Bukkit/state mutation.
- `Spec2Storage`: worker completion обновляет только atomic persisted-version и логирует ошибку; Bukkit API не используется.
- `AsyncStorageWriter`: callback thread contract и shutdown drain semantics описаны выше.

Эти callbacks не следует механически переносить в один универсальный owner: generation, reset marker и dirty acknowledgement — часть контрактов конкретных хранилищ.

## 4. Рекомендуемые контракты до реализации

### C1 — Долговечные операции не зависят от Player session

Покупка, подарок, grant/revoke и сохранение шаблонов завершаются по UUID/снимку независимо от disconnect. Player object не должен удерживаться до completion. UI feedback — отдельный best-effort этап.

### C2 — Доставка gift note должна быть подтверждённой

Не удалять note окончательно до того, как main-thread delivery path подтвердил возможность доставки текущей online-сессии; если игрок offline или dispatch отклонён, note должна оставаться доступной для следующего join. Реализация должна исключить повторную доставку при гонке join/reconnect.

### C3 — Salvage reservation имеет явный commit policy

Выбрать один из вариантов:
- **Attempt-charged:** успешная durable reservation считается использованной попыткой даже при disconnect/rejected dispatch/item change. Это просто и anti-abuse, но должно быть явно сообщено игроку и протестировано.
- **Commit-on-salvage:** reservation не становится окончательным расходом до успешного изменения inventory и выдачи фрагментов; disconnect/rejected dispatch/item change освобождает reservation. Это требует versioned reserve/commit/release protocol, а не простого уменьшения YAML-счётчика без защиты от гонок.

### C4 — Уведомления адресуются по устойчивому ключу

Для player-facing результатов хранить UUID и разрешать текущую online Player на main thread. Для командных ответов сохранять только минимальные данные; не захватывать Player/target Player без необходимости. Нужно определить, следует ли доставлять результат инициатору после reconnect или достаточно логирования/сообщения только текущей сессии.

### C5 — Shutdown не обещает доставку Bukkit callbacks

После начала disable writer может завершить durable работу, но main-thread notification/acknowledgement может быть отклонён. Shutdown должен логировать недоставленные пользовательские результаты; он не должен запускать Bukkit API из worker и не должен откатывать уже durable domain operation только из-за невозможности показать сообщение.

## 5. Рекомендуемая последовательность реализации

1. Зафиксировать C1–C5 как продуктовые/доменные решения.
2. Исправить gift-note delivery как отдельный persistent claim/ack flow; добавить тесты disconnect, reconnect, rejected scheduler и exactly-once semantics.
3. Исправить PlusSub/PlusGui notification dispatch: UUID-based current-session resolution, корректное обновление gift cooldown в момент принятия/успешного выполнения операции, независимость durable result от UI.
4. Реализовать Salvage reservation contract и deterministic tests для disconnect, item change, rejected dispatch, write failure и повторной попытки.
5. Проверить командные grant/revoke/export callbacks и shutdown observability.
6. Запустить Build, Analyze, Spotless на одном финальном HEAD; затем выполнить live Paper 26.2 smoke tests.

## 6. Решение C3 и реализация commit-on-salvage

**Выбранная политика: commit-on-salvage.** Дневная попытка окончательно расходуется только после успешного изменения инвентаря и выдачи фрагментов.

Реализован двухфазный протокол в `SalvageDailyLimitStore`:

1. `reserve` записывает durable pending-запись с уникальным operation ID. Pending учитывается при проверке лимита, но ещё не увеличивает committed count.
2. На Paper main thread заново разрешается Player по UUID, проверяется online-сессия и сравнивается предмет в руке с исходным снимком.
3. Если игрок вышел до выполнения, предмет изменился или main-thread dispatch отклонён, вызывается `release`. Успешное освобождение возвращает попытку в лимит.
4. После изменения предмета и выдачи фрагментов вызывается `commit`: pending удаляется, committed count увеличивается.
5. Если финализация commit не удалась, durable pending остаётся и продолжает занимать попытку; это предотвращает повторный разбор после перезапуска. Ошибка явно логируется.

**Граница транзакционности:** Bukkit inventory и YAML-файл не образуют единую атомарную транзакцию. Если JVM аварийно завершится после durable reserve, но до release/commit, pending после рестарта консервативно продолжит занимать попытку. Это намеренная fail-closed политика против дублирования награды; обычный disconnect, изменение предмета и rejected dispatch освобождают резерв. При ошибке освобождения резерв также остаётся занятым до ручного/операционного восстановления.

Существующий формат committed-записей `UUID.day/count` сохранён; pending хранится в отдельной секции `pending`. Дневной лимит и формулы наград не менялись. Completion команды больше не удерживает исходный Player: инициатор повторно разрешается по UUID.

### Проверки

- committed count переживает пересоздание хранилища;
- release не расходует попытку и позволяет повторный reserve;
- pending переживает рестарт и учитывается против лимита;
- новый день начинает отдельный счётчик;
- ошибка сохранения блокирует reserve fail-closed.


## 7. C2 — подтверждённая доставка gift note

**Контракт:** подписка и списание за подарок сохраняются независимо от онлайн-сессии получателя; уведомление не удаляется до успешного вызова Player.sendMessage на Paper main thread и последующего durable acknowledgement.

### Реализация

1. PlusStorage хранит gift-note вместе с уникальным gift-note-id. Новые подарки сохраняют уведомление независимо от того, online ли получатель в момент начала операции.
2. Для старых YAML-записей без ID вычисляется стабильный legacy ID из UUID игрока и текста уведомления. Формат gift-note остаётся строковым; добавляется только соседнее поле gift-note-id.
3. getGiftNoteAsync(UUID) читает note без удаления. Чтение ожидает завершения уже выполняющейся записи, чтобы не вернуть устаревший снимок.
4. PlusSubscription запускает доставку после успешного сохранения подарка и при PlayerJoinEvent. На main thread игрок заново разрешается по UUID; исходный Player не удерживается между async этапами.
5. Перед отправкой используется in-memory delivery state по UUID + note ID. Параллельные join/reconnect callbacks в одном процессе не отправляют одно и то же уведомление повторно.
6. Только после вызова sendMessage выполняется acknowledgeGiftNoteAsync(UUID, expectedNoteId). Ack удаляет запись только если ID всё ещё совпадает. Старый callback не может удалить более новое уведомление.
7. Если scheduler недоступен, игрок успел выйти до отправки или YAML ack не удался, note остаётся в persistent storage. При следующем join/повторной попытке она снова рассматривается; если сообщение уже отправлено в этом процессе, повторная отправка блокируется сохранённым in-memory delivery state.
8. Ack ожидает завершения текущей записи PlusStorage, вместо того чтобы теряться из-за writeInFlight.

PlusSub больше не отправляет отдельное прямое сообщение объекту получателя: уведомление получателя проходит через единый durable delivery path. В completion команды остаются только UUID и имя получателя.

### Граница exactly-once

Minecraft chat delivery и YAML write не образуют одну транзакцию. Между вызовом sendMessage и durable ack остаётся аварийное окно: если JVM завершится именно в этот момент, после рестарта сохранённая note может быть отправлена повторно. В рамках одного работающего процесса гонка join/reconnect подавляется; абсолютная exactly-once доставка через crash boundary без клиентского idempotency token или транзакционного транспортного подтверждения недостижима. Выбран fail-safe приоритет: не терять уведомление из-за scheduler/write failure, допуская редкий повтор после аварии.

Сохраняется прежняя модель одного pending gift note на игрока: более новый подарок заменяет ранее ожидавший note, как и до этого изменения. Очередь нескольких подарочных сообщений не вводилась.

### Регрессионные проверки

- чтение не удаляет note;
- неверный ID не подтверждает и не удаляет note;
- правильный ID удаляет note durable;
- ID переживает reload хранилища;
- старый YAML без ID читается и подтверждается;
- ack failure оставляет note доступной;
- устаревший ack не удаляет note от следующего подарка.

## 8. Границы этого аудита

- Это source-backed аудит перечисленных storage/economy/user-completion paths, а не заявление о полном формальном доказательстве отсутствия любых async callbacks в репозитории.
- Изменены `SalvageDailyLimitStore`, `SalvageService`, callback в `ItemSub`, `PlusStorage`, `PlusSubscription`, `PlusSub` и тесты reservation/delivery.
- Формулы наград, дневной лимит и legacy-формат committed-записей YAML сохранены; добавлена секция `pending`.
- `AsyncStorageWriter` не получил Bukkit dependency.
- `main` не изменяется; `BootstrapCoordinator` не активируется.
- C3 выбран и реализован как commit-on-salvage; C2 реализован отдельным persistent read/ack flow (см. раздел 7).


## 8. C4 — доставка результата только в исходную сессию

**Выбранная политика:** инициатор получает уведомление только в той Bukkit-сессии, из которой была принята команда/покупка. После disconnect и reconnect уведомление старой операции не переносится в новую сессию; состояние durable операции проверяется отдельной командой `/rc plus status`. Подарочная note получателя остаётся независимым C2 read/ack flow.

### Реализация

- `PlusSub` сохраняет для игрока UUID и слабую ссылку на исходный объект Player. На Paper main thread текущий Player разрешается по UUID и сравнивается по identity с исходным объектом. Это отличает reconnect от исходной сессии, не удерживая Player сильной ссылкой в async completion.
- Для консольного отправителя сохраняется только признак console; на main thread разрешается текущий Bukkit console sender. Иные не-player/non-console отправители не удерживаются и не получают отложенный ответ.
- Grant/revoke/export сохраняют только UUID/имя целевого игрока и минимальные данные операции; Bukkit Player и исходный CommandSender не захватываются в async callback.
- `PlusGui` применяет ту же session-identity проверку: callback после выхода/перезахода не открывает GUI и не отправляет сообщение новой сессии.
- При offline/reconnect или rejected scheduler результат не теряется и не повторяется автоматически: durable операция остаётся завершённой согласно своему контракту, а инициатор проверяет состояние через `/rc plus status`. Недоставленное уведомление логируется.
- Gift cooldown фиксируется сразу после проверки аргументов и принятия корректной команды, до запуска async gift operation. Он не зависит от того, успел ли UI completion выполниться. Невалидные команды cooldown не расходуют.

### Ограничения

WeakReference нужна только для сравнения identity; online Player удерживается самим сервером. Если исходная сессия завершилась, weak reference может стать пустой, и callback безопасно подавляется. Это намеренно fail-closed. Gift note адресата остаётся C2 механизмом с его отдельной crash-boundary exactly-once оговоркой.


## 9. C5 — shutdown-aware async completion delivery

**Контракт:** durable операции продолжают жить по своим storage/domain правилам; пользовательские Bukkit callbacks являются best effort и не запускают Bukkit API из storage worker. Если callback нельзя доставить из-за disable или scheduler rejection, операция не откатывается, а факт недоставки фиксируется в журнале. Исключение — инфраструктурная компенсация salvage pending reservation: её undelivered handler освобождает резерв, пока общий StorageWriter ещё открыт.

### Реализация

- Добавлен ShutdownAwareCompletionDispatcher: он регистрирует каждый отложенный callback до передачи Paper scheduler, удаляет его при выполнении и подавляет повторное выполнение.
- При закрытии dispatcher все ещё ожидающие callbacks логируются по operation label, не исполняются и получают свой onUndelivered handler. Повторный close безопасен.
- Отказ Paper scheduler обрабатывается тем же путём: callback не исполняется, operation label и причина записываются, вызывается инфраструктурный handler.
- Dispatcher закрывается первым этапом onDisable, до остановки runtime producers и до финального drain общего StorageWriter. Поздние async completions видят закрытый dispatcher и не пытаются обращаться к Bukkit scheduler.
- PlusSub, PlusGui и PlusSubscription переводят пользовательские результаты и gift-note delivery на общий dispatcher. C4 session identity сохраняется; закрытие плагина не переносит сообщения на reconnect.
- SalvageService использует dispatcher для main-thread commit и результата. Если commit callback не доставлен из-за shutdown/scheduler failure, undelivered handler пытается освободить durable pending reservation. Если общий writer уже перестал принимать записи, освобождение fail-closed: pending остаётся, а ошибка явно логируется.
- AsyncStorageWriter остаётся Bukkit-независимым. Его completion futures по-прежнему могут завершаться на worker, submitter или shutdown thread; callbacks обязаны использовать dispatcher перед любым Bukkit API.

### Границы и гарантии

- Закрытие dispatcher предотвращает запуск уже поставленных, но ещё не начавшихся callbacks после начала disable. Уже исполняемый main-thread callback не может быть прерван этим механизмом.
- При shutdown pending notifications логируются по имени операции; durable purchase/grant/revoke/export не откатываются из-за отсутствия сообщения.
- Gift note не подтверждается без успешного main-thread send; если её callback отменён shutdown, persistent note остаётся доступной для следующей сессии.
- Salvage release при shutdown best effort и зависит от того, что StorageWriter ещё принимает запись. При ошибке/тайм-ауте сохраняется fail-closed pending reservation, а не риск повторной награды.
- При timeout общего StorageWriter queued writes отменяются, а выполняющийся I/O может не завершиться к моменту возврата shutdown. Финальные снимки и durable callbacks не получают обещания успешной записи только на основании вызова submit.

### Регрессионные проверки

- закрытие dispatcher подавляет queued callback и вызывает undelivered handler ровно один раз;
- повторное закрытие безопасно;
- dispatch после close отклоняется и вызывает undelivered handler;
- scheduler rejection не исполняет callback, очищает pending registry и вызывает undelivered handler;
- штатный callback выполняется один раз и удаляется из pending registry;
- live Paper 26.2 + Java 25 smoke test остаётся обязательным для проверки фактического disable/scheduler поведения.
