# Combat Lab — Equipment, Weapon Cadence and Specialization Sets

## Implemented contract

- The headless runner loads committed `items.yml` through `ItemConfigLoader.loadHeadless()`; it uses the production `ItemRegistry`, item definitions, eligibility rules, `AffixRoller`, `ItemStatAggregator` and `SetBonusService`.
- Loadout selection is deterministic by class, specialization, level, rarity, slot, exact-spec priority, WeaponMastery, item level and stable item ID.
- The template expander preserves the specialization ID in each generated `ItemDefinition`; the loader rejects a specialization that does not belong to the declared class.
- Added 18 specialization-locked sets, each with five slots (main hand, head, chest, legs, feet), three generated level bands (20/40/60), and 2/4/5-piece bonuses.
- At levels 30, 45 and 60 the specialization runner requires all five matching pieces and the corresponding active set. Levels 1 and 15 intentionally remain un-geared.
- Base stats, production affix rolls and active set thresholds are aggregated into immutable equipment snapshots. `ATTACK_DAMAGE` contributes only from MAIN_HAND, matching `ItemHook`.
- The specialization duel matrix covers every directed pair of distinct specializations at levels 1, 15, 30, 45 and 60: 1,530 scenarios, 1,200 duels per scenario.
- Equipment report schema 1.2 records specialization IDs, item IDs, active sets, weapon family, attack type, mastery multiplier and cadence model for both sides.

## Weapon cadence contract

The headless model is independent of Bukkit Registry initialization:

- Melee weapons use the committed material attack-speed reference values: `IRON_SWORD=1.6`, `IRON_AXE=0.9`, `MACE=0.6`, `TRIDENT=1.1`, `BLAZE_ROD=4.0`.
- The interval is `1 / (materialAttackSpeed × (1 + equipmentAttackSpeedPct / 100))`. This mirrors the one aggregated `MULTIPLY_SCALAR_1` modifier installed by `ItemHook`.
- Bow cadence uses a full critical charge (20 ticks / 1 second). Crossbow cadence uses an unenchanted reload (25 ticks / 1.25 seconds). Item `ATTACK_SPEED` does not accelerate these use-duration mechanics.
- Unarmed cadence uses the player default of 4 full-strength attacks per second (0.25 seconds).
- `PaperWeaponCadenceResolver` reads the actual Paper player base attribute and material HAND modifiers. It audits all configured melee materials during plugin startup and logs mismatches without blocking server startup.
- `SPEAR` is explicit in WeaponMastery: native for HUNTER, foreign for the other classes, consistent with the existing Hunter SPEAR/TRIDENT template.

## Deliberate limitations

- The Paper startup audit has been implemented but must still be confirmed against a live RASKOL Paper 26.2 startup log.
- Bow partial draw, Quick Charge, projectile travel, hit probability, ammunition and movement are not simulated. The current profile represents a fully charged bow and an unenchanted crossbow.
- This remains an auto-attack-only profile. Active abilities, talents, procs, pets, CC, resource economy, burst caps and PvE behavior are not included.
- The 18-spec matrix is an equipment calibration pass, not an approved v1.16 balance baseline. Do not enable talent nodes until the remaining runtime combat layers and QA are complete.
