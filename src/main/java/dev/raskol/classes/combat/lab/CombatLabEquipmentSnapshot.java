// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.WeaponMastery;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.SetDefinition;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemRegistry;
import dev.raskol.classes.item.service.ItemStatAggregator;

/**
 * Неизменяемый headless-снимок экипировки, собранный из production items.yml.
 *
 * Использует те же определения предметов, требования экипировки, классовые
 * ограничения, слоты, базовые статы, сетовые пороги и WeaponMastery, что и сервер.
 * Случайные affix-rolls намеренно не включены в этот первый слой: для них нужен
 * отдельный общий детерминированный roller, используемый и ItemFactory, и Combat Lab.
 */
public record CombatLabEquipmentSnapshot(
        Map<ItemStat, Double> stats,
        List<String> equippedItemIds,
        List<String> activeSetIds,
        String mainHandItemId,
        WeaponType weaponType,
        String autoAttackType,
        double weaponMasteryMultiplier) {

    public static final CombatLabEquipmentSnapshot EMPTY = new CombatLabEquipmentSnapshot(
            Map.of(), List.of(), List.of(), null, null, "PHYSICAL", WeaponMastery.FOREIGN);

    public CombatLabEquipmentSnapshot {
        stats = Map.copyOf(stats);
        equippedItemIds = List.copyOf(equippedItemIds);
        activeSetIds = List.copyOf(activeSetIds);
        if (autoAttackType == null || (!autoAttackType.equals("PHYSICAL") && !autoAttackType.equals("MAGIC"))) {
            throw new IllegalArgumentException("Некорректный тип автоатаки экипировки");
        }
        if (!Double.isFinite(weaponMasteryMultiplier) || weaponMasteryMultiplier < 0.0) {
            throw new IllegalArgumentException("Некорректный множитель WeaponMastery");
        }
    }

    public static CombatLabEquipmentSnapshot from(
            ItemRegistry registry, PlayerClass playerClass, int level, ItemRarity rarity) {
        if (registry == null || playerClass == null || rarity == null) {
            throw new IllegalArgumentException("Реестр, класс и редкость обязательны");
        }
        if (level < 1 || level > 60) {
            throw new IllegalArgumentException("Недопустимый уровень экипировки: " + level);
        }

        List<ItemDefinition> equipped = new ArrayList<>();
        for (ItemSlot slot : ItemSlot.values()) {
            registry.all().stream()
                    .filter(item -> item.slot() == slot)
                    .filter(item -> item.rarity() == rarity)
                    .filter(item -> item.itemLevel() <= level)
                    .filter(item -> ItemStatAggregator.eligible(item, playerClass, null, level))
                    .min(candidateOrder(playerClass))
                    .ifPresent(equipped::add);
        }

        Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
        Map<String, Integer> setPieces = new HashMap<>();
        for (ItemDefinition item : equipped) {
            item.baseStats().forEach((stat, value) -> {
                if (stat != ItemStat.ATTACK_DAMAGE || item.slot() == ItemSlot.MAIN_HAND) {
                    stats.merge(stat, stat.clamp(value), Double::sum);
                }
            });
            if (item.setId() != null) {
                setPieces.merge(item.setId(), 1, Integer::sum);
            }
        }

        List<String> activeSets = new ArrayList<>();
        setPieces.forEach((setId, pieces) -> {
            SetDefinition set = registry.sets().get(setId);
            if (set == null) return;
            boolean active = false;
            set.bonuses().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .filter(entry -> pieces >= entry.getKey())
                    .forEach(entry -> {
                        entry.getValue().stats().forEach((stat, value) ->
                                stats.merge(stat, stat.clamp(value), Double::sum));
                    });
            active = set.bonuses().keySet().stream().anyMatch(threshold -> pieces >= threshold);
            if (active) activeSets.add(setId);
        });
        stats.replaceAll((stat, value) -> stat.clamp(value));

        ItemDefinition mainHand = equipped.stream()
                .filter(item -> item.slot() == ItemSlot.MAIN_HAND)
                .findFirst()
                .orElse(null);
        WeaponType weaponType = mainHand == null ? null : mainHand.weaponType();
        String attackType = weaponType == WeaponType.STAFF || weaponType == WeaponType.WAND
                ? "MAGIC"
                : "PHYSICAL";
        double mastery = mainHand == null
                ? WeaponMastery.FOREIGN
                : WeaponMastery.masteryMultiplier(playerClass, weaponType);

        return new CombatLabEquipmentSnapshot(
                stats,
                equipped.stream().map(ItemDefinition::id).sorted().toList(),
                activeSets.stream().sorted().toList(),
                mainHand == null ? null : mainHand.id(),
                weaponType,
                attackType,
                mastery);
    }

    private static Comparator<ItemDefinition> candidateOrder(PlayerClass playerClass) {
        return Comparator.<ItemDefinition>comparingDouble(
                        item -> item.slot() == ItemSlot.MAIN_HAND
                                ? WeaponMastery.masteryMultiplier(playerClass, item.weaponType())
                                : 1.0)
                .reversed()
                .thenComparing(Comparator.comparingInt(ItemDefinition::itemLevel).reversed())
                .thenComparing(ItemDefinition::id);
    }

    public double value(ItemStat stat) {
        return stats.getOrDefault(stat, 0.0);
    }
}
