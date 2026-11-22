# A-03-003.6 — Legacy Runtime Adapter Assessment

## Status

**Decision: do not introduce `LegacyRuntimeModule` at this stage.**

The assessment is read-only with respect to runtime behavior. It records why a wrapper around the current `RaskolClasses` lifecycle would add an abstraction without transferring meaningful ownership.

- Repository: `hayferdahmer/RaskolClasses`
- Branch: `refactor/a03-foundation-lifecycle`
- Baseline: `1acce195f8c23d572361f9659581f1a3806ac153`
- Target platform: Paper 26.2 / Java 25

## 1. Evidence from the current composition root

The current `RaskolClasses` remains both the Paper entry point and a compatibility facade:

- It owns concrete references to configuration, storage, character/resource, combat, ability, item, specialization, world and presentation services.
- It exposes a broad set of getters used by existing services and integration code. Those getters are still part of the compatibility surface; moving lifecycle calls alone would not remove the dependency on the concrete plugin class.
- `activeTasks` is a root-owned `List<BukkitTask>`. Existing tasks are registered directly in this list rather than through module-specific `TaskScope` instances.
- The shared `AsyncStorageWriter` is created by the root and closed only after all final save calls.
- The runtime RIPS monitor has a dedicated executor and a plugin-disable callback; startup verification remains a pre-bootstrap gate.

This means a wrapper would not yet own the services, task handles, registrations or shared storage that it coordinates.

## 2. Lifecycle phases do not currently match the coordinator contract

The extracted methods improve locality, but they are not yet clean module lifecycle phases.

### Work performed during `initializeCoreServices()`

The method does more than construct services. In the existing order it also:

- registers the combat-profile listener and item blueprints;
- starts the item system when enabled;
- registers and starts DoT and CC runtime behavior;
- registers the HP synchronization listener and starts its sweep;
- registers the Spec2 role listener and Plus subscription/GUI listeners;
- starts the foliant lock-flush task;
- registers pet listeners and starts pet tick/orphan-scan tasks.

### Work performed outside the apparent phase boundaries

- `registerOptionalIntegrations()` starts the Placeholder snapshot service before registering its expansions.
- `finishStartup()` starts runtime RIPS monitoring after the gameplay startup sequence.
- `shutdownRuntime()` performs ordering-sensitive work across multiple owners and closes shared storage last.

Therefore, mapping the current methods directly to `initialize/register/start/stop/close` would be semantically misleading.

## 3. Why a single legacy adapter is rejected

A single `LegacyRuntimeModule` has only two plausible implementations, and neither is an acceptable long-term seam.

### Option A — delegate the entire startup to one phase

The adapter could run all legacy startup operations inside `initialize()`, leaving `register()` and `start()` empty.

This would preserve the existing order, but it would defeat the coordinator's shared phase semantics. The module lifecycle would report `INITIALIZED` while listeners and repeating tasks were already active. `TaskScope` would not own the existing `activeTasks`, and the adapter would simply be a pass-through around the God Object.

### Option B — distribute existing operations across coordinator phases

The adapter could distribute construction, registration and task startup across `initialize()`, `register()` and `start()`.

That would make the lifecycle labels look correct, but the current startup sequence interleaves those kinds of operations. Moving them into global phases risks changing registration/start timing and dependency order. That violates the compatibility requirement for this migration.

## 4. Rollback and shutdown ownership risks

The current coordinator has a specific cleanup contract:

- `stop()` is invoked only when a module's `start()` was attempted.
- `close()` is invoked for every module whose initialization was attempted, including partial initialization failure.
- The module's `TaskScope` is closed separately from `module.close()`.

A legacy wrapper would consequently need to make its close path clean up partially initialized legacy state, while avoiding a second cleanup after a normal `stop()`. That requires an additional idempotent state machine around the existing lifecycle.

There is another mismatch: `shutdownRuntime()` is a sequential cleanup routine. If an individual cleanup call throws, later cleanup calls are not currently guaranteed to run. Merely invoking it through a coordinator does not make the internal legacy cleanup failure-isolated. Nor can the wrapper hand shared-writer shutdown to a module without violating the root-owned, close-last rule.

These are reasons to extract real ownership seams before coordinator activation, not reasons to add a wrapper.

## 5. Decision

Do **not** create `LegacyRuntimeModule`.

Keep the current `onEnable()` and `onDisable()` orchestration in `RaskolClasses` while extracting one real lifecycle owner at a time. Do not register every concrete service in `ServiceRegistry`, and do not move the existing `activeTasks` list wholesale into an artificial module.

The adapter can be reconsidered only after the root no longer owns the lifecycle responsibilities it would merely forward.

## 6. Recommended next slice — A-03-003.7

Start with a narrow owner whose lifecycle is already distinct from gameplay services: **runtime integrity monitoring**.

Proposed extraction boundaries:

- Keep artifact-bound startup verification and the startup-denial policy in `JavaPlugin.onEnable()`, before gameplay initialization.
- Extract runtime monitor construction, start and close into a dedicated owner with explicit constructor dependencies.
- Keep the Paper main-thread disable callback explicit; do not give the new owner unrestricted access to the plugin facade.
- Preserve the 30-minute interval, daemon executor, reporter, state manager and current enforcement behavior.
- Ensure the owner is started only after successful gameplay startup and stopped before other plugin-owned infrastructure is released.
- Make close idempotent and cover start/partial-start/close behavior with focused tests.
- Do not route the whole plugin through `BootstrapCoordinator` yet.

This is a real ownership extraction, not a speculative generic adapter. Its exact class shape and test seam must be checked against the current RIPS types before implementation.

## 7. Acceptance criteria

- [x] Inspect current lifecycle phase boundaries and their side effects.
- [x] Inspect the coordinator's rollback and close behavior.
- [x] Trace existing task ownership and shared storage ownership.
- [x] Decide whether a legacy wrapper transfers meaningful ownership.
- [x] Record the decision and the next bounded extraction.
- [ ] No runtime behavior changed by this assessment.
