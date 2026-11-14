# A-03-003.21 — Аудит оставшихся runtime-задач и отложенных callback

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Проверенный HEAD на начало аудита: `af6bd3cd4663e0ef4fbc308cf736b68719edf2cd`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed аудит; игровые механики и runtime-код не изменялись.
- Область: root lifecycle, оставшиеся root-owned periodic tasks, известные отложенные callback и task-bearing сервисы.

Этот документ фиксирует подтверждённые остатки после A-03-003.20. Он не объявляет полный repository-wide scan завершённым: перед закрытием A-03-003.21 необходимо пройти оставшиеся source-файлы и сверить каждую регистрацию с актуальным владельцем.

## 1. Текущая картина

После выделения специализированных runtime owners в `RaskolClasses.activeTasks` остаются шесть периодических задач:

| Задача | Текущий владелец | Интервал / назначение | Вывод |
|---|---|---|---|
| Resource tick | root `activeTasks` | 20 ticks; обновление ресурсов | Нужен отдельный runtime owner; состояние и persistence остаются в ResourceService |
| Installation sweep | одновременно root handle и `InstallationService.sweepTask` | 20 ticks; expiry/tick установок | Дублированное владение handle; выбрать один lifecycle owner |
| Scroll cooldown display | root `activeTasks` | 10 ticks; отображение cooldown на предметах | Нужен owner для producer и закрытия сервиса/cache |
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

- `startSweepTask()` сохраняет BukkitTask в поле `sweepTask` и одновременно возвращает тот же handle в root `activeTasks`.
- Shutdown отменяет задачу через root, а затем вызывает `installations.shutdown()`, где она отменяется повторно.
- `shutdown()` также завершает активные установки и очищает cooldown maps.
- Риск: двойное владение cancellation handle и смешение runtime scheduling с domain cleanup.
- Рекомендация: отдельный `InstallationRuntimeOwner` должен владеть sweep producer; InstallationService — активными установками и их expiry/cleanup. Зафиксировать один путь остановки.

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

- `WarlockAbilities` хранит per-caster списки BukkitTask в instance map, но доступ к shutdown осуществляется через static singleton facade.
- Несколько delayed callbacks и финальный callback создаются во время `soulRift()`.
- Root вызывает `WarlockAbilities.cancelAllChannelTasks()` при shutdown.
- Риск: если создание серии задач завершится исключением до публикации списка в `channelTasks`, уже созданные handles могут остаться вне map. Cancellation paths также завязаны на static facade.
- Рекомендация: выделить per-caster channel task scope/owner; регистрировать каждый handle сразу после создания, обеспечить rollback частичного schedule и retryable close. Сохранить точные тики, damage cadence, CastChannels semantics и визуальные эффекты.

### 3.6 CCFeedback

- Одноразовые cleanup callbacks для TextDisplay уже принадлежат `CCFeedbackRuntimeOwner`.
- Используется identity check, чтобы устаревший callback не удалял заменённую запись.
- Повторно переносить эту часть не требуется.

## 4. Риски shutdown, подтверждённые на root уровне

- Root отменяет оставшиеся `activeTasks` одним `forEach(BukkitTask::cancel)`; если один cancel бросит RuntimeException, последующие handles не будут обработаны этим циклом.
- InstallationService повторно отменяет sweep handle после root cancellation.
- Warlock cancellation вызывается через static facade и не изолирована отдельным owner.
- Большая часть root cleanup уже имеет локальные try/catch, но последовательность всё ещё не является единым failure-isolated shutdown coordinator.
- `storageWriter.closeAndAwait(...)` остаётся последним инфраструктурным действием — этот порядок сохранить.

Эти пункты не означают, что обычный Paper disable обязательно оставляет задачи активными: Bukkit также очищает plugin-owned scheduler tasks при отключении. Проблема здесь — явное владение, частичный rollback, тестируемость и детерминированная очистка до закрытия зависимостей.

## 5. Рекомендуемая последовательность следующих срезов

1. Warlock channel task ownership: наиболее явный риск частично созданных и не опубликованных handles.
2. Installation runtime ownership: устранить двойное владение sweep handle.
3. Resource runtime ownership.
4. Scroll cooldown runtime ownership.
5. ManaSoaked runtime ownership.
6. Отдельный maintenance lifecycle для global purge/autosave без переноса cross-domain логики в домены.
7. HP respawn one-shot callback ownership.
8. A-04 storage completion/drain contract для Foliant, HP Bar, Resources и Cooldowns.

Порядок может быть скорректирован после полного repository-wide scan и проверки всех remaining task creation sites.

## 6. Acceptance criteria A-03-003.21

- [x] Root recurring task registrations повторно проверены.
- [x] Оставшиеся root-owned periodic tasks перечислены.
- [x] InstallationService duplicate handle ownership подтверждено.
- [x] HP attribute respawn one-shot callback подтверждён.
- [x] HP Bar, Foliant и Resource storage/main-thread callbacks классифицированы.
- [x] Warlock channel task creation/cancellation paths проверены.
- [x] CCFeedback one-shot cleanup подтверждён как уже owned.
- [ ] Полный repository-wide scan всех Java source завершён.
- [ ] Все найденные task creation sites сверены с владельцами и root shutdown.
- [ ] Отдельные тестовые требования для callback races и partial scheduling зафиксированы перед implementation slices.

До закрытия последних двух scan-критериев этап остаётся **в работе**. Код и игровые механики этим аудитом не менялись.
