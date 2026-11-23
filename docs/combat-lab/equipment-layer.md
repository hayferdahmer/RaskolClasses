# Combat Lab — Equipment and Weapon Cadence

## Implemented contract

- The headless runner loads the committed `items.yml` through `ItemConfigLoader.loadHeadless()`; it uses the production `ItemRegistry`, item definitions, eligibility rules, `AffixRoller`, `ItemStatAggregator` and `SetBonusService`.
- Loadout selection is deterministic by class, specialization, level, rarity, slot, WeaponMastery, item level and stable item ID.
- Base stats, affixes and active set thresholds are aggregated into an immutable equipment snapshot. `ATTACK_DAMAGE` contributes only from MAIN_HAND, matching `ItemHook`.
- The duel snapshot includes equipment-derived attributes, physical/magic auto-attack type, WeaponMastery and a resolved attack interval.
- Equipment report schema 1.1 records each combatant's cadence interval and cadence model ID.

## Weapon cadence contract

The headless model is independent of Bukkit registry initialization:

- Melee weapons use the Paper 26.2 reference attack-speed values for the materials currently configured in `items.yml`: `IRON_SWORD=1.6`, `IRON_AXE=0.9`, `MACE=0.6`, `TRIDENT=1.1`, `BLAZE_ROD=4.0`.
- The interval is `1 / (materialAttackSpeed × (1 + equipmentAttackSpeedPct / 100))`. This mirrors the one aggregated `MULTIPLY_SCALAR_1` modifier installed by `ItemHook`.
- Bow cadence uses a full critical charge (20 ticks / 1 second). Crossbow cadence uses an unenchanted reload (25 ticks / 1.25 seconds). Item `ATTACK_SPEED` does not accelerate these use-duration mechanics.
- Unarmed cadence uses the player default of 4 full-strength attacks per second (0.25 seconds).
- `PaperWeaponCadenceResolver` reads the actual Paper player base attribute and material HAND modifiers. It audits all configured melee materials during plugin startup and logs any mismatch against the headless reference table.
- `SPEAR` is explicit in WeaponMastery: native for HUNTER, foreign for the other classes, consistent with the existing Hunter SPEAR/TRIDENT item template.

## Deliberate limitations

- The Paper startup audit has been implemented but must still be confirmed against a live RASKOL Paper 26.2 startup log.
- Bow partial draw, Quick Charge, projectile travel, hit probability, ammunition and movement are not simulated. The current profile represents a fully charged bow and an unenchanted crossbow.
- The runner still passes `spec=null`; specialization-specific item templates and all 18 specialization sets are the next implementation slice.
- Enchantments, gems and sockets are not included.
- Active abilities, talents, procs, pets, CC, resource economy, burst caps and PvE behavior are not included.

This equipment-aware auto-attack profile is not a complete combat simulator and is not an approved v1.16 balance baseline. Keep all talent nodes disabled until the remaining runtime layers and QA are complete.
