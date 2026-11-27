# Stage 4 — Production Combat Modifier Order and Listener Interaction Audit

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

## 1. Status and snapshot

- Audited production snapshot: `a96a1a5cbdc82cc19b9242b568aa49afd433cc49`
- Platform: Paper 26.2 / Java 25
- Status: **source trace recorded; canonical order NOT approved**
- Scope: documentation-only. No production code, formulas, config, balance or talents changed.

Evidence labels: **Source-confirmed** means directly implemented in the snapshot; **Runtime-observed** means exercised by isolated Paper 26.2 Watchtower tests for that snapshot (not a live-server test); **Open** means not established by this audit.

## 2. Executive findings

1. Path A is a Paper event pipeline: LOW listeners → `VanillaDamageListener` at NORMAL → later HIGH/HIGHEST listeners → MONITOR settlement → Paper applies the event.
2. Path B calculates in `CombatService.dealDamage`, reserves burst budget, then issues synchronous Paper damage calls under suppression and `AbilitySourceContext`.
3. **Suppression is not a global listener bypass.** `VanillaDamageListener` and `CCGuard` explicitly check `CombatService.isSuppressed()`. The inspected damage handlers in `ActiveEffectManager`, `Spec2RoleListener`, `PassiveListener` and `PetService` do not.
4. Thus Path B synthetic events can still be modified or observed by other listeners. `DamageAttribution` measures actual health + absorption delta around synchronous calls, and burst settlement reconciles to that amount, but this does not make pre-event formulas or side effects equivalent to Path A.
5. Path A NORMAL is not the last modifier: FIGHTER and class passives run at HIGH; CCGuard may cancel at HIGHEST. MONITOR settlement sees final event damage/cancellation, not actual applied health/absorption.
6. Black Mass lifesteal is currently an event-time side effect: it reads current `event.getDamage()` through PassiveListener at HIGH and submits healing before Paper has applied the damage. It is not proven to be based on actual damage dealt.
7. Watchtower confirms selected event mutation, cancellation, nested settlement, absorption and source-context cases only. It does not establish complete formula parity or canonical order.

## 3. Listener priority map

The map below reflects event annotations in source. Relative order among listeners at the same priority must not be inferred from this table; registration order matters and needs a dedicated check/test. External plugins can add handlers at any priority.

| Handler | Event | Priority | Effect | Checks suppression? |
|---|---|---:|---|---|
| `ActiveEffectManager.onIncomingEarly` | EntityDamageEvent | LOW | Fall/evasion cancellation; SHIELD_WALL ×0.2 | No |
| `ActiveEffectManager.onOutgoingEarly` | EntityDamageByEntityEvent | LOW | STEALTH ×1.5; AIMED_SHOT ×2 for arrows | No |
| `VanillaDamageListener.onDamage` | EntityDamageEvent | NORMAL | Path A calculations | Yes |
| `ActiveEffectManager.onIncomingLate` | EntityDamageEvent | HIGH | Break target stealth on non-cancelled hit | No |
| `ActiveEffectManager.onOutgoingLate` | EntityDamageByEntityEvent | HIGH | Consume stealth/AIMED_SHOT, slow; BLOOD_FURY proc damage | No |
| `Spec2RoleListener.onDamageDealt` | EntityDamageByEntityEvent | HIGH | FIGHTER outgoing multiplier | No |
| `PassiveListener.onDamageByEntity` | EntityDamageByEntityEvent | HIGH | Class outgoing/incoming passives; Black Mass | No |
| `PetService.onDamageByEntity` | EntityDamageByEntityEvent | HIGH | Pet ownership/friendly-fire and retargeting | No |
| `CCGuard.onDamageByEntity` | EntityDamageByEntityEvent | HIGHEST | STUN/FEAR, BLIND, DISARM attack restrictions | Yes |
| `VanillaDamageListener.onDamageMonitor` | EntityDamageEvent | MONITOR | Path A burst settlement/rollback | No |

Handlers with `ignoreCancelled = true` are skipped if an earlier listener has cancelled the event. LOW incoming effects apply to Player targets across damage causes, not only player attacks. Same-priority listener ordering and external plugin listeners remain environment-dependent.

## 4. Path A — native Paper event pipeline

### 4.1 LOW phase

For Player targets, `ActiveEffectManager.onIncomingEarly`:
1. Cancels FALL when `NO_FALL_DAMAGE` is active.
2. Cancels non-VOID damage when `EVASION` is active.
3. Applies SHIELD_WALL ×0.2 if active.

For entity damage-by-entity events, `onOutgoingEarly` resolves a Player directly or through a projectile shooter:
1. STEALTH multiplies current event damage by 1.5.
2. AIMED_SHOT multiplies damage by 2.0 when damager is an `AbstractArrow`.

These modifiers precede the NORMAL handler, so it sees the already-mutated event amount.

### 4.2 NORMAL — VanillaDamageListener

Source-confirmed sequence inside `onDamage`:

1. Read suppression state; resolve available target/source CombatProfiles and Player attacker.
2. If not suppressed, perform Player-vs-Player ally/friendly-fire gate; may cancel and return.
3. If not suppressed, run `applyOutgoingOffense`:
   - resolve Player attacker or source CombatProfile fallback;
   - choose effective damage type;
   - add weapon power or spell power coefficient and optional STR/INT contribution;
   - choose item attack damage or projectile PDC `shotDamage` override;
   - apply WeaponMastery for physical damage;
   - apply stealth proc multiplier;
   - apply amplifier and consume it when applicable;
   - on crit, apply physical/spell crit multiplier, feedback and crit-proc work; physical crit sets undodgeable.
4. Apply Warlock Seal Amplify to current event damage.
5. Resolve incoming type and cast/effective school.
6. For living targets, apply immunity/school multiplier and target CombatProfile immunity/vulnerability; may cancel and return.
7. For non-Player targets with CombatProfile, apply channel/school resistance and available source penetration; eligible reflect handling follows.
8. For Player targets, continue Player-specific eligibility, true-damage, avoidance and defensive-mitigation branches in the method.
9. Apply target-side modifiers, single-hit cap and burst reservation when their conditions apply.
10. If reservation exists, bind a rollback/settlement handle to this exact event identity.

This is only the NORMAL handler's work, not the entire Path A pipeline. Later HIGH handlers can still modify event damage.

### 4.3 HIGH phase

Applicable handlers include:

- `ActiveEffectManager.onIncomingLate`: breaks target stealth after a non-cancelled incoming event.
- `ActiveEffectManager.onOutgoingLate`: breaks attacker stealth; consumes AIMED_SHOT and applies slow for eligible arrows; may issue BLOOD_FURY return damage through `CombatService.dealDamage(..., fromProc=true)`.
- `Spec2RoleListener.onDamageDealt`: multiplies `event.getDamage()` for FIGHTER.
- `PassiveListener.onDamageByEntity`: dispatches class outgoing passive for a resolved Player attacker and incoming passive for a Player victim.
- `PetService.onDamageByEntity`: pet ownership/friendly-fire cancellation and retargeting; not a generic pet-owner damage attribution bridge.

These listeners are not globally suppression-aware. Their relative ordering at HIGH must be established from registration and interaction tests.

### 4.4 HIGHEST / MONITOR / application

- `CCGuard.onDamageByEntity` first returns if suppressed. Otherwise direct Player attacks against living targets may be cancelled for STUN/FEAR, BLIND miss chance or DISARM.
- `VanillaDamageListener.onDamageMonitor` reconciles the Path A reservation against final event cancellation/damage. Cancellation rolls back the event's reservation; final damage above reservation is recorded as overage.
- MONITOR settlement is not a measurement of actual health + absorption. Paper applies the final non-cancelled event after event dispatch.

### 4.5 Path A sequence

```text
Paper EntityDamageEvent
  → LOW ActiveEffectManager incoming/outgoing early modifiers
  → NORMAL VanillaDamageListener
      → ally/friendly-fire gate
      → outgoing base/item/stat/WeaponMastery/stealth/amplifier/crit
      → Seal Amplify
      → type/school/immunity/vulnerability
      → target resistance/penetration/avoidance/defense
      → target-side modifiers / cap / burst reservation
  → HIGH ActiveEffectManager late effects
  → HIGH Spec2RoleListener FIGHTER
  → HIGH PassiveListener class passives (incl. Black Mass)
  → HIGH PetService ownership/friendly-fire logic
  → HIGHEST CCGuard
  → MONITOR burst settlement
  → Paper applies final event
```

The relative order of HIGH handlers and third-party listeners is not asserted here.

## 5. Path B — CombatService.dealDamage

### 5.1 Pre-application calculation

Source-confirmed broad sequence in `CombatService.dealDamage`:

1. Validate profile/target/dead state and `canHit`; sanitize profile; reject empty damage and Player CREATIVE/SPECTATOR targets.
2. Resolve cast school, school multiplier and school immunity when enabled.
3. Split physical, magic and true components; apply Player-source Spec2 physical/magic outgoing bonuses.
4. Apply outgoing proc modifiers: amplifier (consumed), Expose read from target, Enrage to physical, stealth to physical/magic, armor-penetration roll.
5. Apply separate physical shield-block check for Player targets holding a shield. A successful block zeros physical component.
6. Roll/apply physical and magic crits separately.
7. Resolve Warlock low-health magic-resistance bypass condition.
8. Apply Player resistance/penetration/elemental mitigation or target CombatProfile immunity, channel/school resistance and vulnerability for non-Player targets.
9. Apply cast-school multiplier and target Seal Amplify.
10. Apply single-hit cap and burst reservation unless `allowOverCap` applies.
11. Open `AbilitySourceContext`; UUID is populated only for Player sources.
12. Apply physical and magic components through synchronous Paper damage calls. Each call is wrapped in suppression and a `DamageAttribution` scope.
13. Finish attribution using actual health + absorption deltas; settle burst reservation against attributed applied damage.
14. If actual damage is positive, run post-application work: CC break, defender proc dispatch, magic defender dispatch, attacker landed-hit/crit procs, Expose application, double-strike follow-up and Warlock recoil.

Path B does **not** call `AvoidanceService.tryAvoid` in the inspected `dealDamage` method. Its physical shield-block check is separate and is not parity with Path A dodge/parry.

### 5.2 Synthetic Paper event dispatch

The synchronous `target.damage(...)` calls still dispatch Paper events. Suppression prevents the ordinary Path A calculations from treating them as fresh independent hits; it does not cancel event dispatch and is not a global bypass.

Eligible synthetic events may still reach:
- LOW `ActiveEffectManager` incoming/outgoing handlers;
- HIGH `ActiveEffectManager` late handlers;
- HIGH `Spec2RoleListener` FIGHTER multiplier;
- HIGH `PassiveListener` class passives;
- HIGH `PetService` handler when damager/target conditions match;
- external plugin handlers at any priority.

`CCGuard` sees suppression and returns. `VanillaDamageListener` sees suppression and skips its ordinary Path A calculation. The MONITOR handler is a separate handler and must be evaluated according to whether a matching Path A reservation handle exists.

Because Path B measures actual health + absorption around the synchronous call, event mutations/cancellation that affect application are reflected in attribution. However, the Path B formula and reservation were computed before those Paper listeners ran. Settlement reconciles the difference; it does not move the earlier cap or formula stages.

### 5.3 Path B sequence

```text
Ability / proc call site
  → CombatService.dealDamage
      → eligibility + profile sanitization
      → school + Spec2 outgoing components
      → amplifier / Expose / Enrage / stealth / armor penetration
      → physical shield block
      → physical/magic crit
      → Warlock conditional resistance bypass
      → Player resistance or mob CombatProfile mitigation
      → school multiplier + Seal Amplify
      → single-hit cap + burst reservation
      → AbilitySourceContext open (Player UUID only)
      → per-component DamageAttribution scope
      → beginSuppress → target.damage(...)
          → Paper LOW listeners (not globally bypassed)
          → VanillaDamageListener NORMAL (suppression-aware)
          → Paper HIGH listeners (several not suppression-aware)
          → CCGuard HIGHEST (suppression-aware)
          → MONITOR listeners
      → endSuppress / attribution finish
      → burst settlement from actual health + absorption delta
      → post-application proc / CC / follow-up / recoil
  → return actual applied damage
```

## 6. Suppression interaction matrix

| Component | Path B synthetic damage | DoT tick | Qualification |
|---|---|---|---|
| `VanillaDamageListener` | Suppression-aware; skips ordinary Path A calculation | Same | Event still dispatches; MONITOR is a separate phase |
| `CCGuard` | Explicitly returns when suppressed | Explicitly returns | Prevents CC from retroactively cancelling synthetic/active DoT hits |
| `ActiveEffectManager` | Not suppression-aware | Not suppression-aware | LOW modifiers/cancellations and HIGH state/side effects may run |
| `Spec2RoleListener` | Not suppression-aware | Not suppression-aware | FIGHTER may mutate event damage at HIGH |
| `PassiveListener` | Not suppression-aware | Not suppression-aware | May dispatch class passives when Player attacker resolution succeeds |
| `PetService` | Not suppression-aware | Not suppression-aware | Ownership/friendly-fire logic, not generic owner attribution |
| External plugins | Unknown | Unknown | Requires actual production plugin-set characterization |

Consequences:

- “Suppressed” does not mean “no combat listener runs.”
- Path B with Player source can still be observed as a Player-caused event by HIGH handlers.
- AIMED_SHOT's arrow branch requires an `AbstractArrow`; ordinary `target.damage(amount, sourcePlayer)` is not an arrow event.
- STEALTH exists in both explicit Path B proc logic and ActiveEffectManager event hooks. Whether the same state is shared/consumed before the event must be verified per call path; do not assume universal double application.
- FIGHTER can multiply synthetic event damage after Path B's single-hit/burst calculation. Attribution and settlement can observe application, but pre-event cap was earlier.
- Class passives can run on synthetic events and may create nested damage/healing.
- Same-priority state consumption can make outcome registration-order-dependent; add characterization tests.

## 7. DoT, projectile, pet, proc, nested and reflection routes

### 7.1 DoT

Confirmed in `DotService`:

- Each DoT instance stores owner UUID.
- A tick requires the owner Player to be online; offline-owner ticks are skipped.
- Per-target aggregate DPS cap is applied before damage calls.
- Each tick opens suppression and reflect-suppression scopes, then calls `target.damage` with owner as source.
- Physical channel uses Player-source damage; non-physical channel uses magic DamageSource with owner attribution when available.
- DoT tick does not open `AbilitySourceContext`.

Therefore Path A's ordinary formula and CCGuard attack restrictions are bypassed, but non-suppression-aware listeners can still observe/mutate the event. Black Mass's ability-source equality gate is not established for DoT because the tick does not open the ability-source scope. Do not award Black Mass lifesteal to DoT without a targeted test. Reflect marker suppresses selected Black Mass/recoil consumers; it is not general listener suppression.

### 7.2 Projectiles

Confirmed:
- Path A resolves a direct Player attacker or a projectile with Player shooter.
- Item PDC can override projectile `shotDamage` and `shotType`.
- LOW/HIGH ActiveEffectManager has stealth and arrow-specific AIMED_SHOT behavior.
- Synchronous `AbilitySourceContext` does not automatically survive projectile flight.
- Fan-arrow sets vanilla arrow damage to zero and routes tagged impact damage through Path B; zero-damage arrow event and custom Path B hit are separate.

Open: all projectile families, non-Player shooters, launch-to-impact ability identity, PDC validation and each custom hit route.

### 7.3 Pets

Confirmed:
- PetService stores owner UUID in pet PDC and sets vanilla wolf owner where applicable.
- Its HIGH damage handler uses PDC owner for offline-owner and friendly-fire checks and may cancel.
- `VanillaDamageListener.resolveAttacker` resolves only direct Player or projectile with Player shooter; it does not read pet-owner PDC.
- `CombatService` opens non-null ability-source UUID only for Player source.

Pet owner attribution for damage stats, player procs, Black Mass, talents or shared source context is therefore **not established**. Ownership guard is not an owner-attribution resolver.

### 7.4 Procs and nested damage

Proc placement is split:
- Path A event-time work includes crit-proc rolls in `applyOutgoingOffense` and HIGH-listener side effects; some can invoke Path B recursively.
- Path B has pre-application proc modifiers and post-application dispatch. `fromProc` affects selected follow-up behavior; it is not a universal guarantee that every event listener ignores resulting synthetic events.

Nested calls require independent characterization of outer/nested AbilitySourceContext, suppression depth, reflect marker, per-call DamageAttribution, burst tokens, cancellation and callbacks. Existing Watchtower covers selected nested reservation and Black Mass source-context cases, not every proc combination.

### 7.5 Reflection

Reflection consumers issue Path B true-damage calls under a separate reflect marker. Black Mass and Warlock recoil consult `reflectSuppressed()`. General suppression and reflect suppression are distinct. The reflect marker is a boolean ThreadLocal, not depth-counted: an inner `endReflect()` can clear an outer scope. Nested reflection is an explicit correctness risk and needs a targeted test before refactoring.

## 8. Modifier classification matrix

This classifies current implementation, not approval to unify formulas.

| Rule | Classification | Evidence / caveat |
|---|---|---|
| Hit eligibility / friendly-fire | Shared intent, path-specific enforcement | Path B validates before calculation; Path A ally gate is in NORMAL; exact parity unproven |
| Base item / shot damage | Path A-specific entry calculation | Item stats and projectile PDC in `applyOutgoingOffense`; Path B receives DamageProfile |
| Weapon/spell power; optional STR/INT | Path A-specific in inspected event formula | Not automatically reconstructed by Path B |
| Spec2 physical/magic outgoing bonus | Path B pre-calculation | Path A also has distinct HIGH FIGHTER event multiplier |
| WeaponMastery | Path A-specific | Physical event offense |
| Amplifier | Shared mechanic, different placement | Path A offense; Path B before mitigation/application |
| Stealth | Shared state, multiple path hooks | Path A LOW/event proc; Path B proc; synthetic event listeners may also see state |
| AIMED_SHOT | Path A arrow-event-specific | LOW arrow multiplier; HIGH consume/slow |
| Enrage | Path B-specific in inspected method | Physical component only |
| Expose | Shared mechanic, different phase | Path B reads pre-mitigation; Path A event-side handling; identical timing unproven |
| Armor/channel/school penetration | Shared domain, separate implementations | Different Path A and Path B branches |
| School immunity/vulnerability | Shared domain, separate implementation | Event handler vs explicit resolver |
| Physical dodge/parry | Path A-specific in inspected entry | Path B does not call `AvoidanceService.tryAvoid` |
| Physical shield block | Path B-specific in inspected entry | Separate shield roll; not equivalent to dodge/parry |
| Critical strike | Shared concept, separate call sites | Path A event offense vs Path B separate physical/magic rolls |
| Single-hit cap | Shared policy intent, separate placement | Later event listeners can still mutate damage |
| Burst reservation | Shared ledger, path-specific settlement | Path A event identity + MONITOR; Path B actual attributed delta |
| FIGHTER multiplier | Paper HIGH listener | Can affect Path A after NORMAL and Path B synthetic events |
| Class passives | Paper HIGH listener | Player attacker/victim dispatch; may create nested effects |
| DoT | Separate scheduled damage route | Suppression + reflect marker; no AbilitySourceContext |
| Pet owner attribution | Not established | PDC used for ownership guard, not resolved as attacker |
| Projectile ability attribution | Not established universally | Shooter resolution exists; ability context does not persist through flight |
| Black Mass lifesteal | Pre-application event-time side effect | Uses current event damage at HIGH |
| Black Mass reflection | Nested Path B side effect | Separate reflect marker |
| CC break / landed procs / recoil | Path B post-application | Driven by actual attributed damage |
| Path A MONITOR settlement | Path A lifecycle-specific | Final event state, not measured health delta |

## 9. Black Mass — exact current semantics

`WarlockPassives.onDamageOut` is dispatched by `PassiveListener.onDamageByEntity` at HIGH for a resolved Player attacker and living target.

Current gates:
1. Black Mass enabled.
2. Reflect suppression is false.
3. Attacker UUID equals `CombatService.abilitySourceMark()`, restricting it to synchronous ability-source context rather than ordinary auto-attacks.
4. Lifesteal is calculated from the current `damage` argument (event damage at the PassiveListener call).
5. The amount is sent to `HpBarService.heal`, which enters the CustomHealEvent path.

It does not wait for later same-priority listeners, HIGHEST cancellation, MONITOR final event state, Paper health/absorption application or a post-application `DamageAttribution` result.

Therefore Black Mass is not presently proven to mean “percentage of actual damage dealt.” It is an event-time consumer. A future choice to preserve this behavior or redefine it against actual applied damage is a gameplay decision, not a neutral resolver refactor. The Watchtower nested heal assertion does not prove all Black Mass paths consume post-application damage.

## 10. What is truly post-application?

A behavior is post-application only if triggered from a measured/confirmed applied result, not merely because it runs at a late event priority.

Confirmed Path B post-application work:
- finish `DamageAttribution` for actual health + absorption delta;
- settle burst reservation against that attributed delta;
- return when actual damage is non-positive;
- CC break-on-damage using actual damage;
- defender proc dispatch using actual damage;
- attacker landed-hit / follow-up work after successful application;
- Warlock recoil derived from actual applied damage, with its guards.

Not post-application:
- HIGH event modifiers and class passive dispatch;
- Black Mass lifesteal as currently implemented;
- Path A MONITOR settlement (final event state, not measured applied delta);
- event-time reflection scheduling.

## 11. Open questions before canonical approval

1. **Same-priority order:** establish registration order for ActiveEffectManager, Spec2RoleListener, PassiveListener and PetService; test state-consuming and damage-mutating interactions.
2. **External listener chain:** identify relevant third-party damage modifiers/cancellers on production server and characterize priorities. Repository source cannot prove this.
3. **Path A full Player branch:** preserve line-by-line call-site map for avoidance, armor/mitigation, Expose, cap and reservation, including early returns and source types.
4. **Path B synthetic modifiers:** characterize LOW/HIGH changes and reconcile actual damage with pre-event cap/reservation semantics.
5. **Suppression contract:** decide whether suppression should bypass only core Path A / CCGuard or every combat listener. Current implementation does the former, not the latter.
6. **Black Mass:** decide whether lifesteal remains event-time or becomes actual-damage-based; either choice is gameplay-significant.
7. **DoT:** test online/offline owner, school/event causes, ActiveEffectManager, FIGHTER/passives, death, refresh/stacking and DPS cap.
8. **Projectiles:** inventory every family, launch metadata, ability identity, PDC validation and non-Player shooter.
9. **Pets:** decide/test owner credit for stats, procs, talents, PvP and source context.
10. **Proc recursion:** cover each proc family, `fromProc`, nested source scopes, suppression depth, reflection and burst tokens.
11. **Reflection nesting:** characterize nested begin/end and consumers.
12. **Healing/lifesteal application:** trace CustomHealEvent to actual health change, max-health clamp, cancellation, dead target, overheal/absorption and recursion.
13. **Paper application:** compare event damage with actual health/absorption for relevant entities and DamageSources.
14. **Talents:** all 20 talent nodes remain disabled; no talent behavior is assumed.

## 12. Runtime evidence available

The isolated Paper 26.2 Watchtower run for mainline snapshot `a96a1a5c` passed selected scenarios:
- Path B physical/magic cancellation combinations;
- nested hit with outer physical cancellation;
- event damage increased and reduced by another listener;
- absorption-only application;
- Path A external reduction, external increase/overage, nested success with outer cancellation, and nested plus outer success;
- suppression scope visibility/cleanup;
- Black Mass nested source UUID and tested heal result.

These scenarios do not establish universal listener order, complete production plugin-chain behavior or Path A/Path B parity.

## 13. Stage 4 conclusion and review gate

**No canonical modifier order is approved or proposed as final by this document.**

The source trace establishes separate Path A and Path B sequences, confirms that suppression is selective rather than global, identifies event-time vs post-application effects, and records unresolved order/attribution issues.

Recommended review sequence:
1. Review source accuracy and missing handlers.
2. Decide intended suppression contract.
3. Decide whether Black Mass preserves event-time behavior or becomes actual-damage-based (do not implement either during this review).
4. Approve the shared / Path A / Path B / conditional / post-application classification.
5. Only then freeze golden characterization tests and design Combat Resolver contracts.

No production code/formulas changed. No talents enabled. No PR merged.
