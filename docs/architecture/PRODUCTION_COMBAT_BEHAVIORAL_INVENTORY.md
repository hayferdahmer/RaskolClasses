# Production Combat Core — Behavioral Inventory

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

**Статус:** фактическая инвентаризация текущего поведения; не проект новой реализации.
**Платформа:** Paper 26.2, Java 25.
**Срез исходников:** main `dbbccdee1def17de2df4e331e29173da04072f68`.
**Архитектурная основа:** [Production Combat Core Architecture, PR #74](https://github.com/hayferdahmer/RaskolClasses/pull/74).
**Связанный контур:** [RASKOL Arena v1 Spec, PR #72](https://github.com/hayferdahmer/RaskolClasses/pull/72).
**Ограничение:** документ не меняет runtime, баланс или talent unlock state. Все 20 talent nodes остаются disabled.

> Важно: это статический аудит исходников main на указанном SHA. Он фиксирует кодовые пути и порядок вызовов внутри RaskolClasses. Он не выдаёт предположения о полном порядке всех сторонних Paper listeners за подтверждённый runtime-факт. Для этого предусмотрена отдельная Paper 26.2 event-matrix проверка.

## 1. Область и метод

Проверены:

- `CombatService`, `VanillaDamageListener`, `DamageProfile`, `DamageCaps`, `AvoidanceService`, `ResistService`, `ProcService`, `WeaponMastery`, school/penetration services;
- `AbilityRegistry`, class ability kits, passive listeners, `CCGuard`;
- `DotService`, `PetService`, `CustomHealEvent`, `HpBarService`;
- `CombatProfile` / registry, projectile metadata call sites, installations/effects that call combat;
- существующие combat-related unit/runtime tests в `src/test/java`.

Основные source anchors:

- [CombatService.java](https://github.com/hayferdahmer/RaskolClasses/blob/dbbccdee1def17de2df4e331e29173da04072f68/src/main/java/dev/raskol/classes/combat/CombatService.java)
- [VanillaDamageListener.java](https://github.com/hayferdahmer/RaskolClasses/blob/dbbccdee1def17de2df4e331e29173da04072f68/src/main/java/dev/raskol/classes/combat/VanillaDamageListener.java)
- [AbilityRegistry.java](https://github.com/hayferdahmer/RaskolClasses/blob/dbbccdee1def17de2df4e331e29173da04072f68/src/main/java/dev/raskol/classes/ability/AbilityRegistry.java)
- [ProcService.java](https://github.com/hayferdahmer/RaskolClasses/blob/dbbccdee1def17de2df4e331e29173da04072f68/src/main/java/dev/raskol/classes/combat/ProcService.java)
- [DotService.java](https://github.com/hayferdahmer/RaskolClasses/blob/dbbccdee1def17de2df4e331e29173da04072f68/src/main/java/dev/raskol/classes/combat/dot/DotService.java)
- [HpBarService.java](https://github.com/hayferdahmer/RaskolClasses/blob/dbbccdee1def17de2df4e331e29173da04072f68/src/main/java/dev/raskol/classes/hud/HpBarService.java)

## 2. Карта двух основных damage paths

```text
A. VANILLA / PAPER EVENT PATH
Minecraft/Paper damage source
  └─ EntityDamageEvent / EntityDamageByEntityEvent
      ├─ LOW: ActiveEffectManager / early passive modifiers
      ├─ NORMAL: VanillaDamageListener.onDamage
      │   ├─ attacker + source CombatProfile resolution
      │   ├─ outgoing offense for Player / profiled source
      │   ├─ school, immunity, target profile vulnerability
      │   ├─ mob profile resist OR player avoidance/resist
      │   ├─ single-hit + burst caps
      │   ├─ reflect and proc side effects
      │   └─ event.setDamage(...)
      ├─ HIGH: role/passive listeners may still modify/cancel
      ├─ HIGHEST: CCGuard and other late guards
      └─ MONITOR: burst rollback / resource observers
          └─ Paper applies final event result

B. CUSTOM / RASKOL ABILITY PATH
AbilityRegistry.castOn
  ├─ Auth → class → caster → CC → level → unlock → cooldown
  ├─ resource consume → anti-spam mark
  ├─ setCurrentCastSchool(ThreadLocal)
  └─ class/tree caster
      └─ CombatService.dealDamage(target, source, DamageProfile, ...)
          ├─ canHit / target state / sanitize
          ├─ spec and proc outgoing modifiers
          ├─ block / crit / penetration
          ├─ target resist / profile / school modifiers
          ├─ single-hit + burst caps
          ├─ target.damage(physical channel)
          ├─ target.damage(magic + true channel)
          │   └─ Paper event is emitted with suppress marker
          ├─ before/after health delta
          └─ post-hit procs / CC break / double strike / recoil
```

Paths A and B converge on Paper damage events only at the application boundary. They do not currently share one complete resolver.

## 3. Path A — vanilla / Paper event pipeline

### 3.1. Event entry and source resolution

`VanillaDamageListener.onDamage(EntityDamageEvent)` is registered at `NORMAL`, `ignoreCancelled=true`.

At entry it:

1. Reads and consumes the suppress marker through `CombatService.consumeSuppress()`.
2. Resolves target `CombatProfile` from `CombatProfileRegistry`.
3. Resolves source profile for `EntityDamageByEntityEvent`.
4. Resolves a Player attacker only when damager is Player or Projectile whose shooter is Player.
5. Applies friendly-fire/ally cancellation for resolved player attackers.

Consequences:

- A projectile shot by a player is attributed to the shooter for the vanilla path.
- A non-player mob can supply a `CombatProfile` if registered.
- A pet entity is not resolved as its owner by `VanillaDamageListener.resolveAttacker`; PetService has a separate PDC owner lookup for ally protection, not a shared combat-source context.
- A non-player, unprofiled source has no player outgoing-stat/penetration attribution in this listener.

### 3.2. Outgoing offense

For Player or Player-shot projectile, `applyOutgoingOffense`:

1. Determines PHYSICAL/MAGIC/TRUE using event cause, projectile metadata, auto-attack type or source profile.
2. TRUE returns without the Player outgoing formula.
3. Adds weapon power or spell power multiplied by the configured basic coefficient.
4. Optionally adds STR + level to physical or INT + level to magic.
5. Uses item ATTACK_DAMAGE as replacement base when positive; projectile shotDamage PDC can replace item damage.
6. Applies WeaponMastery for physical damage based on class and main-hand weapon definition.
7. Rolls stealth bonus and consumes next-hit amplifier.
8. Rolls melee or spell critical and applies the respective multiplier.
9. On crit, runs crit feedback and crit procs; physical crit also sets undodgeable state.

The early outgoing modifiers in `ActiveEffectManager` run at `LOW`, before the combat listener's `NORMAL` calculation. `Spec2RoleListener` and `PassiveListener` also have damage listeners at `HIGH`; those run later and can observe/change the event after the core's normal-priority cap calculation. Ordering between listeners registered at the same priority must be verified from runtime registration/dispatch, not inferred from annotation alone.

### 3.3. School and target-profile handling

After outgoing offense:

- Seal amplification is applied to the event amount.
- School is selected from current cast school if present; otherwise from effective damage type / configured cause mapping.
- Entity-type school immunity and configured school multiplier are applied.
- Target `CombatProfile` can cancel by immunity or multiply by school vulnerability.
- For non-player targets with a profile, channel resistance, school resistance and source penetration are combined and clamped to 0–90%.

For player targets:

- Spectator exits the custom player-defense section.
- If resistance is disabled in the world, the method exits after carrier break handling.
- TRUE damage takes the environmental lethal-scale path and bypasses avoidance/resistance.
- Suppressed damage exits before avoidance/resistance; DoT therefore computes its own mitigation before creating the Paper event.
- Physical damage can be dodged/parried through `AvoidanceService.tryAvoid`.
- Channel resistance and, when enabled, channel/school penetration plus elemental mitigation are applied.
- Expose on the target is applied as incoming amplification.
- Single-hit carrier cap and burst-window cap are applied.
- Reflect, carrier break and defender proc hooks are invoked in the event listener.

### 3.4. Final application and cancellation

The listener mutates `event.setDamage(...)`; Paper applies the event after listeners finish. The listener itself does not measure final health delta.

The burst tracker arms a rollback entry. A MONITOR listener removes that entry if the event is later cancelled. This is specifically a cancellation rollback, not a general proof of actual health loss.

Potential ordering risk: some proc/reflect/defender side effects are initiated from the NORMAL listener before later HIGH/HIGHEST handlers and before final health application. A later listener can still cancel or modify the event. The exact set of side effects that survive a downstream cancellation needs an event-level test.

## 4. Path B — custom ability / direct CombatService pipeline

### 4.1. Ability dispatch

`AbilityRegistry.castOn` centralizes the following gates:

1. AuthGate.
2. Class selection.
3. Caster / targeted caster lookup.
4. CC cast guard.
5. Character level.
6. Transferable ability unlock.
7. Tree ability unlock.
8. Cooldown.
9. Resource consume.
10. Anti-spam window check/mark.
11. Caster invocation under current-cast-school marker.
12. Resource refund on failed cast.
13. `onAbilityCast` proc on success.
14. Cooldown start after successful cast.

The current-cast-school marker is cleared in `finally`. This is a cast-scoped ThreadLocal, not a field on the damage request.

### 4.2. CombatService.dealDamage order

The current method accepts target, source, three-channel `DamageProfile`, `allowOverCap` and `fromProc`.

Observed order:

1. Reject null/dead/invalid target, failed `canHit`, empty sanitized profile, and Player CREATIVE/SPECTATOR.
2. Read current cast school and calculate school multiplier/immunity gate.
3. Apply outgoing specialization physical/magic percentages to their channels.
4. Read and consume next-hit amplifier.
5. Apply target expose multiplier.
6. Apply Enrage to physical channel.
7. Roll stealth bonus to physical and magic channels.
8. Roll armor penetration proc.
9. For physical channel against Player with shield, roll block; on success physical channel becomes zero.
10. Roll separate melee and spell crits for physical and magic channels.
11. Apply Warlock low-health magic-resist bypass condition.
12. Apply player channel resistance; if school system enabled, apply channel penetration, elemental resistance, school penetration and mitigation. For non-player target, use target CombatProfile when present.
13. Apply school multiplier to post-mitigation physical and magic/true channels.
14. Apply seal amplification.
15. Apply single-hit and burst caps unless `allowOverCap` is true.
16. Convert formula amount to carrier scale and call `target.damage` separately for physical and magic/true channels.
17. Measure target health before/after each call and derive actual health delta.
18. If actual damage is positive, break CC and invoke defender procs.
19. For Player source and non-proc call, invoke melee-hit, crit procs, undodgeable, expose and double-strike hooks.
20. Apply Warlock recoil through direct health mutation, guarded against reflect.

### 4.3. Application semantics

Physical and magic/true channels are applied as separate synchronous Paper damage calls. The service resets no-damage ticks before each call. It wraps calls with suppress marker and stores `ABILITY_SOURCE` while applying them.

The return value is derived from before/after Bukkit health delta, converted through target scale. This is more informative than Path A's pre-application event amount, but it still measures entity health change, not a separately typed result with absorbed/blocked/cancelled/overkill fields.

True damage is added to the magic-side application amount after channel mitigation. It is therefore not mitigated as a physical/magic channel, but it is still subject to later school/seal multipliers and caps in this path.

### 4.4. Burst-cap cancellation gap

`DamageCaps.applyBurstCap` records accepted burst amount when called. Path A arms a `BurstRollbackTracker` entry and has a MONITOR cancellation rollback.

Path B calls `applyBurstCap` directly but does not arm the rollback tracker. If the Paper event generated by `target.damage` is cancelled by a downstream listener, health delta can be zero while the burst-window entry has already been recorded. This is a code-level discrepancy to cover with a focused test before unifying the resolver.

## 5. Source and origin inventory

| Source kind | Current entry | Current attribution | Key limitation |
|---|---|---|---|
| Player melee | Paper EntityDamageByEntityEvent → A | Player damager | Full outgoing formula lives in listener |
| Player vanilla projectile | Paper event → A | Projectile shooter Player; PDC shot metadata | Depends on metadata and vanilla event cause |
| Custom ability projectile | ProjectileHitEvent → ability callback → B | Shooter Player passed explicitly | Projectile event is translated into a new damage call |
| Direct class/tree ability | AbilityRegistry → caster → B | Player source argument | Ability/school context is ThreadLocal |
| Environmental damage | EntityDamageEvent → A | No actor | Cause mapping and TRUE handling differ from custom path |
| CombatProfile mob/source | Paper event → A | Registered source/target profile | Integration registration/lifecycle is external |
| Pet attack | Native entity attack → A | Pet entity; owner PDC used by PetService ally guard | Owner/credit is not resolved by VanillaDamageListener |
| DoT tick | DotService.tick → direct Paper damage event under suppress | Stored owner UUID, re-resolved online Player | Manual mitigation + suppressed event; no common resolver |
| Vanilla reflect | Vanilla listener → B with trueDamage | Original source entity passed as target; defender as source | Nested call and reflect ThreadLocal guard |
| Proc reflect_magic | ProcService → direct attacker.damage under suppress | Defender is direct damager | Does not use CombatService.dealDamage |
| Double strike | ProcService callback → B trueDamage | Original Player source | Replays already mitigated physical result; fromProc suppresses recursive on-hit chain |
| BLOOD_FURY | ActiveEffectManager → B with fromProc=true | Victim is source, aggressor is target | Separate ThreadLocal recursion guard |
| Installation damage | Installation handler → B | Stored owner Player where available | Ownership depends on each installation handler |
| Healing | HpBarService.heal → CustomHealEvent | Optional Player healer | Separate pipeline from damage |

### 5.1. Projectile distinctions

There are at least two distinct projectile patterns:

- Ordinary player-shot projectile is handled by vanilla Path A; the shooter is resolved and projectile PDC can override damage/type.
- Hunter fan-arrow sets vanilla arrow damage to zero, stores damage in projectile PDC, then its ProjectileHit listener calls `CombatService.dealDamage` (Path B).

Do not assume all projectiles share the same damage path.

### 5.2. Pet ownership

`PetService` stores `rc_pet_owner` in entity PDC and uses it for ally checks, target checks, lifecycle and owner lookup. Its own damage listener does not convert pet damage into `CombatService.dealDamage`. The main vanilla listener resolves only Player or Player projectile shooter as attacker. Therefore the current pet combat path does not carry a first-class actor/owner/credit triple through the common damage pipeline.

## 6. Healing path inventory

### 6.1. Custom healing

`HpBarService.heal(target, amount, healer)`:

1. Returns early for an anti-healed Player.
2. Publishes `CustomHealEvent`.
3. Returns if cancelled or amount is non-positive.
4. Applies the resulting formula amount through `attrs().healFormula`.

Known listeners include:

- `Spec2RoleListener.onCustomHeal` at HIGH: healer outgoing and target incoming modifiers.
- `PassiveListener.onCustomHeal` at HIGHEST: class passive dispatch for the healer.
- `ResourceService.onCustomHeal` at MONITOR: resource-on-heal observation.

The comments in PassiveListener describe HIGHEST as running before HIGH, but Bukkit priority order is LOWEST → LOW → NORMAL → HIGH → HIGHEST → MONITOR. The actual order is HIGH before HIGHEST. This discrepancy should be corrected or explicitly tested; do not rely on the current comments as proof of order.

### 6.2. Vanilla healing

Vanilla regain uses `EntityRegainHealthEvent`. Spec2 role modifiers run at HIGH; CombatService anti-heal cancellation runs at HIGHEST. This is not the same path as custom healing.

### 6.3. Missing result semantics

Neither `CustomHealEvent` nor `HpBarService.heal` exposes a typed result with requested amount, effective healing, overheal and actual health delta. The custom event exposes mutable amount and cancellation only.

## 7. Modifier-order comparison

This table records source order, not an endorsed future formula order.

| Stage | Path A — vanilla event | Path B — custom resolver |
|---|---|---|
| Base damage | Paper event base; Player power/item additions | Supplied DamageProfile |
| Weapon/mastery | Weapon attack stat and WeaponMastery for physical | Not recalculated in CombatService |
| Spec outgoing damage | Not the same percentage step as B; event offense comes from power/item | Physical/magic spec percentages early |
| Early effects | LOW listeners can modify event before NORMAL | Explicitly invoked by ability/caster or read in services |
| Amplifier | Stealth then next-hit amplifier in outgoing event formula | Next-hit amplifier, then expose/enrage/stealth sequence |
| Expose | Target expose applied after mitigation in player branch | Target expose applied to base channels before mitigation |
| Enrage | Not in same outgoing formula block | Physical channel before mitigation |
| Armor penetration proc | No equivalent `rollArmorPen` call in A outgoing handler | Rolled before target mitigation |
| Block/avoidance | Physical dodge/parry through AvoidanceService | Shield block roll zeroes physical channel; no same dodge/parry step |
| Crit | One event type (physical or magic) | Separate physical and magic channel rolls |
| School multiplier | Before target resist in event path | After target resist in CombatService |
| Resist / penetration | Player and profiled mob branches differ | Player and CombatProfile mob branches differ |
| Caps | Event carrier cap + burst cap; rollback if downstream cancellation | Formula cap + burst cap; no cancellation rollback arm |
| Apply | One event amount; Paper finalizes after listeners | Separate physical and magic/true Paper calls |
| Actual delta | Not measured by VanillaDamageListener | Measured by before/after health |
| Post-hit proc timing | Some proc hooks execute before final event application | Most source on-hit hooks execute after positive measured delta |

### 7.1. Confirmed behavioral differences

1. **Base calculation differs.** Path A derives damage from vanilla event amount plus Player weapon/spell power, item attack damage, optional STR/INT and WeaponMastery. Path B trusts the supplied DamageProfile and applies specialization percentages; it does not run the same item/weapon base formula.
2. **Modifier order differs.** Expose is applied after mitigation in A and before mitigation in B. School multiplier is applied before resistance in A and after resistance in B.
3. **Critical model differs.** A resolves one effective event type; B can roll physical and magic crit independently for a hybrid profile.
4. **Avoidance/block differs.** A uses physical dodge/parry. B has a shield block check that zeros physical damage; the same avoidance step is not called.
5. **Armor penetration differs.** B rolls the armor_pen proc. A uses channel/school penetration traits but does not call the same armorPen proc roll in its outgoing handler.
6. **Caps differ in units/boundaries.** A caps carrier event amount; B caps formula amount before scaling. A has cancellation rollback; B currently lacks the corresponding rollback.
7. **Side-effect timing differs.** A starts some procs/reflect work while mutating an event that can still be changed/cancelled by later listeners. B runs post-hit hooks only after measuring positive health delta.
8. **Source attribution differs.** A resolves Player and Player-shot projectile; B receives an explicit Entity source argument. Neither provides a unified immutable actor/owner/credit/parent context.
9. **True damage representation differs.** A classifies TRUE event causes and has an environmental lethal-scale branch. B combines trueDamage into the magic-side application after channel mitigation, then applies later multipliers/caps.
10. **Pet and DoT are separate special paths.** Neither is a complete participant in a shared source-aware resolver today.

These differences are not automatically bugs: some may be deliberate design. They are the parity checklist; no formula should be reordered until each difference has an approved behavior decision and golden test.

## 8. Suppression and nested-event markers

Current static markers in CombatService:

- `SUPPRESS` — one-shot Boolean consumed by `consumeSuppress()`.
- `ABILITY_SOURCE` — source Player UUID during Path B health application; used by Warlock `black_mass` passive to distinguish ability damage.
- `REFLECT_SUPPRESS` — suppresses reflect-related recursion/side effects.
- `CURRENT_CAST_SCHOOL` — cast-scoped school context read by both damage paths.

### 8.1. Suppress consumption ordering concern

Static review identifies a concrete ordering concern:

- `VanillaDamageListener.onDamage` at NORMAL calls `consumeSuppress()`; when true, that method resets the ThreadLocal to false.
- `CCGuard.onDamageByEntity` at HIGHEST also calls `consumeSuppress()` to skip suppressed DoT/Path B events.
- Bukkit invokes NORMAL before HIGHEST. Therefore the first listener can consume the one-shot marker before CCGuard checks it. CCGuard may then see false and apply its STUN/FEAR/BLIND/DISARM guard to the generated damage event.

This contradicts the comments that suppressed damage bypasses CCGuard. It is a high-priority event-level verification item, not a claim that a runtime reproduction has already been performed. A shared immutable event context or non-consuming marker read is a likely direction, but no fix is included in this inventory.

### 8.2. ThreadLocal scope

The markers are process-thread state rather than explicit event fields. Nested damage calls and multiple listeners rely on correct set/clear/finally discipline. A future context must preserve source, school, suppression reason and parent event without a global mutable marker.

## 9. Current tests and evidence coverage

Combat-related tests present in main include:

- `CombatMathTest`
- `ResistBreakdownTest`
- `BurstRollbackTrackerTest`
- `DotMathTest`
- `DotCombatProfileMathTest`
- `DotRuntimeOwnerTest`
- `ManaSoakedRuntimeOwnerTest`
- `PetMathTest`
- `PetRuntimeOwnerTest`
- `CCServiceDrStateCleanupTest`
- `CCRuntimeOwnerTest`
- `CCFeedbackRuntimeOwnerTest`
- `CooldownRuntimeOwnerTest`
- `WarlockChannelTaskOwnerTest`
- `CombatProfileTest`

These establish coverage for isolated math, state owners and selected lifecycle behavior. The main test tree does not currently contain a dedicated end-to-end `VanillaDamageListener` vs `CombatService.dealDamage` parity test or a full Paper event-priority integration matrix.

SelftestRunner contains selected runtime/selftest assertions, but these are not a substitute for a repeatable Paper damage-event suite.

### 9.1. Minimum missing behavioral tests

1. Same attacker/target/profile/input through A and B with controlled RNG.
2. Physical, magic, hybrid and true channels against Player and profiled/unprofiled mob.
3. Modifier order: expose, school multiplier, armor penetration, crit, enrage, stealth, seal.
4. Dodge/parry vs shield block and undodgeable state.
5. Single-hit/burst caps with downstream event cancellation and later HIGH/HIGHEST modifiers.
6. Path B physical+magic split with external plugin cancellation on first or second event.
7. DoT tick while owner is stunned/feared; suppression visibility to both VanillaDamageListener and CCGuard.
8. Pet owner credit, ally gate, source stats and resource/proc attribution.
9. Reflect and proc recursion depth/parent chain.
10. Healing event priority, antiheal, cancelled heal, modified amount and actual effective health delta.
11. Projectile metadata and owner attribution for vanilla projectile vs custom zero-damage projectile.
12. Repeatability with seeded RNG and virtual clock.

## 10. Arena relationship — what is and is not production today

- Main currently has no canonical `combat-ci.yml` workflow.
- PR #73 introduces the Arena CI guard and diff/report validation; it is CI tooling, not a replacement production combat engine.
- PR #54–#58 contain staged Combat Lab work: headless snapshots/duel runner, equipment, cadence, specialization-locked gear and partial source attribution/talent consumers.
- The Combat Lab chain is not equivalent to a completed Paper-free production domain. Its report contracts and prototypes must be reconciled with actual Path A/B behavior documented here.
- The `v1.16.0` balance baseline remains pending; statistical comparisons must not be treated as approved balance evidence.

No PR #54–#58 branch, base, commit or merge state is changed by this document.

## 11. Behavioral decisions required before resolver implementation

| Decision | Current evidence | Required decision |
|---|---|---|
| Canonical base damage | A uses vanilla + stats/item; B uses DamageProfile | Which attack families are normalized into a shared base model |
| Expose placement | A after mitigation; B before mitigation | Preserve one order or define explicit source-specific semantics |
| School multiplier placement | A before resist; B after resist | Canonical order and compatibility impact |
| Crit on hybrid profile | A one event type; B separate channel rolls | One shared rule for hybrid/dual-channel actions |
| Avoidance vs block | A dodge/parry; B shield block | Eligibility and order of all defensive outcomes |
| Armor penetration proc | Present in B, not same A stage | Whether it applies to auto-attacks, abilities or both |
| Event side-effect timing | A before final application; B after measured delta | Which hooks require confirmed hit |
| Burst rollback | A rollback tracker; B missing arm | Atomic reservation/commit/rollback contract |
| Suppression visibility | One-shot marker read by multiple priorities | Explicit event-context contract |
| Pet attribution | PDC owner only in PetService | Actor/owner/credit and PvP eligibility semantics |
| DoT semantics | Manual mitigation then suppressed Paper event | One resolver entry for tick damage and no accidental on-hit recursion |
| Healing semantics | CustomHealEvent vs vanilla regain | Effective heal/overheal/actual delta and event ordering |
| Time/RNG | Wall clock and ThreadLocalRandom in combat services | Clock/RNG ports and deterministic test contract |

## 12. Recommended sequence after inventory review

1. Resolve or explicitly accept the suppression/CCGuard ordering concern with a Paper 26.2 integration test.
2. Freeze a versioned Path A/Path B behavior matrix with examples and expected outputs.
3. Add event-level tests for downstream cancellation, cap rollback, listener priority and source attribution.
4. Define the shared `CombatSourceContext` and result semantics without switching production paths.
5. Implement one small parity slice in shadow mode; do not change balance values.
6. Expand only after runtime and headless results match the approved matrix.

## 13. Explicit non-goals

- No production Java refactor or new combat formula.
- No changes to item, class, specialization, talent, cooldown or balance configuration.
- No activation of any of the 20 talent nodes.
- No merge/rebase/base change of PR #54–#58.
- No merge of PR #72, #73 or #74.
- No new required Arena check.
- No approved v1.16.0 balance baseline is created by this document.

**Inventory conclusion:** Path A and Path B share services and some formulas, but they are not behaviorally identical. The largest known parity risks are different base formulas and modifier order, different avoidance/crit handling, non-atomic burst accounting in Path B, early Path A side effects, incomplete pet/source attribution, and the one-shot suppress marker being read at different event priorities. These must become explicit test cases and decisions before a shared production resolver is introduced.
