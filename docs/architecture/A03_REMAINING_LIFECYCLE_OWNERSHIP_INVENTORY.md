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
| HUD ActionBar runtime | `HudRuntimeOwner` + `PaperHudTaskScheduler` + `PaperHudSessionEvents` | Owner управляет quit cleanup и периодическим producer; `HudServiceOutputCleanup` очищает только внутренние кэши | Подключён к root; `HudService` сохраняет renderer и per-player cache; HP bar остаётся отдельным владельцем |
| BossBar runtime | `BossBarRuntimeOwner` | Периодический producer через TaskScope; restart очищает полосы и перечитывает период; close отменяет producer и удаляет полосы | `BossBarService` сохраняет только rendering/state; owner подключён к root |
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
| HUD ActionBar | `HudRuntimeOwner.start()` при `hud.enabled && hp-display.mode=vanilla` | `HudRuntimeOwner` / `TaskScope` | Reload согласует start/stop; начальная задержка и период сохраняют `max(1, hudUpdatePeriodTicks())` |
| HP bar runtime | `HpBarRuntimeOwner` + `PaperHpBarTaskScheduler` + `PaperHpBarRuntimeRegistration` | Owner owns join/quit/respawn listener and periodic tick; `HpBarService` retains HP state, gameplay logic and health persistence | Подключён к root; `saveAll()` остаётся в shutdown перед закрытием общего StorageWriter |
| Resource tick | `resources.startTickTask(this)` | `activeTasks` | Resource runtime; сервис также Listener и хранит изменяемое состояние |
| BossBar tick | `BossBarRuntimeOwner` + `PaperBossBarTaskScheduler` | `BossBarRuntimeOwner` / `TaskScope` | `BossBarService` выполняет один render cycle и хранит полосы; период берётся из `bossBarTickPeriod()`; reload вызывает owner restart |
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

1. Close ActionBar `HudRuntimeOwner` (cancel producer → clear only HUD caches → unregister quit listener).
2. Close Placeholder runtime (cancel refresh producer → unregister expansions → clear snapshots).
3. Close `FlavorRuntimeOwner` (cancel aura producer → unregister CrownFlavorService listener).
4. Close RuntimeIntegrityRuntimeOwner.
5. Close `HpAttributeSyncRuntimeOwner` (cancel sweep → unregister listener).
6. Unregister blueprint hook (сейчас no-op).
7. Cancel Warlock channel tasks.
8. `FoliantRuntimeOwner.close()` (stop periodic flush → unregister listener), затем `foliantService.saveLocksIfDirty()`.
9. `petRuntimeOwner.close()` (despawn → cancel → unregister).
10. Cancel/clear root `activeTasks`.
11. `items.stop()`; unregister PassportChangeListener.
12. `resources.saveAll()`, `hpBarService.saveAll()`, `spec2Storage.save()`.
13. `installations.shutdown()`, `bossBars.shutdown()`.
14. Cooldowns: `CooldownRuntimeOwner.close()` (cancel delayed tasks → unregister listener) → `CooldownManager.saveAll()` → `CooldownManager.clear()`.
15. ClassProvider shutdown; effects clear.
16. `storageWriter.closeAndAwait(Duration.ofSeconds(10))` — последний infrastructure action.

Persistence responsibilities that must remain distinct:

- `AsyncStorageWriter`: shared serial disk writer, root-owned; closes last.
- `ResourceService`: resource state snapshot/persistence.
- `HpBarService`: health store snapshot/persistence and gameplay HP behavior. Periodic `tick()` is now a callback scheduled by `HpBarRuntimeOwner`; the owner closes before `saveAll()` so no producer races the final health snapshot.
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


### A-03-003.16 — BossBarService runtime ownership

`BossBarRuntimeOwner` теперь владеет периодическим producer BossBar через `TaskScope` и `PaperBossBarTaskScheduler`. `BossBarService` больше не создаёт, не хранит и не отменяет `BukkitTask`: он сохраняет rendering logic, карту активных полос и операцию `clearBars()`.

Сохранённые контракты:
- период читается из `bossBarTickPeriod()` и ограничивается минимумом в один тик;
- initial delay равен period, как и раньше;
- выключенный `bossbar.enabled` очищает полосы в очередном цикле, не останавливая producer;
- reload вызывает `BossBarRuntimeOwner.restart()`: cancel → clear → schedule с актуальным периодом;
- shutdown отменяет producer и удаляет все полосы;
- HUD visibility, class/effect lookup, форматирование заголовков, сортировка, цвета и прогресс не менялись намеренно.

Owner не регистрирует listener: `BossBarService` им не является. BossBar task удалён из root `activeTasks`; shutdown cleanup изолирован от ошибок и не пропускает последующие действия root. Добавлены unit tests на идемпотентный запуск, restart, порядок cleanup, scheduling/cancellation failure, retry и нормализацию периода. Совместимый getter `RaskolClasses.getBossBars()` сохранён; для него добавлено точечное исключение EI_EXPOSE_REP в SpotBugs filter, как и для других живых service accessors.


## 11. A-03-003.17 — ActionBar HUD runtime production wiring

`HudRuntimeOwner` is now connected to production wiring. It registers a dedicated quit listener through `PaperHudSessionEvents`, schedules `HudService.tick()` through `PaperHudTaskScheduler`, and owns both the recurring task and per-player cache cleanup. `HudService` no longer creates or stores `BukkitTask`.

The root preserves the original activation gate (`hud.enabled` and `hp-display.mode=vanilla`). Reload now reconciles the owner: start when the gate is enabled, stop and clear HUD caches otherwise. The owner is closed before other root runtime cleanup. `HudServiceOutputCleanup` removes only HUD-owned internal caches; it deliberately does not send an empty ActionBar, because that could erase output from the independent HP presentation path or another plugin. HP bar mechanics and scheduling remain separate. The compatibility accessor `RaskolClasses.getHud()` remains available; its exact `EI_EXPOSE_REP` SpotBugs finding is handled by a method-scoped filter entry, not a class-wide suppression.


## 12. A-03-003.18 — HP bar runtime ownership

`HpBarRuntimeOwner` now owns `HpBarService` lifecycle listener registration (join/quit/respawn) and its repeating synchronous tick. Paper integration is isolated in `PaperHpBarRuntimeRegistration` and `PaperHpBarTaskScheduler`. The root registers the listener in the existing gameplay-listener phase, starts the producer in the existing runtime-task phase, and closes the owner before `HpBarService.saveAll()` during shutdown. The configured period remains `max(1, hp-display.update-period-ticks)` with initial delay equal to period.

`HpBarService` retains HP calculations, regeneration, pending restore, hearts/actionbar presentation, dirty tracking, health.yml snapshots, async writer callbacks, and `saveAll()`. Only its scheduler ownership was removed; `tick()` is a public one-cycle callback. No HP formula, config key, persistence format, or gameplay behavior was intentionally changed.


## 13. A-03-003.18 — HP bar runtime ownership verification

The branch formatting workflow completed successfully and committed the canonical formatting as `89104ef959e754e0b3ce3f5ffa987b586b5f6a39`. Its post-format `mvn -Pdev -B verify` passed on the formatted working tree: 610 tests, no failures/errors/skips, JaCoCo thresholds met, SpotBugs `BugInstance size is 0`, and Spotless `BUILD SUCCESS` with 449 Java files clean. A-03-003.18 is closed.


## 14. A-03-003.19 — Separate DoT and Crowd Control runtime ownership

The DoT and CC lifecycles are deliberately implemented as two independent runtime owners. Neither owner stores or mutates effect state.

**DoT boundary**
- `DotService` remains the owner of the target-to-`DotInstance` registry, stacking, refresh/expiry timestamps, attribution, DoT calculations, damage dispatch, VFX, and projectile-hit behavior.
- `DotRuntimeOwner` owns only the `DotService` listener registration and its recurring producer. `PaperDotTaskScheduler` preserves the original synchronous cadence: initial delay 20 ticks, period 20 ticks.
- The owner uses its own `TaskScope`; it is not shared with CC or another domain.

**Crowd Control boundary**
- `CCService` remains the owner of active `CCInstance` entries, DR category state, fear-turn deadlines, slow movement restoration data, expiration/break behavior, mob AI changes, and feedback coordination.
- `CCRuntimeOwner` owns only the `CCService` listener registration and its recurring producer. `PaperCCTaskScheduler` preserves the original synchronous cadence: initial delay 1 tick, period 1 tick.
- CCGuard, CastGuard, and VanillaCCWrapper remain distinct presentation/guard adapters and are not folded into the CC runtime owner.

Both owners independently roll back partial startup, cancel their own producer before unregistering their own listener, and preserve retry paths for failed cleanup. `RaskolClasses` no longer stores either task in `activeTasks`. No effect-state map, DR rule, duration, tick cadence, damage formula, CC behavior, API call site, or configuration key is intentionally changed.

The separate `CCFeedbackRuntimeOwner` extraction is tracked in section 15 below; it is deliberately independent of `CCRuntimeOwner`.
## 15. A-03-003.20 — CCFeedback resource lifecycle assessment

### Реализованная структура

`CCFeedbackRuntimeOwner` теперь хранит target-keyed наборы ресурсов и handles отложенной очистки. `CCFeedback` — instance-компонент, передаваемый в `CCService`; статическая карта `ACTIVE` удалена. `PaperCCFeedbackScheduler` изолирует Bukkit scheduler. Composition root создаёт owner и закрывает его при shutdown после остановки периодического CC runtime.

The global display is visible to everyone except a player target; the optional local display is hidden by default and shown to that target. Both displays are passengers of the target. This visibility and attachment behavior is a compatibility contract.

### Source-backed lifecycle gaps

1. **Stale delayed callback can delete a replacement.** The callback removes both captured displays and then unconditionally executes `ACTIVE.remove(targetUuid)`. If a new CC application replaced the bundle before the old callback fires, the old callback can remove the new bundle's map entry. This can orphan the replacement displays from explicit cleanup.
2. **Partial spawn can leak a display.** The global display is spawned before the local display and before the bundle is published in `ACTIVE`. If local display creation or later setup throws, the already spawned global display has no registered owner.
3. **Delayed tasks are untracked.** Explicit clear removes the current displays but does not cancel the corresponding delayed callback. The callback can still run later against captured entities and UUID state.
4. **No disable cleanup boundary.** `shutdownRuntime()` closes `CCRuntimeOwner`, but that owner intentionally owns only the CC listener and 1-tick producer. There is no independent close operation for holograms and their delayed tasks.
5. **No explicit world/entity lifecycle hook.** `CCService` removes active CC on player quit, entity death, or when its tick discovers a missing/dead entity. These paths normally call feedback cleanup, but they do not replace a plugin-wide resource owner and do not cover partial creation.

### Ownership decision

Implemented: dedicated `CCFeedbackRuntimeOwner`, separate from `CCRuntimeOwner`.

- It owns target-UUID keyed hologram bundles and their one-shot cleanup task handles.
- It owns only visual resources and their cleanup callbacks. It does not own `CCInstance`, DR state, fear deadlines, slow modifiers, CC calculations, or the periodic CC tick.
- Replacing a target bundle must retire the exact previous entry before publishing the new one.
- A delayed callback may remove an entry only when it still owns the exact current entry (identity/generation check); it must never remove a newer replacement.
- Creation must be transactional: every successfully spawned display is either published to the owner or removed if a later creation/attachment/scheduling step fails.
- Explicit clear and owner close cancel the corresponding delayed task before removing displays. Cleanup attempts for task cancellation and display removal must be independent and retryable.
- The Paper boundary remains responsible for Bukkit `TextDisplay` and scheduler operations; the owner core should be testable without a live server.
- Close is idempotent and must leave failed-to-cancel task handles / failed-to-remove displays reachable for retry.

### Preserved behavior / non-goals

- Preserve the existing two-display visibility model, passenger attachment, text, color, billboard, see-through and shadow settings.
- Preserve `cc.feedback-hologram`, duration calculation, and the one-shot cleanup deadline (`max(1, ticks)`).
- Preserve all sounds, particles, player/caster messages, CC timing, DR rules, and service call semantics.
- Do not merge this owner into `CCRuntimeOwner`.
- Do not move CC domain state into the resource owner.
- Do not add new gameplay listeners or change quit/death semantics as part of the extraction.

### Implementation acceptance criteria

- [x] One-shot task handles are retained and cancelled on replacement, explicit clear, and plugin disable.
- [x] An old callback cannot remove a replacement bundle.
- [x] Partial global/local display creation and scheduling failures remove every resource already created.
- [x] Cleanup attempts every task and every display even when one cleanup operation fails.
- [x] Failed cleanup remains retryable on a subsequent close.
- [x] Focused unit tests cover replacement identity, stale callback, scheduling rollback, cancellation/removal failures, and repeated close.
- [x] `CCService` remains the owner of CC state; `CCRuntimeOwner` remains the owner of the periodic CC producer.

Paper-level integration verification of TextDisplay visibility/passenger behavior remains a follow-up; no live server test has been claimed.


## A-03-003.21 — Аудит оставшихся runtime-задач и callback

Подтверждённые остатки после выделения owners, включая шесть root-owned periodic tasks, HP respawn callbacks, storage completion callbacks, Warlock channel tasks и duplicate sweep handle InstallationService, описаны в отдельном source-backed документе: [A03_RUNTIME_TASK_CALLBACK_AUDIT.md](A03_RUNTIME_TASK_CALLBACK_AUDIT.md).

Аудит выполняется поэтапно. Полный repository-wide scan и сверка всех task creation sites остаются незакрытыми acceptance criteria; runtime-код в этом срезе не менялся.


### A-03-003.21 implementation update — Warlock channel tasks

`WarlockChannelTaskOwner` now owns the one-shot tasks created by `WarlockAbilities.soulRift()` on a per-caster basis. Each handle is registered immediately after scheduling; partial scheduling failure cancels already registered tasks; channel completion releases handles in a `finally` block; plugin shutdown closes the owner independently. Five focused unit tests cover caster isolation, retryable cancellation, close retry, normal completion, and rejection after close.

The next CI pass must include Spotless formatting and the complete `mvn -Pdev -B verify` pipeline. This implementation slice remains open until both Build and Analyze jobs pass.


**CI formatting checkpoint:** Spotless Format workflow run #16 completed successfully. It applied the canonical formatting and its post-format `mvn -Pdev -B verify` passed on the workflow workspace: 623 tests, 0 failures/errors/skips; SpotBugs reported 0 BugInstances and 0 errors; Spotless reported 456 files clean. The formatting commit is present on this branch. A fresh Build run is required against the resulting commit because Build #3223 analyzed the pre-format SHA and its Analyze job raced ahead of the formatting push.

### A-03-003.21 implementation update — InstallationService sweep ownership

InstallationRuntimeOwner now owns the recurring installation sweep task through TaskScope. PaperInstallationTaskScheduler is the Paper boundary and preserves the original 20-tick initial delay and 20-tick period. InstallationService.sweep() performs one domain cycle and no longer creates or stores a BukkitTask; InstallationService.shutdown() only expires active installations and clears its domain state.

RaskolClasses constructs and starts the runtime owner. During shutdown it closes the owner before calling InstallationService.shutdown(), so the producer is stopped before active installation resources are expired. The task is no longer added to root activeTasks, eliminating the previous duplicate cancellation path.

Focused tests cover idempotent start, close-before-start, scheduling failure, cancellation failure with retry, and single cancellation. The original installation limits, TTL, cooldowns, placement gates, handler tick behavior, and expiry behavior are intentionally unchanged.
