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

После выделения специализированных runtime owners, InstallationRuntimeOwner, ResourceRuntimeOwner и ScrollCooldownRuntimeOwner в `RaskolClasses.activeTasks` остаются три периодические задачи:

| Задача | Текущий владелец | Интервал / назначение | Вывод |
|---|---|---|---|
| Resource tick | `ResourceRuntimeOwner` | 20 ticks; обновление ресурсов | Реализовано; ResourceService остаётся Listener и владельцем resource state/persistence |
| Installation sweep | одновременно root handle и `InstallationService.sweepTask` | 20 ticks; expiry/tick установок | Дублированное владение handle; выбрать один lifecycle owner |
| Scroll cooldown display | `ScrollCooldownRuntimeOwner` | 10 ticks; отображение cooldown на предметах | Реализовано; owner отменяет producer и очищает кэши `ScrollCooldownTask` |
| ManaSoaked | root `activeTasks` | 20 ticks; пассивный резист магов | Нужен domain-specific owner; mage state остаётся в ManaSoakedService |
| Global purge | root `activeTasks` через `PluginTasks` | конфигурируемый интервал | Cross-domain maintenance; не переносить в owner одного домена |
| Global autosave | root `activeTasks` через `PluginTasks` | конфигурируемые минуты | Cross-storage coordinator; должен остановиться до финальных snapshots и закрытия writer |

Текущие владельцы Pet, HUD, BossBar, HP Bar, HP attribute sweep, PlaceholderAPI, Crown Flavor, DoT, CC, Cooldown, Foliant и CCFeedback в этом списке не дублируются: их recurring producer и/или listener registration уже перенесены в специализированные owners.

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

### 2.4 ManaSoakedService

- `start()` возвращает repeating BukkitTask в root.
- `mageIds` и `lastScanTick` — внутреннее runtime состояние механики; при quit/offline UUID удаляются во время рабочего tick или очистки ResistService.
- Рекомендация: owner управляет периодическим producer и close; сервис сохраняет расчёт модификаторов и mage tracking. Не объединять с ResourceRuntimeOwner только потому, что ManaSoaked читает ресурс.

### 2.5 PluginTasks: global purge и autosave

- Purge вызывает несколько независимых доменов и ability facades.
- Autosave координирует cooldown, resources и spec2 storage.
- Это cross-domain orchestration, а не ответственность одного доменного сервиса.
- Рекомендация: пока оставить в composition root как два явно названных maintenance producer, зарегистрированных в root TaskScope или отдельном узком maintenance owner. Не переносить в ResourceService/CooldownManager/Spec2Service.
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

Подтверждены следующие схемы:

- `HpBarService.flushDirty()`: writer completion → Bukkit main-thread callback → generation check / повторный flush.
- `FoliantService.saveLocks()`: writer completion → Bukkit main-thread callback → dirty-generation acknowledgement.
- `ResourceService.persistAsync()`: writer completion → main-thread completion при необходимости.
- `CooldownManager.persistAsync()`: writer completion только логирует результат.
- `FoliantService` также ставит отдельный one-shot main-thread callback после LuckPerms class transition для проверки результата смены группы.

Это не обычные gameplay timers. Они являются частью persistence/transition protocols. Ошибка постановки main-thread callback обрабатывается локально; при этом dirty/reset state в целом сохраняется консервативно. Общий StorageWriter должен продолжать закрываться последним.

Рекомендация: не переносить эти callbacks в общий runtime owner механически. Для них нужен отдельный контракт storage drain / completion delivery в A-04. До A-04 сохранить текущий порядок и явно тестировать rejected scheduling, writer completion race и shutdown.

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
3. HP respawn one-shot callback ownership.
4. A-04 storage completion/drain contract для Foliant, HP Bar, Resources и Cooldowns.
5. Завершить repository-wide scan и сверку callback/task ownership.

Порядок может быть скорректирован после полного repository-wide scan и проверки всех remaining task creation sites.

## 6. Acceptance criteria A-03-003.21

- [x] Root recurring task registrations повторно проверены.
- [x] Оставшиеся root-owned periodic tasks перечислены.
- [x] InstallationService duplicate handle ownership подтверждено и устранено через InstallationRuntimeOwner.
- [x] ResourceService periodic producer вынесен в ResourceRuntimeOwner; финальное сохранение следует после остановки producer.
- [x] ScrollCooldownTask producer вынесен в ScrollCooldownRuntimeOwner; остановка producer предшествует очистке его внутренних кэшей.
- [x] HP attribute respawn one-shot callback подтверждён.
- [x] HP Bar, Foliant и Resource storage/main-thread callbacks классифицированы.
- [x] Warlock channel task creation/cancellation paths проверены.
- [x] CCFeedback one-shot cleanup подтверждён как уже owned.
- [ ] Полный repository-wide scan всех Java source завершён.
- [ ] Все найденные task creation sites сверены с владельцами и root shutdown.
- [x] Для Warlock и Installation runtime owners добавлены focused tests на partial scheduling и retryable cancellation.

До закрытия двух scan-критериев этап остаётся **в работе**. Реализованные runtime-срезы меняют только владение задачами и lifecycle; игровые механики и пользовательское поведение намеренно сохранены.
