// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.model;

import dev.raskol.classes.classsystem.PlayerClass;

import java.util.List;
import java.util.Map;

/** Immutable definition; base stats are read from the current registry at use time. */
public record ItemDefinition(String id, String displayName, String material, ItemSlot slot,
                             WeaponType weaponType, ItemRarity rarity, int itemLevel,
                             PlayerClass playerClass, String spec, int levelRequirement,
                             Map<ItemStat, Double> baseStats, List<Affix> affixPool, int affixCount,
                             String setId, List<String> lore, boolean glow, Integer customModelData, String template) {

    public ItemDefinition(String id, String displayName, String material, ItemSlot slot, WeaponType weaponType, ItemRarity rarity,
                          int itemLevel, PlayerClass playerClass, String spec, int levelRequirement, Map<ItemStat, Double> baseStats,
                          List<Affix> affixPool, int affixCount, String setId, List<String> lore, boolean glow, Integer customModelData) {
        this(id, displayName, material, slot, weaponType, rarity, itemLevel, playerClass, spec, levelRequirement, baseStats,
                affixPool, affixCount, setId, lore, glow, customModelData, null);
    }

    public ItemDefinition {
        if (id == null || !id.matches("[a-z0-9_]{3,48}") || displayName == null || displayName.isBlank()
                || material == null || slot == null || rarity == null || itemLevel < 1 || itemLevel > 60
                || levelRequirement < 1 || levelRequirement > 60 || affixCount < 0) {
            throw new IllegalArgumentException("Invalid item definition: " + id);
        }
        baseStats = Map.copyOf(baseStats);
        affixPool = List.copyOf(affixPool);
        lore = List.copyOf(lore);
        if (affixCount > affixPool.size()) throw new IllegalArgumentException("affix-count exceeds pool: " + id);
        if (affixPool.stream().map(Affix::stat).distinct().count() != affixPool.size()) throw new IllegalArgumentException("affix stats must be unique: " + id);
        if (slot == ItemSlot.MAIN_HAND && weaponType == null) throw new IllegalArgumentException("Weapon type required: " + id);
        if (slot != ItemSlot.MAIN_HAND && weaponType != null) throw new IllegalArgumentException("Armor cannot have weapon type: " + id);
    }
}
