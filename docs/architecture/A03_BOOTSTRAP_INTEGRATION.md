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
11. Validate configuration and construct attributes/HP synchronization; register HP listener and start sweep; construct HP bar service.
12. Configure Spec2 static bridge; construct storage, applier and service; refresh cache and register role listener.
13. Resolve optional PlayerPoints wallet; construct and load build templates; create plus storage/subscription and register listener/GUI.
14. Construct faction/flavor, installations, foliant and warlock abilities.
15. Construct pet service, register its listeners, start pet tick and orphan scan tasks.
16. Register remaining gameplay listeners, passport listener and session listener; reconcile online players.
17. Register optional PlaceholderAPI snapshots and expansions.
18. Start HUD/HP/resources/bossbar/flavor/install/scroll/mana/purge/autosave tasks according to configuration.
19. Register `/rc`, print startup banner and capacity warnings.
20. Start runtime RIPS monitoring and log successful startup.

### Shutdown sequence

The inspected `onDisable()` currently performs these operations:

1. Close and clear Placeholder snapshot service.
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
5. Placeholder snapshot service closes before the services it reads.
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

### A-03-003.8 — Next: first gameplay module ownership seam

Do not activate the full `BootstrapCoordinator` yet. Select the next bounded gameplay owner only after mapping its service references, task handles, listener registrations, partial-initialization cleanup and shutdown ordering. The next extraction must transfer actual ownership rather than wrap the God Object.

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
