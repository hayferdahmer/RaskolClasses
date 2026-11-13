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
| Flavor aura | `flavorService.startAuraTask()` | `activeTasks` | Периодический визуальный эффект; listener зарегистрирован отдельно |
| Installation sweep | `installations.startSweepTask()` | `activeTasks` | Очистка/обслуживание runtime установок; сервис отдельно участвует в gameplay |
| Scroll cooldown | `new ScrollCooldownTask(this).start()` | `activeTasks` | Отдельный task object теряется после возврата handle; владелец — root |
| ManaSoaked | `manaSoaked.start()` | `activeTasks` | Периодическая механика combat |
| Global purge | `PluginTasks.startPurgeTask(this)` | `activeTasks` | Один callback вызывает purge многих доменов и статических ability-классов; широкий cross-domain orchestration |
| Global autosave | `PluginTasks.startAutosaveTask(this)` | `activeTasks` | Плановый snapshot cooldowns/resources/spec2; обязан завершиться до закрытия общего writer |
| Foliant lock flush | `foliantService.startLockFlushTask()` в `initializeCoreServices` | `activeTasks` | Task создаётся раньше общей фазы start; FoliantService также Listener и владелец dirty lock persistence |
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
- `resources`, `effects`, `cooldowns`, `PassiveListener`, `ClassBook.ClickHandler`, `BindListener`, `CrownFlavorService`, `TrailListener`, `InstallBindListener`, `combat`, `combat.vanillaListener()`, `combat.avoidance()`, `HunterAbilities.FanArrowHit`, `ScrollSanitizer`, `HpBarService`, `FxService`, `FoliantService`, `WarlockAbilities` — зарегистрированы через PluginManager.
- `PassportChangeListener` — собственный reflective registration; root вызывает `unregister()`.
- `SessionListener` — создаётся локально и регистрируется; отдельного close/unregister нет.
- Pet listener — теперь зарегистрирован/отменяется через PetRuntimeOwner.

Bukkit обычно очищает зарегистрированные listeners при выгрузке плагина, однако это не заменяет явный owner lifecycle там, где нужна частичная rollback/повторная очистка или listener имеет отдельные ресурсы.

### Optional / external registrations

- PlaceholderAPI: `PlaceholderSnapshotService.start(this)`, затем две expansion registration calls; root закрывает snapshot service. Отдельного unregister expansion в текущем root sequence не видно — проверить контракт PlaceholderAPI и ownership expansions до extraction.
- RaskolCore passport event: reflective `registerEvent`; явный `HandlerList.unregisterAll(this)` существует.
- RaskolEnchant BlueprintHook: текущий код только обнаруживает plugin и логирует; рецепты не регистрирует, `unregisterBlueprints()` пока no-op.
- PlayerPoints: создаётся wallet adapter или NullWallet; отдельная registration lifecycle в root не видна.
- Placeholder snapshot service — отдельный task owner внутри сервиса, закрывается root до источников, которые он читает.

## 5. Persistence и shutdown ordering

Текущая root последовательность в `shutdownRuntime()`:

1. Close Placeholder snapshots.
2. Close RuntimeIntegrityRuntimeOwner.
3. Close `HpAttributeSyncRuntimeOwner` (cancel sweep → unregister listener).
4. Unregister blueprint hook (сейчас no-op).
5. Cancel Warlock channel tasks.
6. `foliantService.saveLocksIfDirty()`.
7. `petRuntimeOwner.close()` (despawn → cancel → unregister).
8. Cancel/clear root `activeTasks`.
9. `items.stop()`; unregister PassportChangeListener.
10. `resources.saveAll()`, `hpBarService.saveAll()`, `spec2Storage.save()`.
11. `installations.shutdown()`, `bossBars.shutdown()`.
12. Cooldowns: `CooldownRuntimeOwner.close()` (cancel delayed tasks → unregister listener) → `CooldownManager.saveAll()` → `CooldownManager.clear()`.
13. ClassProvider shutdown; effects clear.
14. `storageWriter.closeAndAwait(Duration.ofSeconds(10))` — последний infrastructure action.

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

### P1 — FoliantService runtime/persistence ownership

Reason: Listener + periodic lock flush + dirty persistence. Root currently starts its task in initialize, registers listener in a later phase and invokes final save before writer close. Owner must preserve that order and must not close shared writer.

### P1 — PlaceholderAPI integration lifecycle

Reason: optional external registration and snapshot refresh task. Explicitly distinguish expansion registration/unregistration from snapshot cache lifecycle. Keep optional failure isolated.

### P2 — Presentation and combat tasks

BossBar/HUD/HP/DoT/CC/resource/flavor/installation/ManaSoaked require domain-specific owners. Avoid one large presentation owner: HP persistence and combat state do not belong to generic UI lifecycle. HUD owner scaffolding remains unwired.

### P2 — Global purge/autosave orchestration

These callbacks cross multiple domains. Keep root-owned until individual domain lifecycle owners exist, or split into explicit scheduler registrations with a documented dependency graph. Autosave must stop before final snapshots/writer close; purge must stop before stores are cleared.

## 7. Recommended next slice

The next bounded extraction is **CooldownManager runtime ownership**. Preserve its Listener, per-player delayed notification tasks and persistence semantics; do not merge its storage responsibilities into a generic runtime owner.

Proposed scope:
- introduce narrow registration and task-scheduler ports plus Paper adapters;
- owner controls listener registration, sweep handle and close;
- preserve existing `HpAttributeSync` sync logic and 100-tick cadence;
- keep delayed join sync behavior unchanged in this slice, but record it as a follow-up if it cannot be cancelled through current API;
- root becomes composition wiring only for this component;
- characterization tests cover start/close order, repeated close, partial registration/scheduling failure and cancellation;
- no change to HP calculations, attribute modifiers, health storage or player-visible behavior.

Do not activate `BootstrapCoordinator` in this slice. Do not combine HpAttributeSync with HpBarService.

## 8. Acceptance criteria for this inventory

- [x] Root startup and shutdown call sites inspected.
- [x] Root periodic task registrations identified.
- [x] Internal task-bearing services sampled and ownership recorded.
- [x] Listener and external registration call sites mapped.
- [x] Shared writer finalization and final-save dependencies documented.
- [ ] Full repository-wide scheduled-task and registration scan completed.
- [x] HpAttributeSync listener and recurring sweep transferred to a dedicated runtime owner.
- [ ] Every row validated against the owning class implementation before module graph construction.

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
