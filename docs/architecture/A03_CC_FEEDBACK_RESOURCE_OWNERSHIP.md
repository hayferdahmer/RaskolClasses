# A-03-003.20 — CCFeedback resource ownership

## Status

Implemented on `refactor/a03-foundation-lifecycle`; CI verification is pending.

## Scope

This slice covers only the visual resources created by `CCFeedback`: the global and optional player-local `TextDisplay` pair and the one-shot delayed cleanup task scheduled for each application.

It does not cover CC domain state, diminishing returns, effect timing, or the recurring one-tick CC producer.

## Current behavior

- A global `TextDisplay` is spawned above every affected target and hidden from a player target.
- For player targets, a second display is spawned in front of the target, hidden by default, then shown only to that player.
- Both displays are attached as passengers.
- `CCFeedbackRuntimeOwner` retains each target's resource bundle and delayed-task handle.
- `PaperCCFeedbackScheduler` owns the Paper one-shot scheduling boundary; delay remains `max(1, ticks)`.
- Explicit clear cancels the task before removing displays.
- The plugin composition root closes the feedback owner during shutdown.

## Confirmed defects

1. The delayed callback unconditionally removes the UUID map entry. An older callback can therefore remove a newer hologram entry after replacement.
2. If creation of the local display fails after the global display succeeds, the global display has not yet been published to the map and can leak.
3. Explicit clear does not cancel the delayed task. The callback remains live after the displays have already been removed.
4. There is no plugin-disable cleanup for retained display entities or delayed callbacks.

## Target ownership

`CCFeedbackRuntimeOwner` owns display bundles and delayed task handles independently of `CCRuntimeOwner`, whose scope remains the CC listener and periodic producer.

The owner uses exact-entry identity checks, cancellation before removal, independent cleanup attempts, and retryable resource cleanup. `CCFeedback` rolls back already-created displays when a later spawn/setup step fails. The scheduler boundary is a narrow Paper adapter; the owner itself is tested without a running server.

`CCFeedback` is now instance-scoped and injected into `CCService`; the static UUID map has been removed.

## Compatibility constraints

No changes to CC mechanics, DR state, duration, task deadline, hologram text, colors, visibility, passenger attachment, sounds, particles, messages, configuration keys, or CCService API semantics are intended.

## Implemented checks

- replacement cancels the old task and removes the old resources before scheduling the replacement;
- stale delayed callback cannot remove a replacement;
- scheduling failure rolls back resources;
- explicit clear cancels the one-shot task before removing resources;
- close attempts cleanup for every tracked target;
- failed resource removal remains tracked for retry;
- delay is clamped to at least one tick;
- closed owner rejects new resources.

The Paper display-spawn rollback is covered by guarded production code; a dedicated Paper integration test remains a verification follow-up because the owner tests intentionally avoid Bukkit runtime dependencies.
