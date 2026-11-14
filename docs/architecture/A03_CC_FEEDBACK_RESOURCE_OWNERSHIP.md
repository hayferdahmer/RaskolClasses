# A-03-003.20 — CCFeedback resource ownership

## Status

Source assessment completed. Implementation is the active next step on `refactor/a03-foundation-lifecycle`.

## Scope

This slice covers only the visual resources created by `CCFeedback`: the global and optional player-local `TextDisplay` pair and the one-shot delayed cleanup task scheduled for each application.

It does not cover CC domain state, diminishing returns, effect timing, or the recurring one-tick CC producer.

## Current behavior

- A global `TextDisplay` is spawned above every affected target and hidden from a player target.
- For player targets, a second display is spawned in front of the target, hidden by default, then shown only to that player.
- Both displays are attached as passengers.
- A static UUID-keyed map retains the display pair.
- A one-shot Bukkit task is scheduled for `max(1, ticks)`, but its handle is discarded.
- Normal CC clear/expiry paths call `clearHologram`; plugin shutdown has no dedicated hologram cleanup.

## Confirmed defects

1. The delayed callback unconditionally removes the UUID map entry. An older callback can therefore remove a newer hologram entry after replacement.
2. If creation of the local display fails after the global display succeeds, the global display has not yet been published to the map and can leak.
3. Explicit clear does not cancel the delayed task. The callback remains live after the displays have already been removed.
4. There is no plugin-disable cleanup for retained display entities or delayed callbacks.

## Target ownership

A dedicated `CCFeedbackRuntimeOwner` will own display bundles and delayed task handles. It will be independent of `CCRuntimeOwner`, whose scope remains the CC listener and periodic producer.

The resource owner must support exact-entry identity checks, transactional publication, cancellation before removal, independent cleanup attempts, and retryable failures. Bukkit entity and scheduler operations will be kept behind narrow Paper adapters so the owner can be tested without a running server.

## Compatibility constraints

No changes to CC mechanics, DR state, duration, task deadline, hologram text, colors, visibility, passenger attachment, sounds, particles, messages, configuration keys, or CCService API semantics are intended.

## Acceptance tests

- replacement retires only the old bundle;
- stale delayed callback cannot remove a replacement;
- partial display creation rolls back every created display;
- scheduling failure rolls back displays;
- explicit clear cancels the one-shot task;
- close cancels all tasks and removes all displays;
- cleanup continues after individual failures and can be retried;
- repeated close and close-before-use are safe.
