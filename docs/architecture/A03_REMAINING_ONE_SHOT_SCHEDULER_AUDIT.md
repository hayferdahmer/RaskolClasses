# A-03-003.39 / A-03-003.40 — Remaining one-shot scheduler and async handoff

## Status

The A-03-003.39 source audit and A-03-003.40 implementation are complete on the lifecycle branch. The implementation migrates the three synchronous next-tick callback paths described below. A-04 asynchronous completion callbacks remain explicitly out of scope.

Audited against lifecycle branch source at the A-03-003.38 implementation checkpoint. The findings below are bounded observations from the named call sites, not a claim that every scheduler call in the repository has been exhaustively classified.

## Findings

### P2 — GUI next-tick callbacks retain the event Player

The following methods schedule a next-tick callback and close over the Player instance supplied by the caller:

- `src/main/java/dev/raskol/classes/item/craft/CraftingGui.java:97` — `scheduleOpen(Player, int)`.
- `src/main/java/dev/raskol/classes/item/gui/ItemBrowserMenu.java:151` — `scheduleOpen(Player, ...)`.

Both callbacks previously retained the caller's Player. A-03-003.40 now gives each GUI its own `PlayerCallbackRuntimeOwner`, schedules with a one-tick delay, stores only UUID and the exact page/filter arguments, resolves the current online Player through `Bukkit.getPlayer(UUID)`, and cancels on `PlayerQuitEvent`. The two GUI owners are independent and cannot replace each other's same-UUID callbacks.

### P2 — ItemHook next-tick reconciliation retains Player

- `src/main/java/dev/raskol/classes/item/listener/ItemHook.java:99` — `schedule(Player)` schedules next-tick reconciliation and captures the supplied Player.
- The repeating reconciliation task is separately retained in `reconcileTask` and cancelled by `stop()` (`start()` around line 87).

The one-shot callback was bounded to one tick but had no explicit handle, UUID re-resolution, or quit cancellation. A-03-003.40 now routes it through a dedicated `PlayerCallbackRuntimeOwner`, resolves the current online Player by UUID, cancels on quit, and closes the owner from `ItemHook.stop()`. The existing repeating reconciliation task remains separately owned and retains its cadence.

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

## A-03-003.40 implementation record

- `CraftingGui`, `ItemBrowserMenu` and `ItemHook` now each own a separate `PlayerCallbackRuntimeOwner` backed by `PaperPlayerCallbackScheduler`.
- Each migrated callback preserves its one-tick delay and original GUI/reconciliation arguments; callbacks retain UUID rather than Player and resolve the current online Player at execution.
- Both GUI listeners cancel their pending callback on quit and expose `close()` for shutdown.
- `ItemHook.quit()` cancels its pending reconciliation callback before clearing player state; `ItemHook.stop()` cancels the repeating task and closes its one-shot owner.
- `ItemSystem.stop()` closes all three independent owners through failure-isolated shutdown steps.
- Added a focused owner test proving same-UUID callbacks in independent owners do not replace each other. Existing tests cover quit cancellation, stale callback rejection, completion release, close and retry after cancellation failure.
- `TrailListener` remains unchanged as a bounded self-cancelling exception.
- A-04 asynchronous purchase/salvage/spec notification callbacks remain out of scope.

## Explicit limitations

- This audit does not claim an exhaustive repository-wide classification of every scheduler API, executor, listener registration or delayed callback.
- No gameplay behavior was intentionally changed.
- No live Paper 26.2 runtime test was performed in this audit-only slice.
- `main` remains untouched; `BootstrapCoordinator` remains inactive.
