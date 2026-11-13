# A-03-003.10 — Remaining Lifecycle Ownership Inventory

## Статус и границы

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed inventory; без изменения runtime/gameplay.
- Основной источник композиции: `src/main/java/dev/raskol/classes/RaskolClasses.java`.

Инвентаризация фиксирует наблюдаемые точки создания, запуска, регистрации и остановки. Это не утверждение, что перечислены абсолютно все внутренние Bukkit-задачи: для этого потребуется отдельный проход по каждому сервису и его зависимостям. В частности, отложенные одноразовые задачи и задачи, создаваемые во время игрового процесса, помечены отдельно от корневых периодических задач.

## 1. Уже выделенные владельцы

| Компонент | Текущее владение | Подтверждённое поведение | Остаток |
|---|---|---|---|
| Runtime RIPS | `RuntimeIntegrityRuntimeOwner` | Отдельный daemon executor, monitor, reporter/state; запускается после успешного startup и закрывается в начале shutdown | Bukkit disable callback остаётся инъекцией из root |
| Pet runtime | `PetRuntimeOwner` | Listener + два периодических задания; despawn → cancel → unregister; rollback и retry | Реализация и тесты есть; CI #3081 success |
| HUD runtime | `HudRuntimeOwner` и Paper adapters существуют | Контракт owner, task scheduler/session-event adapters, тесты | Не подключён к production wiring; старые HudService/BossBarService/HpBarService остаются root-owned |
| HP attribute sync | `HpAttributeSyncRuntimeOwner` | Listener registration + 100-tick sweep; rollback, retryable cancellation/unregistration | Delayed respawn sync остаётся одноразовой задачей без отдельного handle |
| PlaceholderAPI | `PlaceholderRuntimeOwner` | Пятитиковый snapshot producer + регистрация двух expansions; rollback и retryable cleanup | Snapshot data остаётся в `PlaceholderSnapshotService`; Paper adapter владеет expansion instances |
| Crown flavor | `FlavorRuntimeOwner` | Listener registration + 40-tick aura producer; rollback и retryable cancellation/unregistration | `CrownFlavorService` сохраняет faction/title lookup и aura tick logic |

Важно: наличие owner-класса не означает, что production lifecycle уже переведён на него.

## 2. Корневые периодические задачи

Точки запуска в `RaskolClasses.startRuntimeTasks()`:

| Задача | Создатель / сервис | Текущий владелец handle | Фактические обязанности и риск |
|---|---|---|---|
| DoT tick | `combat.dots().startTickTask()` | `activeTasks` | DoT runtime; listener регистрируется отдельно в `initializeCoreServices` |
| CC tick | `ccService.startTickTask()` | `activeTasks` | CC runtime; listener регистрируется отдельно |
| HUD ActionBar | `hud.start()`, только при `hud.enabled && hp-display.mode=vanilla` | `activeTasks` | Конфигурационно-условный producer; reload меняет config, но не пересоздаёт задачу |
| HP bar tick | `hpBarService.start()` | `activeTasks` | HP runtime/UI и часть HP domain; сам сервис также Listener и владелец health persistence |
| Resource tick | `resources.startTickTask(this)` | `activeTasks` | Resource runtime; сервис также Listener и хранит изменяемое состояние |
| BossBar tick | `bossBars.start()` | `activeTasks` + внутренняя ссылка `task` в сервисе | BossBar rendering; `applyConfig()` выполняет shutdown/start |
| Flavor aura | `FlavorRuntimeOwner.start()` | `FlavorRuntimeOwner` / `TaskScope` | Сохраняет исходный 40-tick период; listener регистрируется тем же owner до запуска producer |
| Installation sweep | `installations.startSweepTask()` | `activeTasks` | Очистка/обслуживание runtime установок; сервис отдельно участвует в gameplay |
| Scroll cooldown | `new ScrollCooldownTask(this).start()` | `activeTasks` | Отдельный task object теряется после возврата handle; владелец — root |
| ManaSoaked | `manaSoaked.start()` | `activeTasks` | Периодическая механика combat |
| Global purge | `PluginTasks.startPurgeTask(this)` | `activeTasks` | Один callback вызывает purge многих доменов и статических ability-классов; широкий cross-domain orchestration |
| Global autosave | `PluginTasks.startAutosaveTask(this)` | `activeTasks` | Плановый snapshot cooldowns/resources/spec2; обязан завершиться до закрытия общего writer |
| Foliant lock flush | `FoliantRuntimeOwner.start()` в `initializeCoreServices` | `FoliantRuntimeOwner` / `TaskScope` | Owner управляет listener и периодическим flush producer; FoliantService сохраняет dirty lock state и persistence |
| Pet tick + orphan scan | `PetRuntimeOwner.start()` | `PetRuntimeOwner` / `TaskScope` | Уже выведены из root списка |

`HpAttributeSync` больше не хранит sweep handle: его периодическая задача создаётся через `PaperHpAttributeSyncScheduler` и принадлежит `HpAttributeSyncRuntimeOwner`. Отложенная задача respawn (2 ticks) остаётся внутренней одноразовой задачей Bukkit scheduler без отдельного cancellation handle; это явно оставлено за пределами текущего среза.

## 3. Внутренние / одноразовые задачи

| Компонент | Источник | Владение и остановка | Замечание |
|---|---|---|---|
| Cooldown ready notifications | `CooldownManager.start(...)` | Map задач остаётся внутри CooldownManager; runtime owner вызывает `cancelAllTasks()` при остановке | Listener registration и lifecycle координирует `CooldownRuntimeOwner`; состояние и persistence остаются в CooldownManager |
| HP async completion callbacks | `HpBarService.saveHealth()` | Writer completion + Bukkit main-thread callback | Это callbacks, не периодический producer; должны завершаться до/в рамках storage drain contract |
| Foliant async completion / deferred operations | `FoliantService` | Внутри сервиса и общий writer; dirty locks сохраняются root-вызовом | Нужна отдельная проверка каждого deferred callback перед переносом |
| Warlock channel tasks | `WarlockAbilities` | Статический `cancelAllChannelTasks()` вызывается root | Глобальное/static владение усложняет модульную границу; необходимо инвентаризировать task creation/cancel paths |
| HP delayed sync | `HpAttributeSync` join handler | Bukkit scheduler one-shot через static scheduler; явного handle в root нет | Нужен анализ безопасного закрытия/гонки с disable |

## 4. Listener и внешние регистрации

### Прямые регистрации в initialize / registerGameplayListeners

- `CombatProfileLifecycleListener` — создаётся inline, ссылка не сохраняется; явного unregister в root нет.
- `HpAttributeSync` — поле root; listener регистрируется через `HpAttributeSyncRuntimeOwner`.
- `Spec2RoleListener` — создаётся inline, явного unregister нет.
- `PlusSubscription`, `PlusGui` — поля root, Bukkit listeners.
- `resources`, `effects`, `cooldowns`, `PassiveListener`, `ClassBook.ClickHandler`, `BindListener`, `TrailListener`, `InstallBindListener`, `combat`, `combat.vanillaListener()`, `combat.avoidance()`, `HunterAbilities.FanArrowHit`, `ScrollSanitizer`, `HpBarService`, `FxService`, `WarlockAbilities` — зарегистрированы через PluginManager. `CrownFlavorService` listener регистрируется/снимается через `FlavorRuntimeOwner`; `FoliantService` — через `FoliantRuntimeOwner`.
- `PassportChangeListener` — собственный reflective registration; root вызывает `unregister()`.
- `SessionListener` — создаётся локально и регистрируется; отдельного close/unregister нет.
- Pet listener — теперь зарегистрирован/отменяется через PetRuntimeOwner.

Bukkit обычно очищает зарегистрированные listeners при выгрузке плагина, однако это не заменяет явный owner lifecycle там, где нужна частичная rollback/повторная очистка или listener имеет отдельные ресурсы.

### Optional / external registrations

- PlaceholderAPI: `PlaceholderRuntimeOwner` управляет 5-tick snapshot producer и обеими expansions; явный unregister выполняется при shutdown.
- RaskolCore passport event: reflective `registerEvent`; явный `HandlerList.unregisterAll(this)` существует.
- RaskolEnchant BlueprintHook: текущий код только обнаруживает plugin и логирует; рецепты не регистрирует, `unregisterBlueprints()` пока no-op.
- PlayerPoints: создаётся wallet adapter или NullWallet; отдельная registration lifecycle в root не видна.
- `PlaceholderSnapshotService` владеет только snapshot data; периодический producer и expansion registrations принадлежат `PlaceholderRuntimeOwner`.

## 5. Persistence и shutdown ordering

Текущая root последовательность в `shutdownRuntime()`:

1. Close Placeholder runtime (cancel refresh producer → unregister expansions → clear snapshots).
2. Close `FlavorRuntimeOwner` (cancel aura producer → unregister CrownFlavorService listener).
3. Close RuntimeIntegrityRuntimeOwner.
4. Close `HpAttributeSyncRuntimeOwner` (cancel sweep → unregister listener).
5. Unregister blueprint hook (сейчас no-op).
6. Cancel Warlock channel tasks.
7. `FoliantRuntimeOwner.close()` (stop periodic flush → unregister listener), затем `foliantService.saveLocksIfDirty()`.
8. `petRuntimeOwner.close()` (despawn → cancel → unregister).
9. Cancel/clear root `activeTasks`.
10. `items.stop()`; unregister PassportChangeListener.
11. `resources.saveAll()`, `hpBarService.saveAll()`, `spec2Storage.save()`.
12. `installations.shutdown()`, `bossBars.shutdown()`.
13. Cooldowns: `CooldownRuntimeOwner.close()` (cancel delayed tasks → unregister listener) → `CooldownManager.saveAll()` → `CooldownManager.clear()`.
14. ClassProvider shutdown; effects clear.
15. `storageWriter.closeAndAwait(Duration.ofSeconds(10))` — последний infrastructure action.

Persistence responsibilities that must remain distinct:

- `AsyncStorageWriter`: shared serial disk writer, root-owned; closes last.
- `ResourceService`: resource state snapshot/persistence.
- `HpBarService`: health store snapshot/persistence and gameplay HP behavior.
- `Spec2Storage`: specialization storage.
- `CooldownManager`: cooldown persistence and per-player task cleanup.
- `FoliantService`: dirty lock persistence.
- `PlusStorage`: storage used by PlusSubscription; current root shutdown has no explicit PlusStorage save/close call visible in this sequence — inspect its writer semantics before defining ownership.
- `PluginTasks.startAutosaveTask`: central periodic coordinator for three stores; it is not itself a storage owner.

The existing shutdown sequence is not failure-isolated end-to-end: most cleanup calls are sequential without independent guarded execution. Pet cleanup has a local catch, but a failure in earlier unguarded cleanup may skip later work. This must be resolved before coordinator activation, not hidden by wrappers.

## 6. Priority assessment

### P0 — HpAttributeSync lifecycle boundary (implemented; verification pending)

`HpAttributeSyncRuntimeOwner` now owns listener registration and the 100-tick sweep through narrow registration/scheduler ports and Paper adapters. `HpAttributeSync` retains sync logic and join/respawn behavior. The owner supports startup rollback, idempotent close and retry after cancellation/unregistration failures. The delayed respawn one-shot (2 ticks) remains unowned and is a follow-up. Focused tests are authored; CI verification is pending.

### P1 — CooldownManager runtime ownership

Reason: combines Listener, per-player delayed tasks, mutable cooldown state and persistence. Extraction must preserve public compatibility and ensure listener + delayed-task cleanup + save order. Do not move persistence to a generic task owner.

### P1 — FoliantService runtime/persistence ownership (implemented)

`FoliantRuntimeOwner` now owns listener registration and the 1200-tick lock-flush producer through narrow Paper adapters and `TaskScope`. `FoliantService` retains the dirty state, YAML snapshot generation and shared-writer submission. Shutdown stops the producer and unregisters the listener before `saveLocksIfDirty()`; the owner never closes the shared writer.

The asynchronous writer completion callback still schedules a one-shot main-thread acknowledgement with Bukkit. This callback is part of the persistence protocol, not the periodic producer; its cancellation/drain semantics remain an explicit follow-up and must be covered by the storage lifecycle contract.

### P1 — PlaceholderAPI integration lifecycle (implemented)

`PlaceholderRuntimeOwner` coordinates the 5-tick snapshot producer and both optional expansions. `PlaceholderSnapshotService` retains only snapshot data and refresh logic. The owner rolls back partial startup and retries failed task cancellation or expansion unregistration.

### P1 — Crown flavor runtime ownership (implemented)

`FlavorRuntimeOwner` coordinates the `CrownFlavorService` listener and 40-tick aura producer. The service retains faction/title lookup and the actual aura tick logic. Listener registration and producer startup remain separate to preserve root phase ordering; shutdown cancels the producer before unregistering the listener.

### P2 — Presentation and combat tasks

BossBar/HUD/HP/DoT/CC/resource/flavor/installation/ManaSoaked require domain-specific owners. Avoid one large presentation owner: HP persistence and combat state do not belong to generic UI lifecycle. HUD owner scaffolding remains unwired.

### P2 — Global purge/autosave orchestration

These callbacks cross multiple domains. Keep root-owned until individual domain lifecycle owners exist, or split into explicit scheduler registrations with a documented dependency graph. Autosave must stop before final snapshots/writer close; purge must stop before stores are cleared.

## 7. Recommended next slice

The next bounded extraction is **a domain-specific presentation/combat runtime owner**. Crown flavor has been transferred to `FlavorRuntimeOwner`; remaining candidates include BossBar, HUD, HP bar, DoT, CC, resources and installations. Avoid combining unrelated state owners.

Do not activate `BootstrapCoordinator` in this slice.

## 8. Acceptance criteria for this inventory

- [x] Root startup and shutdown call sites inspected.
- [x] Root periodic task registrations identified.
- [x] Internal task-bearing services sampled and ownership recorded.
- [x] Listener and external registration call sites mapped.
- [x] Shared writer finalization and final-save dependencies documented.
- [ ] Full repository-wide scheduled-task and registration scan completed.
- [x] HpAttributeSync listener and recurring sweep transferred to a dedicated runtime owner.
- [x] CooldownManager listener and delayed notification task shutdown coordinated by a dedicated runtime owner.
- [x] FoliantService listener and periodic lock-flush producer transferred to a dedicated runtime owner.
- [ ] Every row validated against the owning class implementation before module graph construction.
- [x] PlaceholderAPI snapshot producer and both expansion registrations transferred to `PlaceholderRuntimeOwner`.
- [x] Crown flavor listener and 40-tick aura producer transferred to `FlavorRuntimeOwner`.
- [x] Crown flavor listener and 40-tick aura producer transferred to `FlavorRuntimeOwner`.

The unchecked items are deliberate gates: this inventory is sufficient to choose the next bounded extraction, but not yet sufficient to activate the coordinator.


## 9. A-03-003.12 — CooldownManager runtime ownership

Реализация выполнена в ветке `refactor/a03-foundation-lifecycle`.

### Новая граница

- `CooldownRuntimeOwner` координирует регистрацию listener и остановку notification tasks.
- `PaperCooldownRuntimeRegistration` регистрирует listener через PluginManager и снимает только этот listener через `HandlerList.unregisterAll(listener)`.
- `CooldownManager` сохраняет владение mutable cooldown state, картой BukkitTask, обработчиками join/quit, YAML snapshot и persistence.
- Плановый autosave `PluginTasks.startAutosaveTask` не перемещён и по-прежнему вызывает `saveAll()`.
- Общий `AsyncStorageWriter` остаётся root-owned и закрывается последним.

### Поведение остановки

1. Owner вызывает `CooldownManager.cancelAllTasks()`.
2. После успешной отмены снимает listener.
3. Root сохраняет cooldown snapshot.
4. Root очищает runtime state.
5. Общий writer закрывается в прежнем месте.

Ошибки отмены задач теперь агрегируются: отмена одной задачи не препятствует попыткам отменить остальные; задачи, отмену которых не удалось подтвердить, остаются в map для повторной попытки. `clear()` сначала требует успешной отмены задач и только затем очищает cooldown state.

Owner независимо пытается снять listener даже при ошибке отмены задач. Неуспешная операция повторяется при следующем `close()`. Root изолирует ошибки cooldown cleanup, чтобы они не пропускали финализацию других сервисов и общего writer.

### Проверки

Добавлен `CooldownRuntimeOwnerTest`: идемпотентный start, порядок cancel → unregister, повторный close, retry после ошибки отмены, retry unregister, rollback частичной регистрации и close-before-start.

Игровая семантика кулдаунов, расчёт времени, уведомления, форматы YAML и порядок autosave не менялись намеренно.


## 10. A-03-003.13 — FoliantService runtime ownership

Реализация выполнена в ветке `refactor/a03-foundation-lifecycle`.

### Границы ответственности

- `FoliantRuntimeOwner` управляет listener registration и периодической задачей lock flush.
- `PaperFoliantRuntimeRegistration` регистрирует/снимает только listener Фолианта.
- `PaperFoliantRuntimeScheduler` создаёт отменяемую Paper-задачу и возвращает `TaskHandle`.
- `FoliantService` сохраняет владение lock map, `DirtyStorageState`, YAML snapshot, генерацией snapshot и submission в общий `AsyncStorageWriter`.
- Общий writer не закрывается Foliant owner и остаётся root-owned.

### Порядок запуска и остановки

Запуск owner выполняется в `initializeCoreServices`: сначала регистрируется listener, затем создаётся flush producer с прежним интервалом 1200 ticks. При ошибке регистрации/планирования выполняется rollback.

При shutdown:
1. `FoliantRuntimeOwner.close()` отменяет периодическую задачу.
2. Owner снимает listener даже при ошибке отмены задачи.
3. Root вызывает `saveLocksIfDirty()`, чтобы финальный snapshot был отправлен в writer после остановки producer.
4. Общий writer закрывается позднее, в прежней root-owned точке.

### Асинхронное подтверждение сохранения

`FoliantService.saveLocks()` после завершения записи ставит one-shot Bukkit callback для `clearIfUnchanged(snapshotGeneration)`. `executeTransition()` также ставит one-shot callback для проверки LuckPerms-класса после миграции. Оба callback остаются за пределами periodic runtime owner: первый относится к persistence acknowledgement и должен рассматриваться вместе с A-04 Storage & Data Integrity; второй — к отложенной игровой проверке и требует отдельного lifecycle review. Их cancellation/drain semantics не объявляются решёнными этим этапом.

### Проверки

Добавлен `FoliantRuntimeOwnerTest`: idempotent start, cancel-before-unregister, repeated close, rollback при ошибке планирования/регистрации, retry после cancellation/unregistration failure, close-before-start и валидация периода.

Игровая логика дропа, soulbound, релок-счётчиков, GUI, LuckPerms-перехода и формат `foliant-lock.yml` не менялись намеренно.


## 11. A-03-003.14 — PlaceholderAPI integration lifecycle

Реализация выполнена в ветке `refactor/a03-foundation-lifecycle`.

### Границы ответственности

- `PlaceholderSnapshotService` формирует и публикует immutable snapshots; больше не создаёт и не отменяет BukkitTask.
- `PlaceholderRuntimeOwner` владеет initial refresh, периодическим producer, registration lifecycle и очисткой snapshots.
- `PaperPlaceholderRuntimeScheduler` создаёт Paper task с исходным интервалом 5 ticks и возвращает `TaskHandle`.
- `PaperPlaceholderRuntimeRegistration` создаёт и регистрирует обе expansions: `%raskolclasses_*%` и `%raskolcrown_*%`; при частичном отказе пытается снять уже затронутые registrations.
- Expansions явно unregister при shutdown, несмотря на `persist() == true`.

### Порядок запуска и остановки

Запуск:
1. Немедленно формируется первый snapshot, как и в исходном `PlaceholderSnapshotService.start()`.
2. Планируется refresh task с initial delay и period 5 ticks.
3. Регистрируются обе expansions.
4. При ошибке выполняется rollback: отмена task → unregister expansions (если registration была начата) → очистка snapshots.

Остановка:
1. Отмена refresh task.
2. Снятие crown expansion, затем raskolclasses expansion.
3. Очистка snapshots и диагностического набора предупреждений.

PlaceholderAPI остаётся необязательной интеграцией. Если plugin отсутствует, либо регистрация/планирование завершается ошибкой, основной RaskolClasses продолжает запуск; ошибка логируется. Ошибки shutdown cleanup логируются независимо от остальных root cleanup.

### Проверки

Добавлен `PlaceholderRuntimeOwnerTest`: initial snapshot ordering, idempotent start, producer cancellation before unregister, schedule/refresh/registration rollback, retry после cancellation/unregistration failure и close-before-start.

Не менялись placeholder identifiers, параметры, возвращаемые значения, форматирование, источник значений, thread model и период обновления. Асинхронные PlaceholderAPI callbacks по-прежнему читают только immutable snapshot по UUID.


### SpotBugs note for A-03-003.14

The two expansions intentionally retain the shared PlaceholderSnapshotService dependency to read the published UUID-keyed snapshots. The service is not copied or exposed through a public accessor; it is a lifecycle-managed runtime collaborator. The EI_EXPOSE_REP2 exceptions are therefore limited to the exact `snapshots` fields of `RaskolPlaceholder` and `FlavorPlaceholder`, alongside their existing exact `plugin` field exceptions. No package-wide or pattern-wide SpotBugs suppression was introduced.


## 12. A-03-003.15 — CrownFlavorService runtime ownership

Реализация выполнена в ветке `refactor/a03-foundation-lifecycle`.

- `FlavorRuntimeOwner` владеет регистрацией listener `CrownFlavorService` и периодической aura task.
- `PaperFlavorRuntimeRegistration` регистрирует и снимает только listener королевских вкусов.
- `PaperFlavorTaskScheduler` создаёт sync Paper task с исходным initial delay и period 40 ticks; handle принадлежит `TaskScope`.
- `CrownFlavorService` сохраняет faction/title lookup, join-message и particle selection/spawn; метод `tickAura()` выполняет один цикл и не планирует задачи.
- Root регистрирует listener на прежней фазе `registerGameplayListeners`, а producer запускает на прежней фазе `startRuntimeTasks`.
- Root больше не помещает aura handle в `activeTasks`; shutdown закрывает owner до остановки остальных игровых сервисов.
- Startup failure при регистрации или планировании выполняет rollback; shutdown независимо пытается отменить задачу и снять listener, сохраняя retry path при ошибке.
- Добавлен `FlavorRuntimeOwnerTest` с 8 сценариями: идемпотентность, обязательность регистрации, порядок shutdown, rollback, cancellation retry, unregister retry и close-before-start.

Не менялись фракционные идентификаторы, конфигурационные ключи, particle fallback, координаты/количество частиц, титулы и сообщение при входе. Интервал ауры остаётся 40 ticks.
