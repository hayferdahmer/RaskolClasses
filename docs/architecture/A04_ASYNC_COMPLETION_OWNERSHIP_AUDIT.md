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

## 7. Границы этого аудита

- Это source-backed аудит перечисленных storage/economy/user-completion paths, а не заявление о полном формальном доказательстве отсутствия любых async callbacks в репозитории.
- Изменены `SalvageDailyLimitStore`, `SalvageService`, callback в `ItemSub` и тесты двухфазного резерва.
- Формулы наград, дневной лимит и legacy-формат committed-записей YAML сохранены; добавлена секция `pending`.
- `AsyncStorageWriter` не получил Bukkit dependency.
- `main` не изменяется; `BootstrapCoordinator` не активируется.
- C3 выбран и реализован как commit-on-salvage; C2 (надёжная доставка gift note) остаётся отдельной следующей задачей и не менялся.
