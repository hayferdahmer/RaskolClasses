# Combat Lab — Equipment Layer (first slice)

## Implemented contract

- Loadout snapshots are built from the production `items.yml` registry through `ItemConfigLoader`.
- Selection respects class/spec/level eligibility, item slot, item level and requested rarity.
- Exactly one best eligible definition is selected per equipment slot.
- Main-hand selection prefers the highest production `WeaponMastery` multiplier, then the highest item level, then a stable item ID.
- Base item stats and cumulative set thresholds are aggregated into an immutable snapshot.
- The snapshot exposes main-hand item identity, weapon family, physical/magic auto-attack type and mastery multiplier.

## Deliberate limitations

This is the first deterministic equipment slice, not the final equipment simulator:
- Random affix rolls are not included yet. A shared deterministic affix roller must be extracted and used by both the runtime item factory and Combat Lab before affixes can be represented faithfully.
- Enchantment/gem/socket systems are not included.
- The snapshot is not yet wired into the duel runner's damage, crit, HP, resist and attack-speed calculations. That integration is the next task in this layer.
- Item stats are aggregated with the same eligibility and item definitions as production; this does not yet claim full runtime parity.

The unarmed probe remains a separate, valid baseline-free diagnostic until the equipment-aware runner is implemented and independently validated.
