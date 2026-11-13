# A-03-003.7 — Runtime Integrity Owner Verification

## Status

**Implemented as an isolated lifecycle owner; local/Maven and GitHub Actions verification remain pending.**

- Repository: `hayferdahmer/RaskolClasses`
- Branch: `refactor/a03-foundation-lifecycle`
- Baseline: `1acce195f8c23d572361f9659581f1a3806ac153`
- Target platform: Paper 26.2 / Java 25

## 1. Ownership boundary

`RuntimeIntegrityRuntimeOwner` owns:

- construction and lifecycle of `RuntimeIntegrityMonitor`;
- its dedicated single-thread scheduled executor;
- the 30-minute periodic interval;
- `RuntimeIntegrityStateManager`;
- `RuntimeSecurityReporter` and the existing `SecurityReporter`;
- monitor start and close.

The owner does not depend on Bukkit/Paper APIs and does not hold a `JavaPlugin` reference. The failure callback is injected as a `Consumer<RuntimeIntegritySnapshot>`; the Paper-specific enforcement callback remains in `RaskolClasses`.

## 2. Preserved composition-root responsibilities

- Artifact-bound startup verification remains the first operation in `onEnable()`.
- Startup denial still disables the plugin and returns before configuration/gameplay initialization.
- Runtime monitoring starts in `finishStartup()`, after gameplay initialization, listener/integration registration and runtime task startup.
- Paper main-thread dispatch and disable enforcement remain in `handleRuntimeIntegrityFailure()`.
- The owner is closed near the beginning of `shutdownRuntime()`, before HP sweep, gameplay services and shared infrastructure are released.
- The 30-minute interval, daemon thread name (`RaskolClasses-RIPS-Monitor`), reporter, state manager and failure policy are preserved.

## 3. Failure and concurrency behavior

The owner delegates scheduling state to the thread-safe `RuntimeIntegrityMonitor`.

- Concurrent/repeated starts schedule only one periodic task.
- Close-before-start is terminal; a later start is rejected.
- Repeated close is safe.
- If scheduling fails during start, the owner closes the monitor and its executor before propagating the startup exception.
- Cleanup exceptions are attached as suppressed exceptions to the original startup failure.
- Null constructor dependencies are validated before the default executor is allocated.

The monitor's own close operation transitions its state manager to STOPPED, completes pending checks exceptionally, clears pending-check tracking and invokes `shutdownNow()` on the owned executor.

## 4. Focused tests

`RuntimeIntegrityRuntimeOwnerTest` covers:

1. owner closes its executor;
2. repeated close;
3. start after close rejection;
4. configured 30-minute interval;
5. concurrent starts schedule one periodic task;
6. rejected scheduling closes the executor and makes the owner terminal.

These tests are committed but have **not yet been executed** in a Maven test run in this work session.

## 5. Explicit non-goals

- Do not move startup verification into the runtime owner.
- Do not give the owner direct access to `JavaPlugin`, Bukkit scheduler or plugin manager.
- Do not register the owner as a `PluginModule` or activate `BootstrapCoordinator` in this slice.
- Do not alter integrity policy, failure thresholds, reporting semantics or gameplay startup/shutdown order.

## 6. Acceptance status

- [x] Runtime monitor/executor ownership extracted from `RaskolClasses`.
- [x] Startup verification remains outside gameplay bootstrap.
- [x] Runtime failure callback remains explicit and Paper-specific.
- [x] 30-minute interval and daemon executor preserved.
- [x] Close-before-start and repeated close covered.
- [x] Concurrent start scheduling covered.
- [x] Failed scheduling releases the executor.
- [ ] Run focused RIPS runtime tests.
- [ ] Run `mvn -B verify` and `mvn -Pdev -B verify` on Java 25.
- [ ] Verify GitHub Actions results before marking the slice fully verified.
