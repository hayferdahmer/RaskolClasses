# Production Combat Core — Behavioral Inventory (mainline snapshot)

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

## 1. Snapshot and purpose

This inventory describes the production combat behavior visible at main commit `a96a1a5cbdc82cc19b9242b568aa49afd433cc49` (Paper 26.2, Java 25). It supersedes neither the architecture decision record nor earlier historical inventories; it is a fresh mainline snapshot intended to prevent implementation from relying on stale assumptions.

This is a documentation-only baseline. It does not claim full Path A/Path B parity, establish a balance baseline, approve formula changes, or authorize enabling talents.

## 2. Scope and evidence classes

The inventory separates:
- **Observed in source** — behavior directly implemented by production code at the snapshot.
- **Runtime verified** — behavior exercised by the isolated Paper 26.2 Watchtower run on the same main commit.
- **Unit verified** — behavior covered by unit/regression tests.
- **Not established** — behavior not proven by the evidence listed here and requiring a later targeted audit or test.

Runtime evidence is recorded in `docs/testing/PRODUCTION_COMBAT_RUNTIME_QA.md` and the Watchtower run for `a96a1a5c`. That isolated QA run is not a test on the live RASKOL production server.

## 3. Entry points and two production damage paths

### Path A — native Paper / vanilla event path

Observed flow:
1. A native or plugin-generated Paper `EntityDamageEvent` enters `VanillaDamageListener.onDamage` at NORMAL priority.
2. The listener reads the current suppression scope, resolves target/source CombatProfile where available, and performs its configured source, mitigation, resistance, avoidance, cap, and burst-reservation work as applicable to the event/source.
3. For player-target events that receive a burst reservation, the listener associates a rollback/settlement handle with the identity of that exact Paper event.
4. At MONITOR, the listener reconciles the reservation against the final event damage and scale. Cancellation rolls back the event's own reservation; a final amount above the reservation is recorded as overage.
5. The event continues through the remaining Bukkit/Paper listener lifecycle; actual application remains owned by Paper.

Key implementation evidence: `VanillaDamageListener`, `DamageCaps`, `BurstRollbackTracker`, `BurstReservationLedger`.

### Path B — custom ability / CombatService path

Observed flow:
1. Class/tree ability handlers call the public `CombatService.dealDamage(...)` overloads.
2. CombatService validates target/source and hit eligibility, resolves the damage profile and applicable physical/magic components, and calculates the custom path's mitigation, school, penetration, immunity, avoidance, critical and cap behavior.
3. The path reserves burst budget before applying damage.
4. Applied physical and magic components are issued through synchronous Paper entity damage calls. The calls are wrapped in a stack-safe ability-source context and suppression scopes so the synthetic Paper events do not re-enter the ordinary production event calculations as fresh independent hits.
5. `DamageAttribution` records defensive-pool deltas per synchronous nested call, including health and absorption, so nested calls are not double-counted as part of their parent.
6. The burst reservation is settled against the attributed applied amount; unused reservation is released and overage is reconciled.

Key implementation evidence: `CombatService`, `DamageAttribution`, `AbilitySourceContext`, `DamageCaps`, `BurstReservationLedger`.

### Important boundary

Path A and Path B are two different entry contracts. Passing the current runtime matrices proves selected event lifecycle, cancellation, settlement, absorption, nested-call and source-context cases; it does **not** prove that all formula stages, modifier ordering, or side effects are mathematically identical between the paths.

## 4. Context propagation and re-entrancy

### Suppression

Current implementation uses a depth-counted ThreadLocal scope:
- `beginSuppress()` increments the current thread's depth.
- `endSuppress()` decrements and removes the ThreadLocal at the outermost exit.
- `isSuppressed()` is a non-consuming read, allowing multiple listeners to observe the same scope.
- A separate deprecated legacy boolean API remains for compatibility and is not the new depth-based contract.

`CCGuard` checks `CombatService.isSuppressed()` before applying player CC attack restrictions to synthetic damage events. The production runtime probe confirmed that the suppression marker remains visible through the listener chain and is absent on the subsequent ordinary event.

### Ability source attribution

`AbilitySourceContext` is a stack of scopes rather than a single replaceable value. `CombatService` opens a scope for the source UUID around damage application; nested scopes restore the outer source on close. The Watchtower Black Mass probe observed the same Warlock source UUID on both nested damage events and verified lifesteal against the final MONITOR contributions.

### Reflection

CombatService exposes a separate reflect-suppression ThreadLocal marker. Reflection calls are routed through CombatService with the reflect marker set and cleared in a finally block. This is a distinct concern from general synthetic-event suppression and ability-source attribution.

### Current limitations to audit

- ThreadLocal scopes are synchronous/thread-bound contracts; asynchronous handoff must not assume the context follows to another thread.
- The deprecated legacy suppression API remains a compatibility surface and should be inventoried at all remaining call sites before removal.
- Reflection marker nesting semantics are boolean rather than depth-counted; nested reflection behavior requires explicit test coverage before any refactor.

## 5. Burst caps and settlement

The current burst ledger is token-based. Each reservation is independently addressable; settlement and rollback target the exact token, not whichever reservation happened most recently.

The ledger supports:
- limiting a new reservation to the remaining budget in the active window;
- expiry/pruning of old reservations;
- partial rollback;
- settlement to a lower final amount, releasing unused budget;
- settlement to a higher final amount, recording overage;
- rejection of invalid amounts and tokens belonging to another ledger.

The Path A listener binds handles to exact Paper event identity and settles at MONITOR. Path B settles its reservation using the actual attributed defensive-pool delta after synchronous Paper damage application.

### Paper 26.2 runtime evidence on this snapshot

The Watchtower runtime run passed:
- Path B: physical event cancelled / magic allowed;
- Path B: magic event cancelled / physical allowed;
- Path B: both channel events cancelled;
- nested hybrid hit while only outer physical event is cancelled;
- physical event damage increased by another listener;
- physical event damage reduced by another listener;
- fully absorbed hit where health is unchanged but absorption is consumed;
- Path A: external reduction, external increase/overage, nested success with outer cancellation, and nested plus outer success with independent settlement.

These tests establish the stated scenarios only; they are not an exhaustive proof for every event type or plugin listener ordering.

## 6. Control effects and event interaction

`CCGuard` currently enforces:
- STUN/FEAR restrictions on player attacks against living entities;
- BLIND miss chance on player attacks;
- DISARM restrictions for held-item attacks, bow shots and player-thrown projectiles;
- STUN/FEAR interaction restrictions for item/block use;
- STUN restrictions for entity interaction and item consumption;
- STUN/ROOT movement locking while allowing yaw/pitch changes;
- suppressed synthetic damage bypasses the attack-blocking branch so already-running DoT/synthetic effects are not accidentally stopped by caster CC.

SILENCE is documented as a cast-gate concern handled by CastGuard, rather than a general damage-event rule. FEAR uses forced movement and is not implemented as a movement lock.

This section describes the inspected CCGuard behavior; a full audit of every CC producer, expiry scheduler, cleanse, immunity and cast-gate interaction remains a separate task.

## 7. Damage sources and attribution — current map

| Source family | Current known route / evidence | Status requiring further audit |
|---|---|---|
| Native melee / vanilla damage | Paper event → Path A | Per-material/source formula parity and all listener interactions |
| Custom class abilities | Ability handlers → CombatService / Path B | Complete per-ability modifier and side-effect inventory |
| DoT | DotService applies synchronous damage; suppression protects the synthetic event | Tick ordering, owner attribution, logout/death/refresh/stack policies |
| Proc damage | ProcService and CombatService call sites | Recursion guards, proc eligibility, budget ownership, event attribution |
| Reflection | Path B true-damage call with reflect marker | Nested reflection and reflect-loop behavior |
| CombatProfile mobs | Path A source/target profile and Path B target profile handling documented in CombatService history | Full profile field-by-field parity and all mob-source cases |
| Pets | Pet owner attribution exists in the current talent/source-context workstream | Every pet event, owner credit, PvP/friendly-fire and talent consumer |
| Projectiles | Vanilla projectile events and ability-scoped projectile routes exist in the wider combat implementation | Projectile launch-to-impact identity, shooter/owner attribution and all projectile families |
| Traps / installations | Specific handlers and source-context factories exist in adjacent implementation work | General registry, ownership lifecycle and complete damage-budget behavior |
| Environmental / custom plugin damage | Paper event entry where applicable | Classification policy for unknown/plugin sources and profile fallback |

Do not infer an owner, ability ID, weapon hand or source kind when runtime data does not establish it. Unknown attribution must remain explicit.

## 8. Modifier and side-effect order

The exact complete order is **not yet declared canonical** by this inventory. The following stages are known to exist across the production paths and must be traced call-site by call-site before extracting shared domain rules:

- source/target eligibility and PvP/PvE hit gates;
- base damage and class/ability-specific outgoing bonuses;
- physical versus magic channel split;
- school selection, school immunity, vulnerability and mitigation;
- armor/resistance and penetration traits;
- avoidance and critical calculations;
- single-hit and burst-window caps;
- Paper event mutation/cancellation and final event damage;
- actual applied defensive-pool delta;
- post-hit effects, procs, DoT, CC break, lifesteal, recoil and reflection.

The list is an audit checklist, not an assertion that every stage runs in this order on both paths. A later canonical-order decision must explicitly define which stages are shared, path-specific, conditional, or post-application.

## 9. Healing

Healing is not assumed to be the inverse of damage. The current inventory must separately trace:
- requested healing versus actual health restored;
- max-health clamping and dead-target behavior;
- heal event cancellation/modification by other plugins;
- overheal and absorption conversion, if any;
- lifesteal and recoil interactions;
- owner-to-pet healing and group healing;
- healing procs and recursion suppression.

The Watchtower Black Mass probe verifies one nested Warlock lifesteal case against actual health change and final MONITOR damage contributions. It does not establish global healing parity or all heal-event semantics.

## 10. Test evidence at snapshot

### Runtime verified on isolated Paper 26.2 / Java 25

The Watchtower run on `a96a1a5c` passed:
- `SUPPRESS_SCOPE_PASS`
- `PATH_B_SETTLEMENT_MATRIX_PASS`
- `BURST_CANCELLATION_MATRIX_PASS`
- `BLACK_MASS_NESTED_CONTEXT_PASS`
- `PATH_A_SETTLEMENT_MATRIX_PASS`

Build & Test and Quality Gate also passed on the same mainline checkpoint. Runtime artifact and exact run details are recorded in the GitHub Actions run for commit `a96a1a5c`.

### Unit/regression evidence

The current source includes focused tests for depth-based suppression and token-addressed burst reservation/settlement, including invalid values, nested reservations, partial rollback, overage and exception-finally settlement. Full test-suite status is reported by the corresponding Build & Test workflow.

### Not established by the current evidence

- full mathematical Path A/Path B parity;
- a canonical modifier order covering every ability and source family;
- exhaustive projectile, pet, trap, installation and mob attribution;
- global healing semantics;
- PvP/PvE balance baseline;
- complete headless Combat Lab parity with live Paper runtime;
- all talent handlers' runtime correctness.

## 11. Gaps and next verification gates

Before implementation of a shared resolver, close these evidence gaps in order:

1. **Canonical call-site inventory:** trace all damage/heal entry points and enumerate formula stages, listeners, side effects and config keys.
2. **Modifier-order decision:** approve the exact order and explicitly document exceptions by damage channel/source.
3. **Attribution matrix:** cover melee, projectile, pet, trap, DoT, proc, reflection, mob profile, environmental and unknown sources.
4. **Healing matrix:** requested versus actual healing, cancellation, overheal, nested lifesteal/recoil and pet/group healing.
5. **Golden characterization tests:** freeze current approved behavior as executable examples before changing the implementation.
6. **Paper/Headless parity contracts:** define which behaviors are deterministic domain rules and which remain Paper adapter responsibilities.
7. **Only then** begin narrow domain contracts and resolver implementation in separate reviewed PRs.

Any discovered production bug should be isolated into its own fix PR with a regression test; do not silently fold behavior changes into a documentation or architecture PR.

## 12. Non-goals and safety gates

- No production Java or runtime behavior changes in this PR.
- No config, item, formula, cooldown, resource, cap or balance changes.
- No merge, rebase or retarget of existing PRs.
- PRs #54–#58 and existing architecture/inventory PRs remain untouched.
- All 20 talent nodes remain disabled.
- No Combat Lab statistical baseline is approved by this document.
- No claim of production-server runtime testing is made; Watchtower is an isolated CI Paper server.
