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
6. Black Mass lifesteal is currently an event-time side effect on Path B synthetic Paper events: it reads current `event.getDamage()` through PassiveListener at HIGH, before application. `AbilitySourceContext.open()` is only called by `CombatService.dealDamage()` in this snapshot, and WarlockPassives requires the attacker UUID to match that context; ordinary Path A events therefore do not trigger Black Mass.
7. Watchtower confirms selected event mutation, cancellation, nested settlement, absorption and source-context cases only. It does not establish complete formula parity or canonical order.

## 3. Listener priority map

The map below reflects event annotations and, for the plugin's own listener instances, the registration sequence traced through `RaskolClasses.onEnable`, `initializeCoreServices` and `registerGameplayListeners`. For the shared HIGH priority, the relevant listener-instance registration order is: Spec2RoleListener (registered during core initialization) → PetService (registered later in core initialization through PetRuntimeOwner → PaperPetRuntimeRegistration) → ActiveEffectManager (during gameplay listener registration) → PassiveListener (immediately after effects). This establishes listener-instance registration sequence. Paper 26.2 / Java 25 probes confirmed that same-instance generic/specific handlers can be invoked in different orders across independent builds: one observed `specific → generic`, another `generic → specific`, and a third actual invocation order differed from `getDeclaredMethods()` reflection order. ActiveEffectManager's own runtime reflection enumeration was `onIncomingLate → onOutgoingLate` in the two runs where it was captured, but this is not evidence of its actual invocation order. Neither source declaration nor reflection order is a reliable predictor. Do not rely on same-instance annotation order for a canonical modifier dependency; make the order explicit or remove the dependency. External plugins can add handlers at any priority.

| Handler | Event | Priority | Effect | Checks suppression? |
|---|---|---:|---|---|
| `ActiveEffectManager.onIncomingEarly` | EntityDamageEvent | LOW | Fall/evasion cancellation; SHIELD_WALL ×0.2 | No |
| `ActiveEffectManager.onOutgoingEarly` | EntityDamageByEntityEvent | LOW | STEALTH ×1.5; AIMED_SHOT ×2 for arrows | No |
| `VanillaDamageListener.onDamage` | EntityDamageEvent | NORMAL | Path A calculations | Yes |
| `Spec2RoleListener.onDamageDealt` | EntityDamageByEntityEvent | HIGH | FIGHTER outgoing multiplier; registered before PetService | No |
| `PetService.onDamageByEntity` | EntityDamageByEntityEvent | HIGH | Pet ownership/friendly-fire and retargeting; registered after Spec2RoleListener and before ActiveEffectManager | No |
| `ActiveEffectManager.onIncomingLate` / `onOutgoingLate` | EntityDamageEvent / EntityDamageByEntityEvent | HIGH | Break target/attacker stealth; consume AIMED_SHOT, slow; BLOOD_FURY proc damage; registered after PetService | No |
| `PassiveListener.onDamageByEntity` | EntityDamageByEntityEvent | HIGH | Class outgoing/incoming passives; Black Mass; registered after ActiveEffectManager | No |
| `CCGuard.onDamageByEntity` | EntityDamageByEntityEvent | HIGHEST | STUN/FEAR, BLIND, DISARM attack restrictions | Yes |
| `VanillaDamageListener.onDamageMonitor` | EntityDamageEvent | MONITOR | Path A burst settlement/rollback | No |

Handlers with `ignoreCancelled = true` are skipped if an earlier listener has cancelled the event. LOW incoming effects apply to Player targets across damage causes, not only player attacks. The RaskolClasses HIGH order is runtime-characterized for Paper 26.2 / Java 25; third-party listener ordering remains environment-dependent.

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

These listeners are not globally suppression-aware. The listener-instance registration sequence above is source-confirmed and runtime-confirmed for this plugin. Generic/specific probe invocation order varied across independent builds and did not always match reflection order. A direct Paper 26.2 behavior probe now identifies the actual ActiveEffectManager HIGH order in the latest run: specific `onOutgoingLate` executes before generic `onIncomingLate`. This was established by the nested Blood Fury behavior, not by reflection enumeration. External plugin handlers can still interleave according to their own registration and priority.

This pair is not semantically order-independent. `onOutgoingLate` can synchronously trigger a BLOOD_FURY `CombatService.dealDamage` call while the Player victim still has STEALTH. Path B applies its own STEALTH modifier during pre-calculation; the nested synthetic event is suppression-scoped, but `ActiveEffectManager.onOutgoingEarly` at LOW is not suppression-aware and applies a second STEALTH ×1.5. Two executions of the Paper runtime job on the same HEAD measured `onOutgoingLate` before `onIncomingLate`: the nested event saw STEALTH at LOW and its damage increased from `0.444` to `0.666` (×1.5) after the Path B pre-calculation had already run. This confirms a duplicate stealth modifier on the nested Blood Fury proc for the tested Paper 26.2 / Java 25 artifact. Same-HEAD repeatability is confirmed; stability across independent builds remains open. The order must be made deterministic or this dependency removed before canonical approval.

### 4.4 Path A NORMAL branch — Player target and non-Player target

The NORMAL handler has materially different branches after shared offense/school resolution.

**Non-Player target (\`event.getEntity()\` is not Player):**

1. If not suppressed and a target \`CombatProfile\` exists, apply channel resistance minus source penetration plus school resistance minus school penetration; clamp resistance to 0–90%.
2. If not suppressed, profile exists, damage is positive, cause is not CUSTOM and event is \`EntityDamageByEntityEvent\`, invoke reflection.
3. If not suppressed and the target is living with positive damage, run carrier-break hook.
4. Return. This branch does not run Player avoidance, Player-specific cap/burst reservation, Expose application or Player defender procs.

**Player target:**

1. SPECTATOR returns immediately.
2. If resist processing is disabled in the target world, optionally run carrier-break hook and return.
3. TRUE damage optionally applies environmental lethal scaling and carrier-break hook, then returns. It bypasses ordinary avoidance, resistance, Expose and burst reservation.
4. Suppressed events return before avoidance/defense/cap/reflect/procs. (The earlier shared offense and school blocks are also skipped while suppressed.)
5. PHYSICAL damage calls \`AvoidanceService.tryAvoid\`. Dodge/parry cancels; parry with shield dispatches \`onDefenderBlock\`, otherwise \`onDefenderAvoid\`; then returns.
6. Apply channel and elemental mitigation using source-profile or attacker penetration. A non-positive defense multiplier cancels and returns.
7. Apply Expose multiplier when a Player attacker is resolved.
8. Apply single-hit cap, then reserve burst cap. If allowed damage is non-positive, cancel and return; otherwise arm rollback and write scaled event damage.
9. For non-CUSTOM entity-caused damage, invoke reflection.
10. Run carrier-break hook.
11. For positive damage, dispatch \`onDefenderDamaged\`; with a resolved Player attacker, apply Expose and, for MAGIC, dispatch \`onDefenderDamagedByMagic\`.

### 4.5 HIGHEST / MONITOR / application

- \`CCGuard.onDamageByEntity\` first returns if suppressed. Otherwise direct Player attacks against living targets may be cancelled for STUN/FEAR, BLIND miss chance or DISARM.
- \`VanillaDamageListener.onDamageMonitor\` reconciles the Path A reservation against final event cancellation/damage. Cancellation rolls back the event's reservation; final damage above reservation is recorded as overage. This is event settlement, not a health/absorption measurement.
- MONITOR settlement is not a measurement of actual health + absorption. Paper applies the final non-cancelled event after event dispatch.

### 4.6 Path A sequence

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
  → HIGH Spec2RoleListener FIGHTER
  → HIGH PetService ownership/friendly-fire logic
  → HIGH ActiveEffectManager late effects
  → HIGH PassiveListener class passives (incl. Black Mass)
  → HIGHEST CCGuard
  → MONITOR burst settlement
  → Paper applies final event
```

The plugin's source-confirmed and Paper 26.2 runtime-confirmed HIGH listener-instance sequence is Spec2RoleListener → PetService → ActiveEffectManager (two HIGH registrations) → PassiveListener. Two executions of the same Paper 26.2 job on one HEAD measured ActiveEffectManager's specific `onOutgoingLate` before generic `onIncomingLate`; reflection enumeration reported the opposite. Generic/specific probe order also varied across independent builds. Treat the observed ActiveEffect order as repeatable for this artifact, not a cross-build contract; the nested stealth dependency must be removed or explicitly ordered. Third-party listeners are outside this repository trace.

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

`DamageAttribution` excludes nested damage from a parent frame only when the nested operation opens its own child attribution scope (the current nested `CombatService.dealDamage` path). A direct `target.damage(...)` call made by an event listener does not create a child frame; its defensive-pool delta remains included in the parent's measured delta. This is a source-confirmed boundary and needs a dedicated runtime characterization before claiming exact per-origin attribution in the presence of direct nested event damage.

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

### 6.1 Recommended target contract (approved for design; not implemented)

Suppression should **not** become a blanket bypass of every combat listener. It should prevent re-entry into RaskolClasses damage calculation and prevent duplicate RaskolClasses combat modifiers/procs on internally generated damage, while preserving mandatory defensive/ownership validation.

Target behavior:

- Skip Path A's ordinary damage calculation when the event is an internally generated suppressed hit.
- Skip CCGuard's attack restrictions for that same internally generated hit, preserving the existing intent that CC does not retroactively cancel an already-authorized internal application.
- Skip re-applicable damage modifiers, class passives and proc dispatch that would calculate or trigger a second combat effect (including suppression-sensitive ActiveEffectManager, Spec2RoleListener and PassiveListener branches).
- Preserve mandatory pet ownership, friendly-fire and other security/eligibility checks unless a specific check is proven to be an unwanted duplicate of the initiating path. A suppression flag must not silently authorize damage that the ownership/eligibility layer would otherwise reject.
- Do not claim control over third-party listeners. Their event handlers still receive Paper events unless they independently opt into a compatible marker/contract.

This is the **approved design recommendation**, not current behavior. The current implementation is selective: VanillaDamageListener and CCGuard check suppression, while the listed ActiveEffectManager, Spec2RoleListener, PassiveListener and PetService damage handlers do not. Implementing the target contract requires explicit classification of each handler and tests; do not solve it by skipping every listener indiscriminately.

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

- Each DoT instance stores owner UUID; projectile-applied DoT is read from the projectile PDC tag.
- `computeTicks` requires the owner Player to be online and `combat.canHit(owner, target)`. If either check fails at calculation time, the outcome is zero and that instance is removed from the target list (not merely paused). There is a second online check immediately before damage; if the owner disconnects between calculation and application, that tick is skipped.
- Tick damage is calculated per instance from DPS × stacks, school multiplier, immunity, Seal Amplify, target profile multiplier, channel/school mitigation and the per-target aggregate DPS cap (default 6% of formula max HP); the resulting tick is scaled and applied separately for each positive instance.
- Each instance opens general suppression and reflect-suppression scopes, resets target no-damage ticks, then calls `target.damage`.
- PHYSICAL channel uses Player-source damage. MAGIC/TRUE channels use registry `minecraft:magic` DamageSource with the owner as direct/causing entity; if the registry lookup returns null, code falls back to Player-source damage, which would expose a PHYSICAL event path.
- DoT does not open `AbilitySourceContext` or a child `DamageAttribution` scope.

Therefore `VanillaDamageListener` and `CCGuard` skip their suppression-aware branches, but `ActiveEffectManager`, `Spec2RoleListener`, `PassiveListener` and `PetService` are not globally suppression-aware and can still observe the synthetic event. In particular, LOW STEALTH and HIGH FIGHTER/passive behavior can affect DoT ticks even though DotService already computed tick damage. `ActiveEffectManager.onOutgoingLate` also has a BLOOD_FURY proc branch without a suppression/reflect guard; a DoT hit against a Player with BLOOD_FURY can therefore initiate a nested Path B proc while the DoT reflect marker is active. The `BLOOD_FURY_ACTIVE` guard limits recursion but does not suppress the initial proc. Black Mass's ability-source UUID gate does not pass for an ordinary DoT tick because no `AbilitySourceContext` is opened. The reflect marker suppresses selected reflection-sensitive consumers; it is not general listener suppression and remains a boolean non-depth-counted scope.

The projectile DoT listener runs at MONITOR with `ignoreCancelled=true`; it requires a non-empty DoT PDC string, a Player shooter and a LivingEntity hit target. It then applies the definition by ID. The projectile's own hit damage and the later DoT ticks are separate event paths.

### 7.2 Projectiles

Confirmed projectile routes and listener order:

- Path A resolves a direct Player attacker or a projectile with Player shooter. Bow-shot metadata is written by `ItemHook.shoot` at MONITOR (`shotDamage` + `shotType`) and read by `VanillaDamageListener`; a finite non-negative shotDamage overrides item attack damage. `shotType` is MAGIC only for the exact string `"MAGIC"`; other non-null values resolve as PHYSICAL.
- `CCGuard.onProjectileLaunch` runs at HIGHEST, returns if suppressed, and cancels Player launches while DISARM is active. The ItemHook MONITOR writer ignores cancelled launch events.
- LOW/HIGH ActiveEffectManager applies STEALTH and arrow-specific AIMED_SHOT behavior. Ordinary bow arrows retain the native Path A damage event.
- Mage snowball has two impact routes on the same projectile: MageAbilities writes the DotService PDC tag `burning` and also calls `FxService.chargeProjectile` with `phys=0`, `magic=dmg`. DotService handles ProjectileHitEvent at MONITOR; FxService handles the same event at MONITOR and applies hybrid Path B damage. DotRuntimeOwner registers DotService during core-service initialization, before FxService is registered later in `registerGameplayListeners`; therefore the internal same-priority order is DotService apply-DoT first, then FxService direct Path B hit. If the direct hit kills the target, the DoT instance is later removed by the dead-target tick cleanup.
- Fan-arrow sets vanilla arrow damage to zero, writes a DOUBLE `fan_arrow` PDC value, and its HIGH `FanArrowHit` listener calls Path B `CombatService.dealDamage`. The zero-damage vanilla arrow event and custom Path B hit are separate.
- `FxService` consumes its charged-shot map entry on hit; if caster is offline or hit is not a LivingEntity, it does not apply the custom hit. DoT projectile application separately requires a Player shooter, valid non-empty DoT ID and LivingEntity hit.
- Synchronous `AbilitySourceContext` does not automatically survive projectile flight. Fan-arrow and FxService open a new Path B context at impact; DoT application/ticks do not open one.

Remaining runtime work: test non-Player shooters, cancelled/invalid PDC, offline caster, same-projectile dual route when the direct hit is lethal, and third-party ProjectileHitEvent listeners at MONITOR.

### 7.3 Pets

Confirmed:
- PetService stores owner UUID in pet PDC (`rc_pet_owner`) and sets vanilla wolf owner where applicable. Pet attack damage is calculated from owner stats/spec at summon and refreshed by pet buff/tick logic; this is a stat snapshot/update path, not combat-event owner attribution.
- `PetRuntimeOwner.start()` registers PetService during core-service initialization, before `registerGameplayListeners()` registers ActiveEffectManager and PassiveListener. Its HIGH owner guard therefore precedes those listeners in the audited same-priority chain.
- Its HIGH `onDamageByEntity` handler is not suppression-aware. For a pet damager it reads owner PDC, cancels if owner is offline, and cancels hits against owner/allies according to `combat.canHit(owner, victim)`. For a Player owner attacking a LivingEntity while their pet exists, it may retarget the pet if it has no live target. The separate HIGH `onTarget` handler prevents pet targeting of owner/allies.
- Owner quit despawns the pet; periodic pet runtime also removes stale/offline-owner pets. Pet damage is not persisted across owner sessions.
- `VanillaDamageListener.resolveAttacker` resolves only direct Player or projectile with Player shooter; it does not read pet-owner PDC. `CombatService` opens non-null `AbilitySourceContext` only for Player source.

PetService therefore enforces ownership/friendly-fire but does not rewrite pet damage attribution to the owner. Pet hits do not automatically receive owner Path A outgoing offense, WeaponMastery, owner procs, Black Mass, talents or owner ability-source context. Suppressed synthetic events can still reach PetService: the owner/friendly-fire guard remains active, and Player-source synthetic events can still trigger the pet-retarget side effect. Pet owner attribution for damage stats and shared source context is **not established**.

### 7.4 Procs and nested damage

Proc placement is split:
- Path A event-time work includes crit-proc rolls in `applyOutgoingOffense` and HIGH-listener side effects; some can invoke Path B recursively.
- Path B has pre-application proc modifiers and post-application dispatch. `fromProc` affects selected follow-up behavior; it is not a universal guarantee that every event listener ignores resulting synthetic events.

Nested calls require independent characterization of outer/nested AbilitySourceContext, suppression depth, reflect marker, per-call DamageAttribution, burst tokens, cancellation and callbacks. Paper 26.2 now confirms that nested `CombatService` calls are separated by child attribution scopes, while direct nested `target.damage()` is included in the parent frame. Other proc combinations and third-party nested calls remain open.

### 7.5 Reflection

Reflection consumers issue Path B true-damage calls under a separate reflect marker. Black Mass and Warlock recoil consult `reflectSuppressed()`. General suppression and reflect suppression are distinct. The reflect marker is a boolean ThreadLocal, not depth-counted. The Paper 26.2 QA probe confirmed that `beginReflect(); beginReflect(); endReflect()` clears the outer marker (`NESTED_CLEAR_BUG`). The audited in-repo `beginReflect()` entry points are `WarlockPassives.onDamageIn`, `ProcService.onDefenderDamagedByMagic`, `DotService` tick damage and `VanillaDamageListener.reflect`. ProcService and DotService also open general suppression; VanillaDamageListener and WarlockPassives reflection issue true-damage calls. The reflect-sensitive consumers found in this snapshot are WarlockPassives outgoing/incoming passives and CombatService Warlock recoil. These call paths appear to block their own recursive effects, but that does not make the marker itself composable. Therefore the probe establishes a structural scope defect, not proof that one of these built-in paths currently nests `beginReflect()`. Before relying on nested use, either prove the call graph cannot nest or introduce a separately reviewed depth-counted scope.

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
| Black Mass lifesteal | Current: pre-application event-time side effect; approved target: post-application actual-damage lifesteal | Current code uses event damage at HIGH; target requires measured health + absorption delta exactly once |
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
5. The amount is sent to `HpBarService.heal`, which enters the CustomHealEvent path. The current call uses the two-argument overload, so `CustomHealEvent.healer` is null; this self-heal is not attributed as a HEALER cast and does not receive the Spec2RoleListener outgoing HEALER multiplier.

It does not wait for later same-priority listeners, HIGHEST cancellation, MONITOR final event state, Paper health/absorption application or a post-application `DamageAttribution` result.

Therefore the current Black Mass behavior is event-time, not actual-damage-based. **Approved target contract:** Black Mass lifesteal must be calculated from the damage actually applied by the originating hit, measured as the confirmed health + absorption delta. Cancelled hits and hits with zero applied damage grant no lifesteal. The calculation must occur after Paper application and after the relevant damage result is known; it must not use an intermediate event damage snapshot.

The actual-damage amount must be attributed to the originating hit exactly once. Nested damage, reflection and multiple component calls must not double-count the same applied delta. Healing continues through the existing two-argument `HpBarService.heal` / `CustomHealEvent` path by default, preserving the current null-healer attribution unless a separate gameplay decision changes it. `HpBarService.heal` returns no applied amount: anti-heal can short-circuit before the event, CustomHealEvent can cancel or modify the amount, and `HpPool.healFormula` clamps to the target's carrier max health. Do not infer that attempted healing equals applied healing or silently switch to the three-argument overload, which would change HEALER-role semantics.

This is an approved gameplay-semantic target, but it is **not implemented in this documentation PR**. The source trace narrows Black Mass to Path B: `AbilitySourceContext.open()` is called only inside `CombatService.dealDamage()`, and `WarlockPassives.onDamageOut` requires the attacker UUID to equal `CombatService.abilitySourceMark()`. Ordinary Path A events do not satisfy that gate in this snapshot. A post-application dispatch can therefore use Path B's measured `actualTaken` after `DamageAttribution` completes, once per `dealDamage` invocation (aggregating physical and magic components), while retaining the existing reflect guard and HpBarService/CustomHealEvent path.

The runtime probe confirms the attribution boundary: a nested `CombatService.dealDamage` call opens a child attribution scope and is measured separately; a direct nested `target.damage()` from an event listener does not open a child scope, so its applied delta is included in the parent frame. **Recommended dispatch boundary:** one Black Mass dispatch per `CombatService.dealDamage` invocation, using that invocation's measured delta. Child-scoped nested calls dispatch independently; unscoped direct nested event damage remains part of the parent aggregate and must not trigger a second Black Mass dispatch. This preserves one count of each applied delta at the call boundary; if a third-party plugin requires separate attribution for its direct nested hit, it must provide an explicit child scope or attribution contract. This is a proposed interpretation of the already-approved exactly-once target, not a production change.

This is a source-derived implementation direction, not a production change or a claim that the target contract is already satisfied. The current Watchtower tests verify the event-time behavior and characterize the current violations described below.

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

1. **Same-priority order:** the HIGH listener-instance sequence Spec2RoleListener → PetService → ActiveEffectManager (two HIGH registrations) → PassiveListener is runtime-confirmed. Two Paper job executions on the same HEAD established specific `onOutgoingLate` before generic `onIncomingLate`; the nested Blood Fury hit received a second STEALTH ×1.5 at LOW. Stability across independent builds remains unproven. Before canonical approval, make this ordering deterministic or remove the nested modifier dependency, then characterize another independent build.
2. **External listener chain:** identify relevant third-party damage modifiers/cancellers on production server, characterize priorities, and determine whether any handler issues direct nested `target.damage(...)`. Such unscoped nested deltas are included in the parent `DamageAttribution` measurement unless a child scope is opened. Repository source cannot prove the production plugin set.
3. **Path A Player branch (source-traced):** the NORMAL source sequence and early-return gates are now documented for Player and non-Player targets. Remaining work is runtime characterization across avoidance/parry, TRUE/CUSTOM/MAGIC/PHYSICAL sources, reflect, cap/reservation and synthetic nested events.
4. **Path B synthetic modifiers:** characterize LOW/HIGH changes and reconcile actual damage with pre-event cap/reservation semantics.
5. **Suppression contract (decision recorded):** target behavior skips re-entry damage calculations and duplicate RaskolClasses modifiers/procs, but preserves mandatory defensive/ownership checks; it is not a blanket bypass of all listeners. Current implementation does not yet satisfy this contract.
6. **Black Mass (decision recorded; runtime divergence confirmed):** target lifesteal is based on actual applied health + absorption damage; cancelled/zero-applied hits grant no lifesteal; one aggregate dispatch per originating `dealDamage` invocation. Paper 26.2 characterization confirms current event-time violations: a cancelled hit (`actualDamage=0`) still created one CustomHealEvent and healed about `0.1332`; one hybrid physical+magic invocation created two CustomHealEvents; direct unscoped nested damage was included in the parent measured delta. Production behavior remains unchanged.
7. **DoT:** test online/offline owner, school/event causes, ActiveEffectManager, FIGHTER/passives, death, refresh/stacking and DPS cap.
8. **Projectiles:** inventory every family, launch metadata, ability identity, PDC validation and non-Player shooter.
9. **Pets:** decide/test owner credit for stats, procs, talents, PvP and source context.
10. **Proc recursion:** cover each proc family, `fromProc`, nested source scopes, suppression depth, reflection and burst tokens.
11. **Reflection nesting (runtime-characterized):** the boolean marker clears the outer scope after an inner end. Confirm whether current in-repo call paths structurally prevent nesting; any change to depth-counted semantics requires a separate implementation review.
12. **Healing/lifesteal application (partially runtime-characterized):** CustomHealEvent healer is null on the current two-argument Black Mass call; Paper 26.2 probes confirmed cancellation and multi-component timing violations. Remaining work: dead target, overheal/absorption, CustomHealEvent cancellation/modification and recursion edge cases.
13. **Paper application:** compare event damage with actual health/absorption for relevant entities and DamageSources.
14. **Talents:** all 20 talent nodes remain disabled; no talent behavior is assumed.

## 12. Runtime evidence available

The latest extended Stage 4 characterization passed in Forge run `46149178466`, Paper Runtime job `140991943209`, on the audited mainline snapshot `a96a1a5c`: Build & Test, Quality Gate, RIPS Security, Storage Integrity and Paper 26.2 Runtime all passed. The Paper job was then rerun on the same HEAD as job `140992866652`; it passed again and reproduced the same ActiveEffectManager order and Path A/Path B matrices. An earlier diagnostic attempt counted a reflect-suppressed secondary event; the final probe isolates the Blood Fury Path B event by AbilitySourceContext and reflect marker. Runtime evidence:
- Path B physical/magic cancellation combinations;
- nested hit with outer physical cancellation;
- event damage increased and reduced by another listener;
- absorption-only application;
- Path A external reduction, external increase/overage, nested success with outer cancellation, and nested plus outer success;
- suppression scope visibility/cleanup (`SUPPRESS_SCOPE_PASS`);
- reflect marker nested scope: `NESTED_CLEAR_BUG` (`REFLECT_NESTING_CHARACTERIZED`);
- Black Mass nested source UUID: expected heal `0.19980000000000003`, actual `0.199798583984375`;
- cancelled Path B hit: `actualDamage=0.0`, `customHealEvents=1`, `actualHeal=0.13320159912109375` (`HEAL_EVENT_ON_CANCELLED_HIT`);
- one hybrid physical+magic `dealDamage`: `damageEvents=2`, `customHealEvents=2`, `actualDamage=1.9400005340576172` (`MULTIPLE_HEAL_EVENTS_PER_INVOCATION`);
- direct nested `target.damage()` without child scope: `parentReported=2.8600006103515625`, equal to `totalPoolDelta`, with two damage/heal events (`UNSCOPED_DIRECT_NESTED_DELTA_INCLUDED_IN_PARENT`);
- Black Mass CustomHealEvent attribution: `healerNull=true`;
- generic/specific probe actual order `specific → generic`; for ActiveEffectManager, direct behavior probe measured `onOutgoingLate → onIncomingLate` despite reflection enumeration `onIncomingLate → onOutgoingLate`;
- ActiveEffectManager order-sensitive nested proc: `state=OUTGOING_HIGH_BEFORE_INCOMING_HIGH`, `stealthAtNestedLowest=true`, nested damage `0.444 → 0.666`, LOW multiplier ratio `1.5` (`ACTIVE_EFFECT_SAME_INSTANCE_ORDER_CHARACTERIZED`);
- Paper 26.2 / Java 25 HIGH HandlerList order: `Spec2RoleListener → PetService → ActiveEffectManager ×2 → PassiveListener`;
- same-instance generic/specific probe observed `specific → generic` in one build, `generic → specific` in another, and an actual order differing from reflection in a third; ActiveEffectManager's actual method invocation order remains unproven;
- nested reflect probe confirmed `NESTED_CLEAR_BUG`: an inner `endReflect()` clears the outer boolean marker.

These scenarios characterize the plugin's own HIGH listener-instance order, reflect marker behavior and selected Black Mass attribution boundaries on Paper 26.2 / Java 25. The run that produced the Black Mass markers stopped at the ActiveEffectManager same-instance behavior probe because the QA harness counted an additional nested event; Path A was not rerun in that attempt. Earlier Watchtower runs passed the existing Path A and Path B matrices. The probes still do not establish a stable same-instance method contract or the external production plugin chain.

## 13. Stage 4 conclusion and review gate

**No canonical modifier order is approved or proposed as final by this document.**

The source trace establishes separate Path A and Path B sequences, confirms that suppression is selective rather than global, identifies event-time vs post-application effects, and records unresolved same-instance method-order, external-listener and attribution issues.

Review decisions recorded:
1. Suppression target contract approved: prevent re-entry and duplicate RaskolClasses modifiers/procs while retaining mandatory defensive/ownership checks; no blanket listener bypass.
2. Black Mass target contract approved: calculate lifesteal from actual applied health + absorption damage; cancelled/zero-applied hits grant no lifesteal.
3. These are design decisions only. This PR changes documentation; it does not implement either behavior.
4. Remaining source/runtime review must establish the actual production third-party listener chain, Path B exactly-once Black Mass attribution across nested and multi-component calls, and the remaining Path A / synthetic-event edge cases. Path A post-application measurement is not a Black Mass prerequisite under this snapshot's ability-source gate.
5. Only after those gaps are characterized should shared / Path A / Path B / conditional / post-application classification be frozen and golden characterization tests / Combat Resolver contracts be designed.

No production code/formulas changed. No talents enabled. No PR merged.
