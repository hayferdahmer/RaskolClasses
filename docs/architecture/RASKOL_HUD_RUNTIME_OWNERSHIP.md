# RaskolHUD — HUD-C Runtime Ownership Design

## Status

Living design and implementation record for HUD-C. The runtime seams and Paper adapters are implemented incrementally; the generic BootstrapCoordinator is not activated.

- Repository: hayferdahmer/RaskolClasses
- Branch: refactor/a03-foundation-lifecycle
- Platform: Paper 26.2 / Java 25
- Parent: [RASKOL_HUD_ARCHITECTURE.md](RASKOL_HUD_ARCHITECTURE.md)
- Data contracts: [RASKOL_HUD_DATA_CONTRACTS.md](RASKOL_HUD_DATA_CONTRACTS.md)
- Source inventory: [RASKOL_HUD_SOURCE_INVENTORY.md](RASKOL_HUD_SOURCE_INVENTORY.md)
- Target interaction spike: [RASKOL_HUD_TARGET_INTERACTION_SPIKE.md](RASKOL_HUD_TARGET_INTERACTION_SPIKE.md)

## 1. Goals and non-goals

HUD-C establishes one explicit owner for HUD runtime activity and presentation-channel resources.

Goals:
1. One lifecycle owner for HUD listeners, scheduled work, per-player runtime state and HUD-owned presentation handles.
2. Deterministic initialize/register/start/stop/close, including partial-start rollback.
3. Change-driven refresh with bounded coalescing; polling is only a configured fallback.
4. One arbitration point for ActionBar, HUD-owned BossBars and future Scoreboard output.
5. Player-session cleanup on quit and complete cleanup on reload/disable.
6. HUD failure degrades presentation only and must not disable gameplay or storage.
7. Preserve compatibility methods and current gameplay/persistence behavior during migration.

Non-goals:
- No visual redesign or renderer implementation in this stage.
- No changes to HP calculations, healing, max-health modifiers or health.yml.
- No changes to resource, combat, effects, class or specialization mechanics.
- No ownership of AsyncStorageWriter.
- No mandatory Resource Pack, BetterHUD or other runtime dependency.
- No BootstrapCoordinator activation in the first HUD-C slice.
- No assumption that crosshair targeting equals persistent MMORPG target selection.

## 2. Runtime ownership model

| Component | Owns | Must not own |
|---|---|---|
| HudRuntimeOwner | HUD lifecycle, registration orchestration, coordinator start/stop, aggregate cleanup | HP domain state, shared storage writer |
| HudCoordinator | dirty-player set, refresh coalescing, snapshot cadence, widget evaluation scheduling | Bukkit BossBar/Scoreboard handles |
| HudSnapshotProvider | main-thread read-only collection into immutable snapshots | mutable long-lived domain objects |
| HudWidgetRegistry | stable widget registration and deterministic evaluation order | Bukkit API or gameplay mutation |
| HudChannelCoordinator | per-player channel arbitration, TTL, priority and output cleanup | direct gameplay service reads |
| Renderer adapters | Paper/Adventure conversion and HUD-owned output handles | refresh policy or domain state |
| HudSessionCleanup | quit cleanup for caches, notifications, bars and per-player presentation state | player progression or persistence |

Small stateless collaborators may be combined initially, but lifecycle ownership and channel ownership must remain explicit and testable.

## 3. Existing service migration boundaries

| Existing component | Current responsibility | Migration rule |
|---|---|---|
| HudService | resource ActionBar formatting, task tick, per-player render caches | Initially adapt; transfer task/cache cleanup without changing output semantics |
| BossBarService | active-effect BossBars and task | Keep as legacy renderer during first seam; prevent duplicate starts |
| HpBarService | HP mechanics, healing, persistence, HP display and ActionBar | Preserve mechanics/persistence; extract presentation only after call-site characterization |
| RaskolClasses | composition root, compatibility getters, shared task list and shutdown | Retain facade; remove HUD tasks from root only after explicit ownership transfer |

Critical constraints:
- HpBarService is not a HUD-only service and must not be moved wholesale.
- health.yml, SafeStorage, dirty-generation semantics and shared async writer usage remain unchanged.
- HP save submission must precede shared writer shutdown.
- getHud(), getBossBars() and getHpBarService() remain compatible during migration.
- Existing YAML keys/defaults remain readable; new config becomes an immutable validated snapshot.
- Old and new owners must never schedule duplicate ActionBar/BossBar producers for one player/channel.

## 4. Lifecycle contract

### initialize
- Validate/freeze HUD config, channel priorities, fallback refresh interval and optional Resource Pack policy.
- Construct provider, registry, coordinator, channel owner and renderer adapters.
- No repeating tasks, visible output or externally observable listener registration.
- Do not guess an unresolved target source.

### register
- Register HUD-owned listeners and optional internal event subscriptions.
- Register quit/session cleanup exactly once.
- On partial registration failure, unregister already registered listeners/subscriptions.
- PlaceholderAPI remains optional; its absence does not fail HUD registration.

### start
- Start coordinator and bounded fallback task only after dependencies/registrations are ready.
- Start is idempotent; repeated start creates no duplicate tasks.
- On partial scheduling/renderer failure, cancel created tasks, clear partial output and aggregate errors.
- HUD startup failure is isolated: disable HUD runtime only, not RaskolClasses.

### stop
- Stop accepting dirty notifications first.
- Cancel refresh/fallback producers before clearing output.
- Clear only RaskolHUD-owned ActionBar/BossBar/Scoreboard resources.
- Clear transient snapshots, widget frames, TTL entries, animation state and notification delivery state.
- Continue cleanup after individual failures; do not mutate or save gameplay state.

### close
- Unregister listeners/subscriptions exactly once.
- Release renderer/channel handles and any owned executor resources.
- Idempotent and safe after partial initialize/register/start.
- Never close shared storage or services owned by other modules.

## 5. Scheduling and refresh policy

Preferred flow: domain event/state revision -> markDirty(UUID) -> coalesce -> snapshot -> widgets -> channel arbitration -> renderer.

1. Initial Bukkit-backed capture runs on the main thread.
2. No Bukkit API calls from async workers.
3. Multiple events for one player in a coalescing window produce one refresh.
4. A dirty player is removed only after a defined snapshot outcome; transient source failure must not silently lose a retry.
5. Offline players are discarded and passed through session cleanup.
6. Polling is bounded and used only for sources without reliable events.
7. Do not poll every service every tick.
8. Refresh has a per-tick budget/batch limit.
9. Snapshot revision is a per-player publication sequence, not a domain-wide revision.
10. One capturedAtMillis anchors all relative effect/DoT durations.

## 6. Channel arbitration

HudChannelCoordinator is the only writer for HUD-owned ActionBar/BossBar/Scoreboard output.

- ActionBar: one resolved frame per player per arbitration cycle; priority then deterministic widget-id tie-break; short-lived notifications may preempt routine status and expire.
- BossBar: multiple HUD-owned bars may coexist, keyed by stable widget/source identity; max-visible policy is centralized.
- Scoreboard: disabled until external-plugin compatibility policy is approved. Never replace another plugin's sidebar/teams without explicit ownership handoff.
- Resource Pack: optional enhancement only. Without a compatible accepted pack, renderers use semantic text/icon fallback.
- Legacy BossBarService must not retain a second scheduling loop after migration to channel owner.
- Existing HudService and HpBarService ActionBar producers require explicit mode arbitration before enabling a new renderer.
- Target frame consumes a resolved Locked TargetView; channel arbitration does not own target selection. Crosshair movement cannot silently retarget.

## 7. Failure isolation and diagnostics

- Provider failure marks only affected fields UNAVAILABLE; it does not fabricate zeroes.
- One widget exception suppresses that widget frame for the current cycle; other widgets continue.
- One renderer failure quarantines only that renderer/feature under a bounded policy.
- Channel failure clears its partial handles and does not prevent other channel cleanup.
- Cleanup attempts all owned resources and aggregates/logs failures.
- No broad silent catch; fatal VM errors are not converted into ordinary HUD failures.
- Diagnostics distinguish config rejection, unavailable dependency, scheduling, widget, renderer and cleanup failures.

## 8. Resource Pack capability

The approved policy is optional.
- Baseline mode works without a pack and exposes all critical values through standard channels.
- Enhanced mode may use branded glyphs/assets only with a compatible accepted pack.
- Declined, failed, missing or outdated pack falls back without disabling HUD.
- Resource Pack capability is presentation state, not character/gameplay state.
- URL, hash, version negotiation, prompt frequency and exact YAML keys are deferred to HUD-D/config.

## 9. Test plan and acceptance criteria

Unit tests:
- lifecycle order and repeated start/stop/close idempotence;
- close after partial initialize/register/start;
- listener registration rollback after partial failure;
- scheduling rollback after a later task fails;
- cancellation failure does not skip remaining cleanup;
- dirty coalescing, per-tick budget and bounded fallback;
- offline-player cleanup;
- widget evaluation order and exception isolation;
- renderer/channel exception isolation;
- ActionBar priority, tie-break and TTL;
- BossBar stable-key ownership and cleanup;
- Scoreboard remains disabled;
- missing/declined/failed/outdated Resource Pack fallback;
- no Bukkit API access from async threads.

Characterization/integration tests:
- legacy HudService output is unchanged for equivalent config/state;
- BossBarService still displays active effects without duplicate bars/tasks;
- HpBarService calculations, healing, health.yml, save timing and public API remain unchanged;
- every legacy ActionBar mode has exactly one producer after migration;
- vanilla/actionbar mode reload leaves exactly one correct producer;
- quit/rejoin starts with clean HUD state;
- disable clears HUD-owned output and leaves shared writer lifecycle intact.

Acceptance gate:
- [ ] Every HUD task/listener/handle has one explicit owner.
- [ ] No duplicate producer exists for a channel/player.
- [ ] Partial startup and cleanup failures are tested.
- [ ] Refresh is event-driven with bounded fallback.
- [ ] Scoreboard remains off pending compatibility decision.
- [ ] Optional Resource Pack fallback is tested.
- [ ] HP/resource/combat semantics and storage formats are unchanged.
- [ ] RaskolClasses compatibility facade remains functional.
- [ ] Maven verification, Spotless and SpotBugs pass on Java 25/Paper 26.2.
- [ ] Controlled Paper 26.2 test covers reload, quit/rejoin and disable.

## 10. Implementation slices

1. HUD-C.1 — runtime seams and test doubles: scheduler/task/listener/channel ports and deterministic fakes; no production wiring.
2. HUD-C.2 — coordinator core: dirty coalescing, bounded refresh, immutable snapshot pipeline and widget evaluation.
3. HUD-C.3 — channel owner: ActionBar arbitration and HUD-owned BossBar registry; Scoreboard remains disabled.
4. HUD-C.4 — legacy adapter migration: move HudService/BossBarService task ownership in small steps.
5. HUD-C.5 — session cleanup and reload: quit cleanup, mode switching, partial rollback and idempotent shutdown.
6. HUD-C.6 — root integration: remove transferred tasks from activeTasks, keep compatibility getters, isolate HUD startup failure.
7. HUD-C.7 — verification: focused tests, full Maven/Spotless/SpotBugs and controlled Paper test.

Do not combine all slices in one commit. Each slice must be independently reviewable and tested.

## 11. Open decisions and dependencies

1. Target Frame semantics is approved as Locked Target: show the last explicitly selected target; camera/crosshair movement does not retarget. The explicit selection UX is still open and must be owned outside HUD.
2. External channel conflict policy: coexistence with other plugins' BossBars and whether/when to claim Scoreboard. Until agreed, Scoreboard remains disabled.
3. Resource Pack delivery details: optional is approved; exact hash/version/URL/prompt/update details are deferred to HUD-D/config.
4. Legacy source characterization: re-read current feature-branch source and enumerate producers/callers before migration; default-branch code search may be stale.

## 12. Decision

HUD-C architecture design is complete enough to begin bounded implementation slice HUD-C.1. This is a design milestone only; no runtime code has been changed by this document.

## 13. H-05 — Locked Target decision

The approved target model is the last explicitly selected/locked entity. HUD does not retarget from crosshair movement. Selection belongs to a separate targeting/application owner and is exposed to HUD through a read-only immutable TargetHudReadPort. H-06 defines right-click on an eligible entity as the primary selection UX; clearing and cycling remain separate explicit actions. Do not invent a client-side keybind or require a client mod.


## 14. HUD-C.1 — First runtime seams committed

The initial production-code seam extraction adds four Bukkit-independent contracts under `dev.raskol.classes.hud.runtime`:

- `HudTaskScheduler` — schedules repeating work and returns the existing lifecycle `TaskHandle` abstraction;
- `HudSessionEvents` — exposes player quit by UUID and returns a closeable `HudRegistration`;
- `HudRegistration` — idempotent registration handle contract;
- `HudOutputCleanup` — clears only RaskolHUD-owned per-player or global presentation output.

These interfaces do not register Bukkit listeners, schedule real Paper tasks, change `RaskolClasses.activeTasks`, start renderers or alter legacy output. The coordinator-facing lifecycle consumer and deterministic test doubles are now documented in HUD-C.1.2 below. Concrete Paper adapters and root integration remain deferred.


## 15. HUD-C.1.2 — Runtime owner and deterministic tests

The first consumer of the HUD runtime ports is implemented as `HudRuntimeOwner`.

Responsibilities:
- registers exactly one quit subscription and clears only the departing player's HUD-owned output;
- requires successful registration before starting scheduled work;
- owns one repeating refresh task through `TaskScope`;
- treats repeated register/start/close calls idempotently;
- cancels the refresh producer before clearing all HUD-owned output;
- rolls back task ownership and clears partial output when scheduling fails;
- retains the started/owned state when cancellation cannot be confirmed, preventing a second producer from being scheduled before retry;
- continues unregistration and task-scope closure when output cleanup reports an error;
- validates non-negative initial delay and strictly positive period;
- does not depend on Bukkit, mutate gameplay state, or own shared storage.

Deterministic tests use local fake implementations of `HudTaskScheduler`, `HudSessionEvents`, and `HudOutputCleanup`. They exercise the actual owner rather than testing the fakes in isolation.

Coverage added:
- idempotent registration and start;
- quit event delegates per-player cleanup;
- start-before-register rejection;
- scheduling failure rollback and retry;
- producer cancellation precedes global output cleanup;
- idempotent close and listener unregistration;
- cleanup failure does not skip listener unregistration;
- cancellation failure retains task ownership, prevents duplicate restart, and allows retry;
- invalid scheduling interval rejection.

Implementation files:
- `src/main/java/dev/raskol/classes/hud/runtime/HudRuntimeOwner.java`
- `src/test/java/dev/raskol/classes/hud/runtime/HudRuntimeOwnerTest.java`

This slice does not wire a Paper listener or scheduler adapter into `RaskolClasses`, does not move legacy HudService/BossBarService tasks out of `activeTasks`, and does not change ActionBar/BossBar output. Maven/Spotless/SpotBugs verification is pending; the presence of tests is not evidence that they have passed.


## 16. HUD-C.1.3 — Paper adapters

Implemented Paper-facing adapters for the runtime ports:

- `PaperHudTaskScheduler` delegates repeating work to Paper's synchronous `runTaskTimer`; the returned `BukkitTask` is wrapped by the lifecycle `TaskHandle` contract.
- `PaperHudSessionEvents` registers a dedicated listener for `PlayerQuitEvent`, forwards only the player's UUID, and returns a registration handle that unregisters only that listener.
- Quit cleanup exceptions are logged and isolated from the server event dispatch; they do not escape into unrelated quit listeners.
- Listener unregistration is idempotent and restores its open state if the underlying unregister operation throws, allowing a later close retry.
- `HudRuntimeOwner.close()` now retains failed registration cleanup and supports retry after a close failure. While closing is incomplete, new registration/start operations are rejected.

Tests added in `PaperHudAdaptersTest` use JDK dynamic proxies around Paper interfaces; they verify synchronous scheduler delegation, action forwarding, interval validation, task cancellation, listener registration and repeated registration close. Existing `HudRuntimeOwnerTest` also covers retry after listener-unregistration failure.

Scope boundary:
- These adapters are production code, but they are not yet wired into `RaskolClasses`.
- Legacy `HudService` and `BossBarService` task ownership remains unchanged.
- No new ActionBar/BossBar renderer or target-selection UX is activated.
- No Resource Pack files or delivery configuration are introduced in this slice.

Verification status:
- Source files and tests are committed to the feature branch.
- Maven/Spotless/SpotBugs and a controlled Paper 26.2 runtime test must still pass before this slice is considered verified.

## 17. Target interaction decision — H-06

Пользователь выбрал ПКМ по допустимому существу как основной UX фиксации цели. HUD-C.1.4 проверил Paper API и выбрал `PlayerInteractAtEntityEvent` с `MONITOR`, `ignoreCancelled = true`, только main hand и без отмены события; подробности и ограничения зафиксированы в [RASKOL_HUD_TARGET_INTERACTION_SPIKE.md](RASKOL_HUD_TARGET_INTERACTION_SPIKE.md). Реальный listener и targeting/application owner ещё не реализованы и не входят в HudRuntimeOwner. HUD получает только read-only TargetView; наведение камеры не меняет locked target. Controlled Paper 26.2 runtime test с NPC-интеграциями остаётся обязательным до активации.

