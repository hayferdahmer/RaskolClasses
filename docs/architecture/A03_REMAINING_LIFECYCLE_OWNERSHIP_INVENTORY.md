# A-03-003.10 — Remaining Lifecycle Ownership Inventory

## Статус и границы

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed inventory с поэтапной runtime-реализацией; игровые механики не меняются намеренно.
- Основной источник композиции: `src/main/java/dev/raskol/classes/RaskolClasses.java`.

Инвентаризация фиксирует наблюдаемые точки создания, запуска, регистрации и остановки. Это не утверждение, что перечислены абсолютно все внутренние Bukkit-задачи: для этого потребуется отдельный проход по каждому сервису и его зависимостям. В частности, отложенные одноразовые задачи и задачи, создаваемые во время игрового процесса, помечены отдельно от корневых периодических задач.

## 1. Уже выделенные владельцы

| Компонент | Текущее владение | Подтверждённое поведение | Остаток |
|---|---|---|---|
| Runtime RIPS | `RuntimeIntegrityRuntimeOwner` | Отдельный daemon executor, monitor, reporter/state; запускается после успешного startup и закрывается в начале shutdown | Bukkit disable callback остаётся инъекцией из root |
| Pet runtime | `PetRuntimeOwner` | Listener + два периодических задания; despawn → cancel → unregister; rollback и retry | Реализация и тесты есть; CI #3081 success |
| HUD ActionBar runtime | `HudRuntimeOwner` + `PaperHudTaskScheduler` + `PaperHudSessionEvents` | Owner управляет quit cleanup и периодическим producer; `HudServiceOutputCleanup` очищает только внутренние кэши | Подключён к root; `HudService` сохраняет renderer и per-player cache; HP bar остаётся отдельным владельцем |
| BossBar runtime | `BossBarRuntimeOwner` | Периодический producer через TaskScope; restart очищает полосы и перечитывает период; close отменяет producer и удаляет полосы | `BossBarService` сохраняет только rendering/state; owner подключён к root |
| HP attribute sync | `HpAttributeSyncRuntimeOwner` + `PlayerCallbackRuntimeOwner` | Listener + 100-tick sweep; отдельный player-keyed one-shot owner сохраняет 2-tick respawn delay, quit/shutdown cancellation | Root закрывает callbacks до listener/runtime owner; storage callbacks остаются в A-04 |
| PlaceholderAPI | `PlaceholderRuntimeOwner` | Пятитиковый snapshot producer + регистрация двух expansions; rollback и retryable cleanup | Snapshot data остаётся в `PlaceholderSnapshotService`; Paper adapter владеет expansion instances |
| Crown flavor | `FlavorRuntimeOwner` | Listener registration + 40-tick aura producer; rollback и retryable cancellation/unregistration | `CrownFlavorService` сохраняет faction/title lookup и aura tick logic |
| Installation sweep | `InstallationRuntimeOwner` + `PaperInstallationTaskScheduler` | Отдельный TaskScope; 20-tick initial delay/period; root close перед service cleanup | `InstallationService` владеет active installations, expiry и cooldown state; BukkitTask больше не хранится в сервисе |
| Resource tick | `ResourceRuntimeOwner` + `PaperResourceTaskScheduler` | Отдельный TaskScope; 20-tick initial delay/period; owner закрывается перед `resources.saveAll()` | `ResourceService` сохраняет listener, resource state, регенерацию и persistence; `tick()` — callback без scheduler ownership |
| Warlock channel callbacks | `WarlockChannelTaskOwner` | Per-caster handles; немедленная регистрация, partial rollback, retryable close | `WarlockAbilities` сохраняет channel/damage behavior; root закрывает owner при shutdown |

Важно: наличие owner-класса не означает, что production lifecycle уже переведён на него.

## 2. Корневые периодические задачи

Точки запуска в `RaskolClasses.startRuntimeTasks()`:

| Задача | Создатель / сервис | Текущий владелец handle | Фактические обязанности и риск |
|---|---|---|---|
| DoT tick | `DotRuntimeOwner` + `PaperDotTaskScheduler` | `DotRuntimeOwner` / `TaskScope` | DoT state остаётся в DotService; cadence 20 ticks |
| CC tick | `CCRuntimeOwner` + `PaperCCTaskScheduler` | `CCRuntimeOwner` / `TaskScope` | CC state остаётся в CCService; cadence 1 tick |
| HUD ActionBar | `HudRuntimeOwner.start()` при `hud.enabled && hp-display.mode=vanilla` | `HudRuntimeOwner` / `TaskScope` | Reload согласует start/stop; начальная задержка и период сохраняют `max(1, hudUpdatePeriodTicks())` |
| HP bar runtime | `HpBarRuntimeOwner` + `PlayerCallbackRuntimeOwner` + Paper adapters | Listener + periodic tick и отдельный player-keyed next-tick respawn callback; quit/shutdown cancellation | Подключён к root; `saveAll()` остаётся в shutdown перед закрытием общего StorageWriter; writer completion callbacks относятся к A-04 |
| Resource tick | `ResourceRuntimeOwner` + `PaperResourceTaskScheduler` | `ResourceRuntimeOwner` / `TaskScope` | `ResourceService.tick()` выполняет один цикл; owner закрывается до финального `saveAll()` |
| BossBar tick | `BossBarRuntimeOwner` + `PaperBossBarTaskScheduler` | `BossBarRuntimeOwner` / `TaskScope` | `BossBarService` выполняет один render cycle и хранит полосы; период берётся из `bossBarTickPeriod()`; reload вызывает owner restart |
| Flavor aura | `FlavorRuntimeOwner.start()` | `FlavorRuntimeOwner` / `TaskScope` | Сохраняет исходный 40-tick период; listener регистрируется тем же owner до запуска producer |
| Installation sweep | `InstallationRuntimeOwner` + `PaperInstallationTaskScheduler` | `InstallationRuntimeOwner` / `TaskScope` | InstallationService.sweep() выполняет один цикл; cadence 20 ticks; owner закрывается до service cleanup |
| Scroll cooldown | `ScrollCooldownRuntimeOwner` + `PaperScrollCooldownTaskScheduler` | `ScrollCooldownRuntimeOwner` / `TaskScope` | Root хранит task object; owner останавливает producer до очистки `barState`/`lastHeld`; cadence 10 ticks |
| ManaSoaked | `ManaSoakedRuntimeOwner` + `PaperManaSoakedTaskScheduler` | `ManaSoakedRuntimeOwner` / `TaskScope` | `ManaSoakedService.tick()` выполняет один цикл; producer сохраняет initial delay и period 20 ticks; close останавливает producer до очистки mage tracking |
| Global purge | `PluginTasks.startPurgeTask(this)` | `activeTasks` | Один callback вызывает purge многих доменов и статических ability-классов; широкий cross-domain orchestration |
| Global autosave | `PluginTasks.startAutosaveTask(this)` | `activeTasks` | Плановый snapshot cooldowns/resources/spec2; обязан завершиться до закрытия общего writer |
| Foliant lock flush | `FoliantRuntimeOwner.start()` в `initializeCoreServices` | `FoliantRuntimeOwner` / `TaskScope` | Owner управляет listener и периодическим flush producer; FoliantService сохраняет dirty lock state и persistence |
| Pet tick + orphan scan | `PetRuntimeOwner.start()` | `PetRuntimeOwner` / `TaskScope` | Уже выведены из root списка |

`HpAttributeSync` больше не хранит sweep handle: его периодическая задача создаётся через `PaperHpAttributeSyncScheduler` и принадлежит `HpAttributeSyncRuntimeOwner`. Отложенная задача respawn (2 ticks) остаётся внутренней одноразовой задачей Bukkit scheduler без отдельного cancellation handle; это явно оставлено за пределами текущего среза.

## 3. Внутренние / одноразовые задачи

| Компонент | Источник | Владение и остановка | Замечание |
|---|---|---|---|
| Cooldown ready notifications | `CooldownManager.start(...)` | Map задач остаётся внутри CooldownManager; runtime owner вызывает `cancelAllTasks()` при остановке | `CooldownRuntimeOwner` корректно владеет shutdown; A-03-003.28 подтвердил stale handles после естественного callback completion. Следующий slice — finally release с identity guard; см. `A03_COOLDOWN_NOTIFICATION_TASK_AUDIT.md` |
| HP async completion callbacks | `HpBarService.saveHealth()` | Writer completion + Bukkit main-thread callback | Это callbacks, не периодический producer; должны завершаться до/в рамках storage drain contract |
| Foliant async completion / deferred operations | `FoliantService` | Внутри сервиса и общий writer; dirty locks сохраняются root-вызовом | Нужна отдельная проверка каждого deferred callback перед переносом |
| Warlock channel tasks | `WarlockChannelTaskOwner` | Per-caster task handles; root закрывает owner | Handles регистрируются сразу после schedule; partial rollback и retryable close покрыты focused tests |
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
10. Close `ManaSoakedRuntimeOwner`, `ResourceRuntimeOwner`, `InstallationRuntimeOwner` и `ScrollCooldownRuntimeOwner`, затем cancel/clear оставшиеся root `activeTasks`.
11. `items.stop()`; unregister PassportChangeListener.
12. `resources.saveAll()` (после ResourceRuntimeOwner.close), `hpBarService.saveAll()`, `spec2Storage.save()`.
13. `installations.shutdown()` (после остановки InstallationRuntimeOwner), `bossBars.shutdown()`.
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

`HpAttributeSyncRuntimeOwner` owns listener registration and the 100-tick sweep through narrow registration/scheduler ports and Paper adapters. `HpAttributeSync` retains sync logic and join/respawn behavior. Its delayed respawn one-shot (2 ticks) is separately owned by a dedicated `PlayerCallbackRuntimeOwner`, cancelled on quit and closed during root shutdown. The parallel HP bar respawn callback (0 ticks) is also owned by its own `PlayerCallbackRuntimeOwner`. Both callback owners are distinct from the recurring HP runtime owners.

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

Подтверждённые остатки после выделения owners, включая оставшиеся три root-owned periodic tasks, HP respawn callbacks, storage completion callbacks и Warlock channel tasks, описаны в отдельном source-backed документе: [A03_RUNTIME_TASK_CALLBACK_AUDIT.md](A03_RUNTIME_TASK_CALLBACK_AUDIT.md).

Аудит выполняется поэтапно. Полный repository-wide scan и сверка всех task creation sites остаются незакрытыми acceptance criteria; runtime-код в этом срезе не менялся.


### A-03-003.21 implementation update — Warlock channel tasks

`WarlockChannelTaskOwner` now owns the one-shot tasks created by `WarlockAbilities.soulRift()` on a per-caster basis. Each handle is registered immediately after scheduling; partial scheduling failure cancels already registered tasks; channel completion releases handles in a `finally` block; plugin shutdown closes the owner independently. Five focused unit tests cover caster isolation, retryable cancellation, close retry, normal completion, and rejection after close.

The next CI pass must include Spotless formatting and the complete `mvn -Pdev -B verify` pipeline. This implementation slice remains open until both Build and Analyze jobs pass.


**CI formatting checkpoint:** Spotless Format workflow run #16 completed successfully. It applied the canonical formatting and its post-format `mvn -Pdev -B verify` passed on the workflow workspace: 623 tests, 0 failures/errors/skips; SpotBugs reported 0 BugInstances and 0 errors; Spotless reported 456 files clean. The formatting commit is present on this branch. A fresh Build run is required against the resulting commit because Build #3223 analyzed the pre-format SHA and its Analyze job raced ahead of the formatting push.

### A-03-003.21 implementation update — InstallationService sweep ownership

InstallationRuntimeOwner now owns the recurring installation sweep task through TaskScope. PaperInstallationTaskScheduler is the Paper boundary and preserves the original 20-tick initial delay and 20-tick period. InstallationService.sweep() performs one domain cycle and no longer creates or stores a BukkitTask; InstallationService.shutdown() only expires active installations and clears its domain state.

RaskolClasses constructs and starts the runtime owner. During shutdown it closes the owner before calling InstallationService.shutdown(), so the producer is stopped before active installation resources are expired. The task is no longer added to root activeTasks, eliminating the previous duplicate cancellation path.

Focused tests cover idempotent start, close-before-start, scheduling failure, cancellation failure with retry, and single cancellation. The original installation limits, TTL, cooldowns, placement gates, handler tick behavior, and expiry behavior are intentionally unchanged.

### A-03-003.21 — ScrollCooldown runtime verification

- `ScrollCooldownRuntimeOwner` owns the 10-tick recurring producer and cache cleanup; `ScrollCooldownTask` retains display logic and cache state.
- Focused tests cover idempotent start, cancellation-before-cache-cleanup, partial startup failure, retryable cancellation, retryable cache cleanup and close-before-start.
- Spotless Format workflow run #23 applied formatting and completed successfully on the formatted branch head.
- Build workflow run #3257 completed its build job successfully: 639 tests, 0 failures/errors/skips; JaCoCo thresholds met.
- The Analyze job in run #3257 checked the pre-format commit and failed only because Spotless reported three formatting violations. Spotless Format #23 fixed those violations and its post-format `mvn -Pdev -B verify` completed successfully: 639 tests, SpotBugs 0 findings/0 errors, Spotless 468 Java files clean, `BUILD SUCCESS`.
- A fresh Build run is required against the formatted branch head because the formatter's push uses `GITHUB_TOKEN` and does not trigger the Build workflow automatically.


## 16. A-03-003.23 — Global maintenance runtime ownership

Global purge и autosave больше не принадлежат неструктурированному `RaskolClasses.activeTasks`. В `bootstrap.runtime` добавлены независимые `PurgeRuntimeOwner` и `AutosaveRuntimeOwner`, каждый со своим `TaskScope`, scheduler port и Paper adapter. Owners обеспечивают идемпотентный запуск, запрет повторного старта после неудачного старта или закрытия и повторную попытку отмены при transient cleanup failure.

`PluginTasks` сохранён как набор cross-domain callback-операций без Bukkit scheduler ownership. Purge по-прежнему последовательно очищает cooldowns, effects, ability attempts, FX, installations, resists, attributes, character levels, ability-facade stale markers и combat logs, после чего обновляет `lastPurgeMillis`. Autosave по-прежнему сохраняет cooldowns, resources и spec2 storage в прежнем порядке.

`RaskolClasses.startRuntimeTasks()` создаёт и запускает оба owner с прежними интервалами: purge — `purgeIntervalTicks`; autosave — `max(1, storage.autosave-minutes) * 60 * 20` ticks. В начале shutdown оба producer закрываются до финальных `saveAll()`/`save()` и до последнего `AsyncStorageWriter.closeAndAwait()`.

Добавлены `PurgeRuntimeOwnerTest` и `AutosaveRuntimeOwnerTest`, по пять сценариев каждый: idempotent start, idempotent close, scheduling failure, retryable cancellation и close-before-start. `main` не затрагивался; live Paper server verification не заявляется до CI и runtime smoke test.


### A-03-003.23 verification checkpoint

Spotless Format run `43571814754` completed successfully and its post-format `mvn -Pdev -B verify` passed: 655 tests, 0 failures/errors/skips, SpotBugs 0 BugInstances, Spotless 480 Java files clean, `BUILD SUCCESS`. A fresh Build/Analyze run on the formatted branch head remains the final CI gate; no live Paper server test is claimed.


## A-03-003.24 — HP respawn callback ownership

PlayerCallbackRuntimeOwner is shared infrastructure, but HP Attribute and HP Bar each receive an independent instance. The owner tracks one callback per player UUID, replaces a prior callback by cancelling its handle, releases entries before invoking completed callbacks, and retains failed cancellations for retry. Paper scheduling is isolated in PaperPlayerCallbackScheduler.

HpAttributeSync preserves the 2-tick respawn sync delay. HpBarService preserves next-tick execution for max-health/HP/hearts restoration. Quit cancels the pending callback without allowing a cancellation error to skip HP persistence. Both owners are closed before the HP listener/runtime owners and before final storage snapshots. Storage writer completion callbacks are explicitly excluded and remain in A-04.


## A-03-003.25 — Storage completion callback audit

Source-backed audit is recorded in [A03_STORAGE_COMPLETION_CALLBACK_AUDIT.md](A03_STORAGE_COMPLETION_CALLBACK_AUDIT.md). The four service completion paths and AsyncStorageWriter were traced, including normal worker-thread completion, inline completion on rejected submission, and shutdown queue cancellation.

No universal storage callback runtime owner is introduced. Dirty generations and resource reset semantics remain owned by their respective services. Deterministic worker-thread/rejected-submit tests and a ResourceService stale-reset acknowledgement regression test are now present. ResourceService uses per-player reset generations for bulk, join and quit completion acknowledgement. HP Bar/Foliant in-flight coalescing is explicitly deferred until queue/latency metrics justify it. Storage drain/timeout semantics remain in A-04. This slice remains open pending full CI on one formatted HEAD.



## 13. A-03-003.26 — FX and projectile runtime task ownership

Source-backed findings are recorded in [A03_FX_RUNTIME_TASK_AUDIT.md](A03_FX_RUNTIME_TASK_AUDIT.md).

- `FxService` owns charged projectile trail, aura and ambient BukkitTask handles alongside VFX state, but has no explicit root-invoked shutdown cleanup boundary.
- Aura expiry callbacks are not handle-tracked; the existing identity guard prevents a stale callback from removing a replacement aura.
- `TrailListener` creates bounded projectile trail tasks (maximum 200 ticks) without retaining handles; tasks self-cancel on invalid/dead projectile and Paper cancels plugin tasks on disable.
- Frost Rune and Pentagram handles remain installation-scoped. Their `expire` methods cancel them, and `InstallationService.shutdown()` invokes `expire` for active installations.
- Do not merge installation task ownership into a generic FX owner.

The next bounded implementation candidate is an explicit, idempotent `FxService` shutdown boundary or a narrow `FxRuntimeOwner`, with focused tests for charged trails, aura replacement/expiration, ambient cancellation and partial cleanup. `TrailListener` should remain a separate decision because its tasks are short-lived and self-cancelling.

This focused audit does not close the full repository-wide scan or authorize `BootstrapCoordinator` activation.


## 14. A-03-003.27 — FX shutdown boundary implementation

FxService.shutdown() теперь закрывает все FX-owned Bukkit task handles через внутренний identity-based FxTaskRegistry. В реестр входят charged projectile trails, repeating aura tasks, delayed aura expiration callbacks и ambient loops. Неуспешные отмены остаются доступными для retry; повторный shutdown безопасен.

Aura expiration callback хранится и отменяется вместе с repeating aura task. Identity guard по repeating handle сохранён, поэтому callback предыдущей ауры не может удалить её замену. Root вызывает FX shutdown до закрытия остальных runtime компонентов. Domain VFX state и диагностические карты очищаются; общий scheduler, TrailListener и installation-scoped handles не включаются в эту границу.

Пять focused unit tests проверяют cancellation, idempotence, retry, независимую обработку ошибок и освобождение завершившегося one-shot callback. Paper 26.2 runtime smoke test остаётся отдельной проверкой.


## A-03-003.28 — Cooldown notification completion ownership audit

- Проверен branch HEAD d974d1307d6d4f698a9bcb1f227f6898a7e72ec2; audit-only commit добавил A03_COOLDOWN_NOTIFICATION_TASK_AUDIT.md.
- Подтверждён stale-handle path в CooldownManager.start(): естественное завершение delayed notification не освобождает её BukkitTask из tasks map.
- Следующий узкий implementation slice: finally-based completion release с identity guard, удаление пустой player map и focused tests на все callback exit paths + replacement race.
- Отдельно отмечены, но не смешаны с этим срезом: FoliantService.executeTransition() next-tick callback и RaskolClasses.handleRuntimeIntegrityFailure() main-thread disable callback.
- Полный repository-wide scan всех 390 Java source files всё ещё не закрыт; этот checkpoint не утверждает обратного.
- Production-код, main и BootstrapCoordinator не менялись.


## 15. A-03-003.29 — Cooldown notification completion release

CooldownManager now releases a naturally completed notification BukkitTask in a finally boundary. Removal is identity-safe (Map.remove(key, expectedHandle)), and empty per-player maps are removed conditionally so a stale callback cannot delete a replacement map. The notification delay and player-facing behavior remain unchanged.

Focused tests cover normal completion, early-return callback paths, callback exceptions, replacement races, and per-player map cleanup. This implementation slice does not change CooldownRuntimeOwner, persistence, gameplay state, main, or BootstrapCoordinator. Full CI and live Paper 26.2 verification remain separate gates.


## 16. A-03-003.30 — Foliant and RIPS one-shot callback ownership audit

Source-backed findings are recorded in [A03_FOLIANT_AND_RIPS_CALLBACK_OWNERSHIP_AUDIT.md](A03_FOLIANT_AND_RIPS_CALLBACK_OWNERSHIP_AUDIT.md).

- `FoliantService.executeTransition()` schedules a next-tick post-transition class verification but does not retain/register its BukkitTask. The callback captures Player and has no explicit quit behavior. This is bounded (one tick), not an unbounded leak; the remaining gap is explicit ownership and disconnect semantics.
- The next implementation candidate is a narrow Foliant-owned one-shot callback boundary with focused completion, quit-before-execution and shutdown tests. The exact seam remains an implementation design decision; no generic callback registry is authorized by this audit.
- `RaskolClasses.handleRuntimeIntegrityFailure()` schedules a one-shot security enforcement callback with duplicate guard, scheduling-failure reset and enabled-state guard. Its lifecycle is intentionally plugin-scoped and delegated to Paper's plugin scheduler.
- `RuntimeIntegrityRuntimeOwner` owns the monitor/executor, not the Paper enforcement callback. Do not add a shutdown cancellation path that could suppress an already accepted enforcement action without a separate security decision.
- `FoliantService.saveLocks()` completion acknowledgement remains in A-04 and is excluded.
- Production code and tests were not changed. `main` remains untouched; `BootstrapCoordinator` remains inactive.
- The repository-wide lifecycle scan remains open.


## 17. A-03-003.31 — Foliant transition callback ownership

Foliant post-transition class verification теперь принадлежит отдельному `PlayerCallbackRuntimeOwner`. Callback сохраняет задержку в один tick, хранит только UUID, разрешает текущий online Player при выполнении и пропускается после disconnect. Quit инициирует отмену; root shutdown закрывает owner до unregister Foliant listener. Cancellation failure логируется, а callback дополнительно защищён online re-resolution.

Переиспользованы focused owner tests на completion, quit cancellation, shutdown cancellation, stale callback rejection и retry. RIPS enforcement callback и Foliant storage completion acknowledgement не менялись. Полный repository-wide scan остаётся открытым.


## 18. A-03-003.31.1 — AsyncStorageWriter test stability

- Тест `successfulCompletionRunsOnStorageWorkerThread` стабилизирован: worker ожидает явный release после регистрации completion callback.
- Production-код `AsyncStorageWriter` не менялся.
- Build #3337 и Analyze #3337 завершились SUCCESS на одном финальном HEAD `724846ed35bdd68d68f43a521cc6619c8bbd292f`.
- Детали и CI checkpoint: `A03_ASYNC_STORAGE_WRITER_TEST_STABILITY.md`.
- `main` не менялся.

## 19. A-03-003.32 — Installation handler task ownership audit

- Проверены все Java handlers каталога `install/`: только `PentagramHandler` и `FrostRuneHandler` создают собственные BukkitTask; FrostRune ambient task принадлежит `FxService`.
- Общий sweep принадлежит `InstallationRuntimeOwner` и закрывается до `InstallationService.shutdown()`.
- Audit выявил batch cleanup failure-isolation gap; исходные findings и lifecycle paths сохранены в `A03_INSTALLATION_HANDLER_TASK_OWNERSHIP_AUDIT.md`.

## 20. A-03-003.33 — Installation cleanup failure isolation

- `InstallationCleanupCoordinator` изолирует RuntimeException каждого `expire()`, сохраняет неудачные записи и удаляет успешные только через identity-safe removal.
- `shutdown()`, `removeAllOf()`, `purgeStale()` и expiry paths в `sweep()` используют coordinator; cooldown cleanup не блокируется ошибкой отдельного handler.
- Pending installations повторно очищаются на следующем sweep до вызова gameplay tick.
- `FrostRuneHandler` и `PentagramHandler` сохраняют внутреннее cleanup-состояние до успешного завершения отдельных cleanup steps, чтобы повторная попытка могла завершить незаконченные действия без повторения уже успешных шагов.
- Добавлен `InstallationCleanupCoordinatorTest` на failure isolation, retry, identity safety и owner selection.
- Полный CI и Spotless Format на одном итоговом HEAD остаются обязательными gates; live Paper 26.2 runtime smoke test не заявляется.
- `main` остаётся неизменным; `BootstrapCoordinator` не активирован.
