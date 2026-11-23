# Combat Lab — Equipment Layer (first slice)

## Implemented contract

- Immutable loadout snapshots consume the same `ItemRegistry` and `ItemDefinition` model used by the runtime item system.
- Selection respects class/spec/level eligibility, item slot, item level and requested rarity.
- Exactly one best eligible definition is selected per equipment slot.
- Main-hand selection prefers the highest production `WeaponMastery` multiplier, then the highest item level, then a stable item ID.
- Base item stats and cumulative set thresholds are aggregated into an immutable snapshot.
- The snapshot exposes main-hand item identity, weapon family, physical/magic auto-attack type and mastery multiplier.
- Tests build deterministic fixtures through the production `ItemTemplateExpander` and the same item model/registry types without requiring a live Paper registry bootstrap.

## Deliberate limitations

This is the first deterministic equipment data slice, not the final equipment simulator:
- The CLI runner is not yet wired to load the live `items.yml` registry. Runtime integration can pass `plugin.getItems().registry()`; the next runner step must add a Paper-registry-independent loader for the committed item resource.
- Random affix rolls are not included yet. A shared deterministic affix roller must be extracted and used by both the runtime item factory and Combat Lab before affixes can be represented faithfully.
- Enchantment/gem/socket systems are not included.
- The snapshot is not yet wired into duel damage, HP, crit, resistance or attack-speed calculations.
- The tests validate the shared production item-model/template contracts, not full runtime registry parity.

The unarmed probe remains a separate, valid diagnostic and is not a v1.16 balance baseline.
