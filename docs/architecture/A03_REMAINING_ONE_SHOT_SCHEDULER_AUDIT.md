# A-03-003.39 — Remaining one-shot scheduler and async handoff audit

## Status

Audit-only. No production source or tests were changed in this slice.

Audited against lifecycle branch source at the A-03-003.38 implementation checkpoint. The findings below are bounded observations from the named call sites, not a claim that every scheduler call in the repository has been exhaustively classified.

## Findings

### P2 — GUI next-tick callbacks retain the event Player

The following methods schedule a next-tick callback and close over the Player instance supplied by the caller:

- `src/main/java/dev/raskol/classes/item/craft/CraftingGui.java:97` — `scheduleOpen(Player, int)`.
- `src/main/java/dev/raskol/classes/item/gui/ItemBrowserMenu.java:151` — `scheduleOpen(Player, ...)`.

Both callbacks check `player.isOnline()` before opening the GUI. This avoids acting on a disconnected player, but it still retains the old Player reference for the scheduled tick and does not resolve a reconnecting session by UUID. The delay is one tick, so this is a bounded stale-reference/lifecycle consistency issue, not an unbounded task leak.

**Recommended treatment:** migrate these callbacks together to a dedicated GUI callback owner or a shared owner with a distinct purpose-specific instance. Store UUID, resolve the current online Player at execution, and cancel by UUID on quit. Preserve next-tick timing and the exact GUI arguments. Do not reuse an owner whose per-player key is shared with HP/Foliant callbacks: independent workflows must not replace each other's callbacks.

### P2 — ItemHook next-tick reconciliation retains Player

- `src/main/java/dev/raskol/classes/item/listener/ItemHook.java:99` — `schedule(Player)` schedules next-tick reconciliation and captures the supplied Player.
- The repeating reconciliation task is separately retained in `reconcileTask` and cancelled by `stop()` (`start()` around line 87).

The one-shot callback is bounded to one tick but has no explicit handle, UUID re-resolution, or quit cancellation. It should be considered with the GUI callbacks only if the owner abstraction remains narrow and the call sites can preserve their independent semantics.

**Recommended treatment:** UUID-based re-resolution for the one-shot path; retain the existing repeating task lifecycle unchanged. Add focused tests for disconnect/reconnect, callback completion and shutdown if this path is migrated.

### P2 — TrailListener uses bounded self-cancelling projectile tasks

- `src/main/java/dev/raskol/classes/fx/TrailListener.java:64-83` creates a repeating task for each eligible projectile.
- The callback terminates for dead/invalid projectiles, invalid worlds, and after `MAX_TRAIL_TICKS` (200 ticks).

The task handle is not retained by a listener-level owner. Its bounded lifetime and self-cancellation make this different from an unbounded repeating task. Paper also cancels plugin-owned tasks on plugin disable. No production defect is established by this observation.

**Recommended treatment:** retain as a documented bounded exception unless runtime evidence or the wider FX task inventory identifies a concrete shutdown/diagnostic requirement. Do not add a generic task registry solely to collect these handles.

### A-04 boundary — asynchronous completion callbacks

These call sites cross asynchronous storage/economy completion boundaries and should not be silently folded into the synchronous one-shot lifecycle refactor:

- `src/main/java/dev/raskol/classes/gui/PlusGui.java:249-251` — purchase completion is handed back to the Paper main thread and closes over Player.
- `src/main/java/dev/raskol/classes/item/craft/SalvageService.java:113-115` — daily-limit storage completion is handed back to the main thread and closes over Player.
- `src/main/java/dev/raskol/classes/spec/build/PlusSubscription.java:213` — notification is scheduled onto the main thread from an asynchronous completion path.

These need a dedicated A-04 completion/acknowledgement audit: define whether a completion is player-session-bound, whether reconnect should receive the result, how shutdown/rejected scheduling is reported, and whether the callback's operation must complete even if the player goes offline. A UUID substitution alone is not enough to decide those business semantics.

### Already handled / separate ownership

- HP Attribute and HP Bar respawn callbacks were migrated in A-03-003.38 to UUID-aware `PlayerCallbackRuntimeOwner` callbacks with online player re-resolution.
- Foliant transition callback already uses its own UUID-aware owner and quit cancellation.
- Cooldown notification callbacks release completed handles.
- RIPS runtime-integrity enforcement remains intentionally plugin-scoped and must not be cancelled by generic player callback shutdown.

## Recommended grouped implementation

Next bounded implementation package:

1. Introduce a purpose-specific owner for GUI and ItemHook next-tick callbacks, or prove that a single owner instance can serve these independent clients without key collisions.
2. Migrate CraftingGui, ItemBrowserMenu and ItemHook together, preserving each callback's arguments and one-tick scheduling semantics.
3. Add quit cancellation and online UUID re-resolution.
4. Add focused tests for replacement isolation, disconnect/reconnect, completion release and owner shutdown.
5. Keep asynchronous purchase/salvage/spec notification completion in A-04.
6. Keep TrailListener unchanged unless further evidence justifies stronger ownership.

## Explicit limitations

- This audit does not claim an exhaustive repository-wide classification of every scheduler API, executor, listener registration or delayed callback.
- No gameplay behavior was intentionally changed.
- No live Paper 26.2 runtime test was performed in this audit-only slice.
- `main` remains untouched; `BootstrapCoordinator` remains inactive.
