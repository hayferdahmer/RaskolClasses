# A-03-003.42 — Player-session callback retention audit

## Scope

The focused audit reviewed two finite delayed callbacks that retained a Bukkit `Player` object beyond the event/ability invocation that scheduled them:

- `WarriorAbilities.taunt`: delayed removal of mob glow after the configured taunt duration.
- `AvoidanceService.stagger`: delayed removal of the parry attack-speed modifier from a player attacker.

The work is intentionally limited to those two callback paths. A-04 async storage/completion handoffs are not part of this slice.

## Findings and resolution

### P2 — Warrior taunt expiry retained the casting Player

The previous callback closed over the `Player` and later called `getNearbyEntities` on that original session object. It now captures only the caster UUID and resolves the currently online Player from the server when the callback runs. If the caster is offline at expiry, the callback is a no-op.

The callback remains one independent scheduled one-shot per successful taunt. This preserves overlapping taunt expiry behavior: one taunt cannot replace another taunt's pending cleanup. The configured duration, radius, and existing glow cleanup selection remain unchanged.

### P2 — Avoidance stagger expiry retained the attacking Player

The previous callback closed over the attacking Player and later re-read its attack-speed attribute. It now uses the existing `PlayerCallbackRuntimeOwner` and captures only the attacker UUID. At expiry it resolves the currently online Player and removes the modifier from the current session's attack-speed attribute.

The owner is cancelled for that UUID during `PlayerQuitEvent` and closed as an independent shutdown step. Existing immediate modifier removal on quit and the join-time stale-data safeguard remain intact. Repeated parries for the same attacker replace the pending expiry callback, ensuring an older expiry cannot remove a newer stagger early.

## Validation support

- Added a focused `PlayerCallbackRuntimeOwnerTest` proving that a UUID callback resolves the current session value at execution time, rather than retaining the original session value.
- Existing owner tests cover quit cancellation, stale callback rejection, replacement semantics, handle release, cancellation failure/retry, and shutdown.
- Build and formatting workflows are required on the final formatted HEAD.
- Live Paper 26.2 testing remains necessary for actual disconnect/reconnect behavior and modifier cleanup timing.

## Non-goals

- No damage, ability cost, target selection, taunt radius, stagger magnitude, or mob stagger behavior changes.
- No generic scheduler registry for bounded one-shot callbacks.
- No changes to A-04 storage completion callbacks.
- No merge to `main`.
