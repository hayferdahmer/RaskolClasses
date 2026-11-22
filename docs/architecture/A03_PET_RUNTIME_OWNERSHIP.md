# A-03-003.8 — Pet Runtime Ownership Seam

## Статус

**Implemented on the working branch.** `PetRuntimeOwner` now owns PetService listener registration, both scheduled tasks, despawn-before-cancel shutdown and partial-start rollback. CI/runtime verification remains pending.

- Repository: `hayferdahmer/RaskolClasses`
- Working branch: `refactor/a03-foundation-lifecycle`
- Target platform: Paper 26.2 / Java 25
- Baseline: `1acce195f8c23d572361f9659581f1a3806ac153`
- Implementation: `PetRuntimeOwner`, `PaperPetRuntimeRegistration`, `PaperPetTaskScheduler`

## 1. Decision

Select `PetService` as the first bounded gameplay runtime owner.

The service is a better first seam than a broad combat or character module because it already has:
- one explicit in-memory state registry (`pets`);
- one listener instance (`PetService implements Listener`);
- two explicit scheduled tasks (pet tick and delayed orphan scan);
- a dedicated finalization operation (`despawnAll()`);
- no durable storage writer ownership;
- no constructor-time listener or scheduler side effects.

This is an ownership seam, not a proposal to redesign pet behavior.

## 2. Current ownership (verified in source)

`RaskolClasses.initializeCoreServices()` currently:
1. constructs `PetService`;
2. calls `registerListeners()`;
3. starts `startTickTask()` and stores its BukkitTask in root `activeTasks`;
4. starts `scheduleOrphanScan()` and stores its BukkitTask in root `activeTasks`.

`RaskolClasses.shutdownRuntime()` currently calls `pets.despawnAll()` before cancelling the general `activeTasks` list.

The service constructor only stores the plugin, constructs its PDC keys and checks static registry attributes. It does not register listeners or schedule tasks.

## 3. Required target owner

Introduce a dedicated `PetRuntimeOwner` (or equivalently named narrow runtime coordinator) that owns the complete runtime lifecycle of one `PetService` instance:

- listener registration for that service;
- pet tick task handle;
- delayed orphan-scan task handle;
- pre-cancellation `despawnAll()`;
- task cancellation and listener unregistration;
- idempotent stop/close behavior.

The owner must not become a generic service locator and must not own unrelated services.

The first implementation may use the existing `TaskScope` and a small BukkitTask-to-TaskHandle adapter. It must not expose BukkitTask handles back to `RaskolClasses` or add pet tasks to the root `activeTasks` collection.

## 4. Lifecycle ordering contract

### Start

1. Construct `PetService` with no runtime side effects.
2. Register its listener exactly once.
3. Schedule the repeating tick task.
4. Schedule the delayed orphan scan.
5. If either scheduling operation fails, cancel any task already scheduled and unregister the listener before propagating the failure.

### Stop

1. Call `PetService.despawnAll()` while Bukkit entities and scheduler are still available.
2. Cancel the pet-owned TaskScope.
3. Unregister the PetService listener exactly once.

The required despawn-before-task-cancellation ordering is mandatory. Do not replace it with a generic coordinator cleanup sequence that cancels TaskScope before `stop()`.

### Close

- Idempotent.
- Safe after partial start.
- Continues listener cleanup if task cancellation fails, and reports aggregated failures.
- Does not close shared `AsyncStorageWriter`.
- Does not alter pet PDC keys, entity metadata, TTL behavior, chunk-unload semantics, or owner-quit behavior.

## 5. Integration constraints

- Preserve the existing position of PetService construction and listener registration in startup ordering.
- Preserve the tick period (20L initial delay, 20L period).
- Preserve orphan scan delay (100L).
- Keep the RIPS startup verification as the first operation in `onEnable()`.
- Do not activate `BootstrapCoordinator` in this slice.
- Keep `RaskolClasses.getPets()` and all existing callers compatible.
- Remove only the two pet task registrations from root `activeTasks` after the owner takes responsibility.
- Do not move unrelated gameplay listeners or tasks.

## 6. Test requirements

Add focused tests for the owner using injected task scheduling and listener registration seams (or equivalent test doubles):

1. Successful start registers listener once and schedules both tasks with unchanged delays.
2. Repeated start does not duplicate listener registration or tasks.
3. Failure while scheduling the second task cancels the first and unregisters the listener.
4. Stop calls `despawnAll()` before task cancellation.
5. Repeated stop/close is safe.
6. Partial initialization/registration failure leaves no task or listener owned by the owner.
7. Cancellation failure does not skip listener unregistration; failures are aggregated.
8. No task handle remains registered after successful close.

The tests must not require a live Paper server. Any Bukkit-specific adapter should remain a thin boundary and be validated by compilation/CI.

## 7. Non-goals

- No changes to pet AI, targeting, combat, buffs, summon rules, TTL, entity type, or PDC.
- No persistence of pet state across restarts.
- No changes to pet commands or ability callers.
- No generic event bus or reflection-based listener discovery.
- No activation of the full module bootstrap.
- No unrelated changes to `RaskolClasses` shutdown ordering.

## 8. Implementation record — A-03-003.9

Added `PetRuntimeOwner` as a Bukkit-independent lifecycle coordinator. It composes `PetRuntimeRegistration`, `PetTaskScheduler`, `TaskScope` and a `despawnAll` callback. The owner is terminal after a successful stop; repeated start while running is idempotent, and start after a previous attempt is rejected.

Paper adapters:
- `PaperPetRuntimeRegistration` registers the PetService listener and unregisters only that listener through `HandlerList.unregisterAll`;
- `PaperPetTaskScheduler` delegates to the existing `PetService.startTickTask()` and `scheduleOrphanScan()` methods and wraps returned `BukkitTask` handles as `TaskHandle`.

`RaskolClasses.initializeCoreServices()` now creates and starts the owner at the same point where PetService was previously registered and scheduled. The two pet tasks are no longer added to root `activeTasks`. Shutdown delegates to the owner before cancelling the remaining root-owned tasks. A pet-owner shutdown failure is logged and does not prevent cancellation of unrelated root tasks or later shutdown steps.

Failure behavior:
- listener registration is marked attempted before invoking Paper, so partial registration failures trigger unregister rollback;
- if orphan-scan scheduling fails, the already-created tick task is cancelled and listener registration is rolled back;
- stop always attempts despawn, task cancellation and listener unregistration even if an earlier operation fails;
- failed cancellation or listener unregistration remains retryable on a later `close()` call;
- close-before-start is side-effect free.

`PetRuntimeOwnerTest` contains eight focused scenarios covering start/idempotence, partial-start rollback, despawn ordering, repeated close, cancellation retry, cleanup continuation after despawn failure, close-before-start and registration rollback. Tests have been authored but not yet executed in Maven/CI.

## 9. Acceptance criteria

- Pet tasks are no longer stored in root `activeTasks`.
- Pet runtime listener/task ownership is represented by one explicit owner.
- Despawn-before-cancel ordering is covered by a deterministic test.
- Partial-start rollback is covered.
- Existing public compatibility access through `getPets()` remains intact.
- `mvn -B verify` and `mvn -Pdev -B verify` pass on Java 25.
- No changes to gameplay semantics or durable data contracts.
