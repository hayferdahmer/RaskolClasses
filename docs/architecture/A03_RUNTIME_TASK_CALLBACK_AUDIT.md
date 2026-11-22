# A-03-003.21 — Аудит оставшихся runtime-задач и отложенных callback

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Проверенный HEAD на начало аудита: `af6bd3cd4663e0ef4fbc308cf736b68719edf2cd`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed аудит и поэтапная runtime-реализация; игровые механики не меняются намеренно.
- Область: root lifecycle, оставшиеся root-owned periodic tasks, известные отложенные callback и task-bearing сервисы.

Этот документ фиксирует подтверждённые остатки после A-03-003.20. Он не объявляет полный repository-wide scan завершённым: перед закрытием A-03-003.21 необходимо пройти оставшиеся source-файлы и сверить каждую регистрацию с актуальным владельцем.

## 1. Текущая картина

В таблице сведены периодические producers и их фактические владельцы. После A-03-003.23 все recurring producers имеют отдельного lifecycle owner; root `activeTasks` удалён:

| Задача | Текущий владелец | Интервал / назначение | Вывод |
|---|---|---|---|
| Resource tick | `ResourceRuntimeOwner` | 20 ticks; обновление ресурсов | Реализовано; ResourceService остаётся Listener и владельцем resource state/persistence |
| Installation sweep | `InstallationRuntimeOwner` | 20 ticks; expiry/tick установок | Реализовано; owner закрывается до `InstallationService.shutdown()` |
| Scroll cooldown display | `ScrollCooldownRuntimeOwner` | 10 ticks; отображение cooldown на предметах | Реализовано; owner отменяет producer и очищает кэши `ScrollCooldownTask` |
| Global purge | `PurgeRuntimeOwner` | `performance.purge-interval-ticks` | Реализовано; отдельный cross-domain owner |
| Global autosave | `AutosaveRuntimeOwner` | `storage.autosave-minutes` | Реализовано; producer закрывается до финальных snapshots и writer |

Текущие владельцы Pet, HUD, BossBar, HP Bar, HP attribute sweep, PlaceholderAPI, Crown Flavor, DoT, CC, Cooldown, Foliant и CCFeedback в этом списке не дублируются: их recurring producer и/или listener registration уже перенесены в специализированные owners.

ManaSoaked больше не входит в root `activeTasks`: `ManaSoakedRuntimeOwner` владеет 20-tick producer, а `ManaSoakedService` сохраняет mage tracking и бизнес-логику. Подробности реализации и тестов отражены в этом checkpoint ниже.

## 2. Подтверждённые runtime-задачи и риски

### 2.1 ResourceService

- `startTickTask()` создаёт repeating task и возвращает BukkitTask в root.
- `ResourceService` одновременно хранит mutable resource state, обрабатывает события и выполняет persistence.
- Внутренний `runOnMainThread()` может ставить Bukkit one-shot callback из completion handler общего StorageWriter.
- Рекомендация: выделить `ResourceRuntimeOwner` для периодического producer и listener registration только после проверки текущей startup phase ordering. Сам ResourceService оставить владельцем resource state и persistence.

### 2.2 InstallationService

- Реализовано: `InstallationRuntimeOwner` владеет sweep task через собственный `TaskScope`; Paper scheduling изолирован в `PaperInstallationTaskScheduler`.
- `InstallationService.sweep()` выполняет один цикл expiry/tick и не создаёт BukkitTask; `shutdown()` только завершает активные установки и очищает cooldown maps.
- Root закрывает runtime owner до `installations.shutdown()`; sweep handle удалён из `activeTasks`.
- Сохранены initial delay и period по 20 ticks. Добавлены unit tests на start/close, partial scheduling failure и retryable cancellation.

### 2.3 ScrollCooldownTask

- Root создаёт task object inline и сохраняет только BukkitTask в `activeTasks`.
- Сам объект владеет `barState` и `lastHeld`; карты чистятся для offline игроков во время периодического tick.
- Отдельного `close()`/owner lifecycle у сервиса нет.
- Рекомендация: сохранить объект в composition root и передать producer в `ScrollCooldownRuntimeOwner`; при закрытии остановить producer и очистить внутренние caches. Поведение durability bar и packet cadence не менять.

### 2.4 ManaSoakedService — реализовано

- `ManaSoakedRuntimeOwner` владеет единственным периодическим producer через собственный `TaskScope`; Paper-адаптер сохраняет initial delay и period 20 ticks.
- `ManaSoakedService.tick()` выполняет один цикл механики; сервис остаётся владельцем `mageIds`, `lastScanTick`, проверки класса/маны и модификаторов резиста.
- При shutdown owner сначала останавливает producer, затем вызывает `clearTracking()`; это очищает только tracking-кэш и не меняет правила `ResistService`.
- ManaSoaked намеренно не объединён с `ResourceRuntimeOwner`, хотя читает значение маны.
- Compatibility accessor `RaskolClasses.getManaSoaked()` сохраняет прежний публичный контракт; SpotBugs `EI_EXPOSE_REP` исключён только для этого метода, без глобального подавления правила.

### 2.5 PluginTasks: global purge и autosave

- Purge вызывает несколько независимых доменов и ability facades.
- Autosave координирует cooldown, resources и spec2 storage.
- Это cross-domain orchestration, а не ответственность одного доменного сервиса.
- Реализовано: `PurgeRuntimeOwner` и `AutosaveRuntimeOwner` независимо владеют repeating tasks через собственные `TaskScope`; Paper scheduling изолирован в `PaperPurgeTaskScheduler` и `PaperAutosaveTaskScheduler`.
- Acceptance gate: purge остановлен до очистки доменных state; autosave остановлен до финальных snapshots; общий AsyncStorageWriter закрывается последним.

## 3. Отложенные и одноразовые callback

### 3.1 HP attribute respawn sync

- `HpAttributeSync.onRespawn()` ставит `runTaskLater(..., 2L)`.
- Handle не сохраняется и не принадлежит `HpAttributeSyncRuntimeOwner`.
- Это gameplay callback с Bukkit Player reference.
- Рекомендация: добавить one-shot scheduling port/TaskScope в отдельном узком изменении; закрытие owner отменяет ожидающий callback. Сохранить задержку 2 ticks.

### 3.2 HP bar respawn callback

- `HpBarService.onRespawn()` ставит one-shot `runTask(...)` для применения max health, HP и отображения сердец.
- Это callback после respawn, не периодический producer.
- Рекомендация: учитывать при полном callback ownership HP Bar; не смешивать с storage completion callbacks.

### 3.3 Storage completion callbacks

Source-backed аудит четырёх сервисов и `AsyncStorageWriter` завершён в A-03-003.25: [A03_STORAGE_COMPLETION_CALLBACK_AUDIT.md](A03_STORAGE_COMPLETION_CALLBACK_AUDIT.md).

Ключевые выводы:
- `CompletableFuture.whenComplete` исполняется без фиксированного executor: штатное завершение обычно приходит с storage worker, отказ submit может завершить future inline на вызывающем потоке.
- HP Bar и Foliant явно переносят generation acknowledgement на Bukkit main thread; ResourceService переносит domain completion через `runOnMainThread`; CooldownManager только логирует.
- Dirty/generation ownership остаётся у доменных сервисов. Универсальный StorageCallbackRuntimeOwner не вводится.
- У HP Bar и Foliant подтверждена возможность лишних одинаковых snapshots при нескольких flush до completion; это P2 coalescing, а не доказанная потеря данных.
- ResourceService требует deterministic race tests для reset marker при пересечении старого completion с новым reset.
- Writer закрывается после producers и final snapshots. Timeout/drain semantics остаются A-04.

Acceptance для A-03-003.25 ещё открыт: execution-context tests, Resource reset race tests, решение/coalescing и CI.

### 3.4 Cooldown ready notifications

- Delayed notification tasks хранятся в CooldownManager.
- CooldownRuntimeOwner вызывает `cancelAllTasks()` до unregister listener.
- Это уже правильная граница владения.
- Дополнительная проверка: callback завершившейся notification не удаляет свой handle из map; проверить накопление завершённых handles и безопасное удаление по identity в отдельном тесте/фиксе.

### 3.5 Warlock channel tasks

- Реализовано: `WarlockChannelTaskOwner` владеет one-shot handles по caster UUID; каждый handle регистрируется сразу после schedule.
- Частичный schedule откатывает уже созданные handles; завершение channel освобождает handles в `finally`; root закрывает owner при shutdown.
- Добавлены focused tests на изоляцию caster, retryable cancellation, close retry и нормальное завершение. Сохранение точных тиков и damage cadence проверяется отдельно от ownership tests.

### 3.6 CCFeedback

- Одноразовые cleanup callbacks для TextDisplay уже принадлежат `CCFeedbackRuntimeOwner`.
- Используется identity check, чтобы устаревший callback не удалял заменённую запись.
- Повторно переносить эту часть не требуется.

## 4. Риски shutdown, подтверждённые на root уровне

- Root отменяет оставшиеся `activeTasks` одним `forEach(BukkitTask::cancel)`; если один cancel бросит RuntimeException, последующие handles не будут обработаны этим циклом.
- InstallationService duplicate cancellation устранено: root закрывает InstallationRuntimeOwner, затем сервис очищает активные установки.
- Warlock channel cancellation вынесена в WarlockChannelTaskOwner; полная изоляция shutdown остальных root-owned задач остаётся отдельной задачей.
- Большая часть root cleanup уже имеет локальные try/catch, но последовательность всё ещё не является единым failure-isolated shutdown coordinator.
- `storageWriter.closeAndAwait(...)` остаётся последним инфраструктурным действием — этот порядок сохранить.

Эти пункты не означают, что обычный Paper disable обязательно оставляет задачи активными: Bukkit также очищает plugin-owned scheduler tasks при отключении. Проблема здесь — явное владение, частичный rollback, тестируемость и детерминированная очистка до закрытия зависимостей.

## 5. Рекомендуемая последовательность следующих срезов

1. ManaSoaked runtime ownership.
2. Отдельный maintenance lifecycle для global purge/autosave без переноса cross-domain логики в домены.
3. A-04 storage completion/drain contract для Foliant, HP Bar, Resources и Cooldowns.
5. Завершить repository-wide scan и сверку callback/task ownership.

Порядок может быть скорректирован после полного repository-wide scan и проверки всех remaining task creation sites.

## 6. A-03-003.23 — Global maintenance runtime ownership

- `PluginTasks` больше не планирует Bukkit-задачи и не возвращает `BukkitTask`: он содержит только два callback-цикла `purge()` и `autosave()`.
- `PurgeRuntimeOwner` и `AutosaveRuntimeOwner` — независимые owners с отдельными `TaskScope`, идемпотентным start, запретом restart после неудачного старта/close и retryable cancellation.
- `PaperPurgeTaskScheduler` сохраняет initial delay = period = `RaskolConfig.purgeIntervalTicks()`.
- `PaperAutosaveTaskScheduler` сохраняет initial delay = period = `max(1, storage.autosave-minutes) * 60 * 20` ticks.
- Состав purge сохранён: cooldowns, effects, ability attempts, FX, installations, resists, attributes, character levels, четыре ability facade purge, combat procs и burst log, затем `markPurgeNow()`.
- Состав autosave сохранён и выполняется в исходном порядке: `CooldownManager.saveAll()`, `ResourceService.saveAll()`, `Spec2Storage.save()`.
- Root больше не содержит `activeTasks`; owners создаются и запускаются в `startRuntimeTasks()`.
- Shutdown закрывает global purge и autosave в самом начале, до финальных сохранений сервисов и до `AsyncStorageWriter.closeAndAwait()`.
- Добавлены отдельные focused tests для каждого owner: idempotent start, close, scheduling failure, retryable cancellation и close-before-start.

## 7. Acceptance criteria A-03-003.21

- [x] Root recurring task registrations повторно проверены.
- [x] Оставшиеся root-owned periodic tasks перечислены.
- [x] InstallationService duplicate handle ownership подтверждено и устранено через InstallationRuntimeOwner.
- [x] ResourceService periodic producer вынесен в ResourceRuntimeOwner; финальное сохранение следует после остановки producer.
- [x] ManaSoaked periodic producer вынесен в ManaSoakedRuntimeOwner; tracking очищается после остановки producer.
- [x] ScrollCooldownTask producer вынесен в ScrollCooldownRuntimeOwner; остановка producer предшествует очистке его внутренних кэшей.
- [x] HP attribute respawn one-shot callback подтверждён.
- [x] HP Bar, Foliant и Resource storage/main-thread callbacks классифицированы.
- [x] Warlock channel task creation/cancellation paths проверены.
- [x] CCFeedback one-shot cleanup подтверждён как уже owned.
- [ ] Полный repository-wide scan всех Java source завершён.
- [ ] Все найденные task creation sites сверены с владельцами и root shutdown.
- [x] Для Warlock и Installation runtime owners добавлены focused tests на partial scheduling и retryable cancellation.

До закрытия двух scan-критериев этап остаётся **в работе**. Реализованные runtime-срезы меняют только владение задачами и lifecycle; игровые механики и пользовательское поведение намеренно сохранены.


**CI verification checkpoint:** Spotless Format workflow run `43571814754` завершился успешно после форматирования актуального maintenance среза. Его post-format `mvn -Pdev -B verify` прошёл: 655 тестов, 0 failures/errors/skips, SpotBugs `BugInstance size is 0`, Spotless 480 Java files clean, `BUILD SUCCESS`. Build/Analyze workflow run `43571814792` проверял предшествующий pre-format SHA: Build job успешен (655 тестов), Analyze job ожидаемо остановился на Spotless diff; окончательный CI на последующем commit требуется.


## 8. A-03-003.24 — HP respawn one-shot callback ownership

- Добавлен общий lifecycle-механизм PlayerCallbackRuntimeOwner, который владеет отменяемыми одноразовыми задачами по UUID игрока; два HP-сервиса используют независимые экземпляры, чтобы не объединять их состояния и shutdown.
- PaperPlayerCallbackScheduler — единственная граница Bukkit scheduler; core owner и его tests не зависят от Paper API.
- HpAttributeSync сохраняет задержку 2 ticks. HpBarService сохраняет next-tick execution (delay 0). Оба callback проверяют актуальность игрока перед применением.
- Повторный respawn заменяет предыдущий callback того же сервиса и UUID. Quit пытается отменить ожидающую задачу; ошибка отмены логируется и не прерывает HP persistence на quit.
- Root закрывает оба callback owner до закрытия соответствующих HP listener/runtime owners и до финальных storage snapshots.
- Six focused tests проверяют завершение и освобождение handle, замену устаревшего callback (включая доставку после отмены), quit cancellation, shutdown cancellation/rejection и retry после ошибки отмены.
- Не переносились HP writer completion callbacks: они остаются частью A-04 storage completion/drain contract.
