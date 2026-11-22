# A-03-003.4 — Bootstrap Integration Design

## Статус

Design approved for the next implementation slices. This document does not activate the new bootstrap runtime and does not change gameplay behavior.

- Repository: `hayferdahmer/RaskolClasses`
- Target platform: Paper 26.2 / Java 25
- Working branch: `refactor/a03-foundation-lifecycle`
- Baseline: `1acce195f8c23d572361f9659581f1a3806ac153`

## 1. Decision

Adopt `BootstrapCoordinator` incrementally. Do not replace the current `RaskolClasses.onEnable()` / `onDisable()` implementation in one change.

The current plugin entry point is still the composition root and compatibility facade. It owns a large number of concrete service fields and getters, while many services receive the concrete `RaskolClasses` instance. A direct conversion into independent modules would therefore create a large dependency migration, not a safe bootstrap-only change.

The first integration release must preserve the existing startup and shutdown order exactly. New module wrappers may initially delegate to extracted lifecycle methods, but must not become a second service locator or introduce duplicate ownership.

## 2. Current lifecycle inventory

### Startup sequence

The inspected `onEnable()` currently performs these operations in this order:

1. RIPS artifact-bound startup verification; disable plugin and return when policy denies startup.
2. Construct `RaskolConfig`, run config migration, log platform information and run startup checks.
3. Resolve `PluginManager` and initialize class provider.
4. Create `AsyncStorageWriter`, item system/API, combat profile registry and its lifecycle listener.
5. Register item blueprints.
6. Construct character level and resource services; connect resource service to class provider.
7. Construct cooldown manager and attach scheduler; create effects and ability registry; load abilities and register casters.
8. Construct HUD, boss-bar, token and FX services; validate FX config.
9. Construct resist/combat services; conditionally start items; register DoT listeners and start DoT task; create ManaSoaked.
10. Construct/register CC service and task, CC guard, cast guard and vanilla CC wrapper.
11. Validate configuration and construct attributes/HP synchronization; transfer listener and 100-tick sweep to `HpAttributeSyncRuntimeOwner`; construct HP bar service.
12. Configure Spec2 static bridge; construct storage, applier and service; refresh cache and register role listener.
13. Resolve optional PlayerPoints wallet; construct and load build templates; create plus storage/subscription and register listener/GUI.
14. Construct faction/flavor, installations, foliant and warlock abilities.
15. Construct pet service, register its listeners, start pet tick and orphan scan tasks.
16. Register remaining gameplay listeners, passport listener and session listener; reconcile online players.
17. Start PlaceholderRuntimeOwner when PlaceholderAPI is present: initial snapshot, 5-tick refresh producer, then both expansions.
18. Start HUD/HP/resources/bossbar/flavor/install/scroll/mana/purge/autosave tasks according to configuration.
19. Register `/rc`, print startup banner and capacity warnings.
20. Start runtime RIPS monitoring and log successful startup.

### Shutdown sequence

The inspected `onDisable()` currently performs these operations:

1. Close PlaceholderRuntimeOwner (cancel refresh producer → unregister expansions → clear snapshots).
2. Stop runtime RIPS monitor.
3. Stop HP sweep and unregister blueprints.
4. Cancel Warlock channel tasks.
5. Persist dirty foliant locks.
6. Despawn pets **before cancelling general active tasks**.
7. Cancel and clear `activeTasks`.
8. Stop item system and unregister passport listener.
9. Save resources, HP bar data and Spec2 storage.
10. Shutdown installations and boss bars.
11. Cancel cooldown tasks, save cooldowns and clear cooldown state.
12. Shutdown class provider and clear active effects.
13. Close `AsyncStorageWriter` with a 10-second graceful wait.

These are compatibility constraints, not an endorsement of the current God Object. They must remain explicit until ownership is transferred and verified.

## 3. Lifecycle ownership boundaries

The target bootstrap is split into modules by lifecycle ownership, not by arbitrary package names.

| Candidate module | Owns / coordinates | Notes |
|---|---|---|
| `configuration` | config construction, migration, validation | Must precede services that read config |
| `storage` | AsyncStorageWriter and storage-backed service setup | Writer closes only after all final saves are submitted |
| `character` | class provider, skill/character levels, resources, attributes | Current cross-links require staged extraction |
| `items` | ItemSystem, ItemsApi, blueprint registration | Preserve config-gated ItemSystem.start |
| `abilities` | cooldowns, ability registry, caster registration, effects | Depends on character/resources and config |
| `combat` | resist/combat/DoT/CC/ManaSoaked services | Preserve distinct vanilla and custom damage paths |
| `spec` | Spec2 storage, service, applier, wallet/build subscriptions | Preserve Spec2Points.configure ordering |
| `world` | installations, pets, foliant, faction/flavor | Pet stop has a pre-task-cancellation requirement |
| `presentation` | HUD, boss bars, HP bar, FX, GUI listeners | Depends on domain/application services |
| `integrations` | LuckPerms, PlaceholderAPI, PlayerPoints, optional hooks | Optional integrations must degrade independently |
| `commands` | `/rc` registration and command adapters | Register only after required services are ready |
| `integrity` | startup verification and runtime monitor | Startup verification remains before gameplay setup |

This is a target ownership map, not a requirement to create eleven classes in one commit. The first real extraction should use coarse modules with explicit internal collaborators, then split only where ownership and dependencies are proven.

## 4. Dependency ordering

The initial dependency graph must express only hard lifecycle prerequisites. Optional plugins must not become hard module dependencies.

Proposed high-level order:

1. `configuration`
2. `storage`
3. `character`
4. `items`
5. `abilities`
6. `combat`
7. `spec`
8. `world`
9. `presentation`
10. `integrations`
11. `commands`

The graph above is a planning outline. Before coding module IDs and edges, each constructor and registration call must be traced to avoid encoding false dependencies or cycles.

RIPS startup verification is a pre-bootstrap gate and remains in `JavaPlugin.onEnable()` until a separate security lifecycle contract is designed. Runtime monitoring starts only after the gameplay bootstrap reports successful completion.

## 5. Required lifecycle mapping

- `initialize(context)`: construct module-owned services and immutable configuration snapshots; no repeating tasks; avoid listener registration unless the existing API requires a strict earlier registration point.
- `register()`: register listeners, commands, placeholders and external integration hooks after all required services have been constructed.
- `start()`: start repeating tasks and accept normal gameplay operations.
- `stop()`: stop producers and module-specific activity; perform required final saves before shared storage shutdown.
- `close()`: release owned resources and references; must be idempotent.

The coordinator's shared phases are useful only if the module boundaries respect them. Any existing constructor with side effects must be identified and either made side-effect-free or explicitly scheduled in the correct phase before a module is introduced.

## 6. Non-negotiable shutdown constraints

1. Pet despawn remains before cancellation of the pet tick/orphan tasks. The pet module's `stop()` must perform despawn before its `TaskScope` is closed.
2. All final YAML/PDC-backed snapshots must be captured/submitted before `AsyncStorageWriter.closeAndAwait(Duration.ofSeconds(10))`.
3. Runtime integrity monitoring must stop before plugin-owned infrastructure is released.
4. Bukkit tasks must be cancelled before their scheduler/plugin-owned references are released.
5. Placeholder runtime stops before the services it reads; its owner cancels the refresh producer and unregisters both expansions before clearing snapshots.
6. Blueprint hooks, passport listeners and other external registrations must be unregistered exactly once.
7. Item system, installations, boss bars, cooldowns, class provider and effects retain their current shutdown semantics until their ownership-specific tests exist.
8. Shutdown must continue after an individual cleanup failure and report aggregated failures without skipping later cleanup.
9. No module may close the shared storage writer. The writer is owned by the root bootstrap/infrastructure lifecycle and closes last.

## 7. Integration strategy

### A-03-003.5 — Lifecycle seam extraction

Extract the existing startup/shutdown sequence into named, package-private orchestration methods without changing statement order. Add characterization tests around phase ordering using narrow internal seams where feasible. Do not yet route execution through the coordinator.

### A-03-003.6 — Legacy runtime adapter

Introduce one temporary `LegacyRuntimeModule` only if it reduces risk and has a single clear owner. It may delegate to the extracted legacy lifecycle methods. It must not register every concrete service in a global registry, and it must not coexist with duplicate direct calls from `onEnable()`.

If the adapter merely wraps the entire God Object without improving ownership or testability, skip it and proceed to coarse real modules.

### A-03-003.7 — Runtime integrity owner extraction

Implemented in bounded slices. `RuntimeIntegrityRuntimeOwner` now owns the runtime monitor, dedicated daemon executor, 30-minute interval, state manager and reporter. The Paper-specific failure callback remains injected from `RaskolClasses`.

The RIPS startup verification and startup-denial gate remain the first operation in `onEnable()`. Runtime monitoring starts only from `finishStartup()`, after gameplay initialization, and closes near the beginning of `shutdownRuntime()`.

Focused tests cover owner close, close-before-start, repeated close, interval preservation, concurrent starts and cleanup after rejected scheduling. The tests are committed; Maven execution and CI confirmation remain pending. See [A03_RUNTIME_INTEGRITY_OWNER.md](A03_RUNTIME_INTEGRITY_OWNER.md).

### A-03-003.8 — Pet runtime ownership seam (assessment completed)

`PetService` is selected as the first gameplay runtime ownership boundary. The source-level assessment is recorded in [A03_PET_RUNTIME_OWNERSHIP.md](A03_PET_RUNTIME_OWNERSHIP.md).

The assessment confirms that `PetService` has one listener, two explicit scheduled tasks, a constructor without listener/scheduler side effects, and a mandatory `despawnAll()` operation that must run before task cancellation. At present, `RaskolClasses` owns both BukkitTask handles in its general `activeTasks` list and directly registers the listener. No pet task should remain in that root list after the ownership transfer.

### A-03-003.9 — PetRuntimeOwner (implemented; verification pending)

Implemented the narrow owner and its Paper adapters. The owner now controls listener registration, both task handles, partial-start rollback, pre-cancellation despawn, task cancellation and listener unregistration. The existing 20-tick pet task cadence and 100-tick orphan-scan delay remain delegated to the unchanged PetService methods. `getPets()` and existing callers remain compatible. The two pet tasks were removed from root `activeTasks`. Focused tests were added; Maven/CI verification is pending. See [A03_PET_RUNTIME_OWNERSHIP.md](A03_PET_RUNTIME_OWNERSHIP.md).

### A-03-003.10 — Remaining lifecycle ownership inventory (first pass documented; full scan pending)

A source-backed first-pass inventory is recorded in [A03_REMAINING_LIFECYCLE_OWNERSHIP_INVENTORY.md](A03_REMAINING_LIFECYCLE_OWNERSHIP_INVENTORY.md). It maps root periodic tasks, internal task-bearing services, listener/external registrations and persistence/shutdown ordering. The inventory identifies `HpAttributeSync` as the next bounded owner extraction because its listener + sweep task + delayed join sync form a narrow boundary. The full repository-wide task/registration scan remains an explicit gate; do not activate `BootstrapCoordinator` until that scan and deterministic rollback/storage-drain tests are complete.



### A-03-003.11 — HpAttributeSyncRuntimeOwner (implemented; verification pending)

`HpAttributeSyncRuntimeOwner` now owns the Paper listener registration and 100-tick periodic sweep. The domain-facing `HpAttributeSync` retains sync operations and event handlers; its old internal sweep handle/start/stop methods were removed. `PaperHpAttributeSyncRegistration` and `PaperHpAttributeSyncScheduler` isolate Bukkit registration/scheduling. `RaskolClasses` is composition wiring and closes the owner during shutdown before storage cleanup. Focused tests cover idempotent start/close, registration and scheduling rollback, cancellation failure retry, and invalid interval. The 2-tick delayed respawn callback remains an explicitly documented one-shot lifecycle follow-up. No HP formula, modifier, persistence or player-visible behavior was intentionally changed.

Next bounded candidate: `CooldownManager` listener + per-player delayed-task ownership, with persistence kept as a separate responsibility. Do not activate `BootstrapCoordinator` yet; full task/registration scan remains a gate.

Coordinator activation remains gated on characterization tests for startup, rollback and shutdown ordering:
- create and validate the real module graph;
- activate `BootstrapCoordinator` only after module ownership is proven;
- ensure startup failure triggers deterministic rollback and plugin disable;
- delegate `onDisable()` only when all critical cleanup paths are represented;
- retain the RIPS preflight gate outside the gameplay graph;
- preserve the 10-second storage drain as the final infrastructure shutdown action.

## 8. Risks and explicit non-goals

- No gameplay balance, combat or persistence format changes.
- No YAML key, PDC key, namespace or serialization changes.
- No public API removal or rename.
- No conversion of all services to `PluginModule`.
- No reflection/annotation injection.
- No global domain service locator.
- No hard dependency on optional plugins or absent RaskolVault.
- No change to the 1.15.8 gameplay baseline.
- No attempt to solve all known domain cycles in the bootstrap package.

## 9. Acceptance criteria before activation

- [ ] Startup statement order is captured and characterized.
- [ ] Every scheduled task has one explicit owner and a cancellation path.
- [ ] Every listener/placeholder/command registration has one matching owner and cleanup rule where applicable.
- [ ] Constructor side effects are inventoried.
- [ ] Dependency graph has no missing nodes or cycles.
- [ ] Pet despawn ordering is tested.
- [ ] Final saves precede writer shutdown; writer remains last.
- [ ] RIPS startup denial still prevents all gameplay initialization.
- [ ] Runtime integrity monitor starts only after successful gameplay bootstrap and stops before infrastructure shutdown.
- [ ] Partial initialization failure is safe at every extracted module boundary.
- [ ] Full `mvn -B verify` and `mvn -Pdev -B verify` pass on Java 25 / Paper 26.2.
- [ ] Existing server-facing behavior remains unchanged in a controlled test deployment.

## 10. Decision summary

A-03-003.4 establishes the integration contract and migration order. It does not activate `BootstrapCoordinator`. The next implementation unit is A-03-003.5: extract lifecycle seams while preserving the current sequence byte-for-byte in behavior.


### A-03-003.14 — PlaceholderAPI runtime ownership (implemented)

PlaceholderRuntimeOwner now coordinates the optional PlaceholderAPI lifecycle. PlaceholderSnapshotService owns only snapshot data and main-thread refresh logic; it no longer owns a BukkitTask. PaperPlaceholderRuntimeScheduler owns the Paper scheduling boundary, while PaperPlaceholderRuntimeRegistration owns both expansion instances and their reversible registration.

The original behavior is preserved:
- initial snapshot is built immediately;
- refresh runs every 5 ticks on the server thread;
- async PlaceholderAPI callbacks read only immutable snapshots by UUID;
- identifiers, parameters and returned values are unchanged;
- missing or failed PlaceholderAPI integration does not prevent the core plugin from starting.

Startup rollback cancels a created task, attempts to unregister any expansion registration that may have started, and clears snapshots. Shutdown uses cancel → unregister → clear. Cleanup failures are logged without skipping the remaining root shutdown sequence; the owner reference is retained when cleanup is incomplete so its retry path is not discarded.

Focused tests are added in PlaceholderRuntimeOwnerTest. CI verification is tracked separately and must not be considered complete until the branch workflow finishes successfully.


### A-03-003.15 — CrownFlavorService runtime ownership

`FlavorRuntimeOwner` now owns the CrownFlavorService listener registration and 40-tick aura producer through narrow ports and Paper adapters.

The root preserves the original startup phases:
- listener registration occurs in `registerGameplayListeners`;
- aura scheduling occurs in `startRuntimeTasks`;
- aura task handles are no longer stored in root `activeTasks`.

`CrownFlavorService` retains faction lookup, title lookup, join messaging and particle behavior. Its `tickAura()` method performs one cycle only; scheduling belongs to `PaperFlavorTaskScheduler`.

Shutdown cancels the aura producer before unregistering the listener. Startup rollback and shutdown cleanup independently attempt task cancellation and listener unregistration; failed cleanup remains retryable. The original 40-tick initial delay and period are preserved.


### A-03-003.16 — BossBar runtime ownership

`BossBarRuntimeOwner` owns the recurring BossBar producer and cleanup of all created bars. `BossBarService` retains rendering and per-player bar state but no longer stores or schedules a BukkitTask. No listener registration is involved because BossBarService is not a Listener.

The Paper scheduler preserves the configured period and the original initial delay (both equal to `max(1, bossBarTickPeriod())`). Configuration reload delegates to owner restart, which cancels the previous producer, clears all bars and schedules a replacement with the current period. Shutdown cancels the producer and removes all bars. The BossBar task is no longer stored in root `activeTasks`.

Focused tests cover idempotent start, restart ordering, close cleanup, scheduling/cancellation failures, retry behavior and period clamping. Gameplay rendering rules and configuration keys are intentionally unchanged.


### A-03-003.17 — ActionBar HUD runtime production wiring

`HudRuntimeOwner` is wired into `RaskolClasses`. `PaperHudSessionEvents` owns a quit listener, `PaperHudTaskScheduler` owns the repeating producer, and `HudServiceOutputCleanup` delegates per-player/global cache cleanup to `HudService`. The service exposes a single `tick()` callback and no longer owns a BukkitTask.

The legacy activation condition is preserved at startup (`hud.enabled && hp-display.mode=vanilla`). Configuration reload reconciles the runtime with the current condition by starting or stopping the owner. Shutdown closes the owner before the remaining runtime cleanup. Cleanup removes only ActionBar HUD cache state and does not send an empty ActionBar, avoiding interference with HP display or other plugins. HP bar remains independent.


### A-03-003.18 — HP bar runtime ownership

`HpBarRuntimeOwner` coordinates the `HpBarService` lifecycle listener and periodic tick. `PaperHpBarRuntimeRegistration` registers/unregisters the service listener, and `PaperHpBarTaskScheduler` schedules the synchronous callback. Root startup phase ordering is preserved: registration occurs in `registerGameplayListeners()`, scheduling in `startRuntimeTasks()`. Shutdown closes the owner before `hpBarService.saveAll()` and before the shared `AsyncStorageWriter` is closed.

The service retains all HP state, formulas, regeneration, restore logic, UI rendering, dirty tracking and persistence. Its former `start()` method and private scheduler callback are replaced by `tick()`. Period remains clamped to at least one tick and the original initial delay equals the period. No data format or player-facing behavior is intentionally changed.


### A-03-003.19 — Separate DoT and Crowd Control runtime owners

DoT and CC use separate lifecycle owners and separate `TaskScope` instances. The owners coordinate only their corresponding listener registration and periodic task; they do not hold effect instances or gameplay state.

- `DotService` remains the state owner for active DoTs, stacks, expiration, attribution, calculations, damage, VFX and projectile-hit application. `DotRuntimeOwner` owns its listener and the 20-tick producer (initial delay 20 ticks).
- `CCService` remains the state owner for active CC instances, DR state, fear timing, slow restoration state, expiration, break-on-damage and mob behavior. `CCRuntimeOwner` owns its listener and the 1-tick producer (initial delay 1 tick).
- `CCGuard`, `CastGuard` and `VanillaCCWrapper` remain separate. No combined CC/DoT owner was introduced.

Each owner independently rolls back startup, cancels its task before listener unregistration, and retries failed cleanup. Root no longer stores these two handles in `activeTasks`. Gameplay state and the original 20-tick/1-tick scheduling contracts remain unchanged.

`CCFeedback` hologram maps and delayed one-shot cleanup tasks remain a separate follow-up; they are not merged into the periodic CC owner.
