// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.WeaponMastery;
import dev.raskol.classes.item.factory.AffixRoller;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.SetDefinition;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemRegistry;
import dev.raskol.classes.item.service.ItemStatAggregator;
import dev.raskol.classes.item.service.SetBonusService;

/** Неизменяемый headless-снимок экипировки, собранный из production ItemRegistry. */
public record CombatLabEquipmentSnapshot(
        Map<ItemStat, Double> stats,
        List<String> equippedItemIds,
        List<String> activeSetIds,
        String mainHandItemId,
        WeaponType weaponType,
        String autoAttackType,
        double weaponMasteryMultiplier) {

    public static final CombatLabEquipmentSnapshot EMPTY = new CombatLabEquipmentSnapshot(
            Map.of(), List.of(), List.of(), null, null, "PHYSICAL", 1.0);

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
        return from(registry, playerClass, level, rarity, null, 0L);
    }

    /** Выбирает предметы с учётом класса/специализации и использует production affix/set aggregators. */
    public static CombatLabEquipmentSnapshot from(
            ItemRegistry registry,
            PlayerClass playerClass,
            int level,
            ItemRarity rarity,
            String spec,
            long seed) {
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
                    .filter(item -> ItemStatAggregator.eligible(item, playerClass, spec, level))
                    .min(candidateOrder(playerClass))
                    .ifPresent(equipped::add);
        }

        List<Map<ItemStat, Double>> rolled = new ArrayList<>();
        for (ItemDefinition item : equipped) {
            long itemSeed = seed ^ (0x9E3779B97F4A7C15L * item.id().hashCode());
            rolled.add(AffixRoller.roll(item.affixPool(), item.affixCount(), item.itemLevel(), itemSeed));
        }

        Map<ItemStat, Double> aggregated = new EnumMap<>(ItemStat.class);
        aggregated.putAll(ItemStatAggregator.aggregate(equipped, rolled, playerClass, spec, level));
        // Runtime ItemHook принимает ATTACK_DAMAGE только с MAIN_HAND; сохраняем тот же контракт.
        double mainHandDamage = 0.0;
        for (int i = 0; i < equipped.size(); i++) {
            ItemDefinition item = equipped.get(i);
            if (item.slot() == ItemSlot.MAIN_HAND) {
                mainHandDamage += item.baseStats().getOrDefault(ItemStat.ATTACK_DAMAGE, 0.0);
                mainHandDamage += rolled.get(i).getOrDefault(ItemStat.ATTACK_DAMAGE, 0.0);
            }
        }
        if (mainHandDamage > 0.0) {
            aggregated.put(ItemStat.ATTACK_DAMAGE, ItemStat.ATTACK_DAMAGE.clamp(mainHandDamage));
        } else {
            aggregated.remove(ItemStat.ATTACK_DAMAGE);
        }

        SetBonusService setBonusService = new SetBonusService();
        setBonusService.definitions(registry.sets());
        UUID snapshotId = UUID.nameUUIDFromBytes(
                (playerClass.name() + ":" + level + ":" + spec + ":" + seed).getBytes(StandardCharsets.UTF_8));
        Map<ItemStat, Double> stats = setBonusService.apply(snapshotId, equipped, aggregated);
        stats.replaceAll((stat, value) -> stat.clamp(value));

        List<String> activeSets = setBonusService.active(snapshotId).stream()
                .filter(active -> !active.activeThresholds().isEmpty())
                .map(SetBonusService.ActiveSet::id)
                .sorted()
                .toList();

        ItemDefinition mainHand = equipped.stream()
                .filter(item -> item.slot() == ItemSlot.MAIN_HAND)
                .findFirst()
                .orElse(null);
        WeaponType weaponType = mainHand == null ? null : mainHand.weaponType();
        String attackType = weaponType == WeaponType.STAFF || weaponType == WeaponType.WAND ? "MAGIC" : "PHYSICAL";
        double mastery = mainHand == null
                ? WeaponMastery.FOREIGN
                : WeaponMastery.masteryMultiplier(playerClass, weaponType);

        return new CombatLabEquipmentSnapshot(
                stats,
                equipped.stream().map(ItemDefinition::id).sorted().toList(),
                activeSets,
                mainHand == null ? null : mainHand.id(),
                weaponType,
                attackType,
                mastery);
    }

    private static Comparator<ItemDefinition> candidateOrder(PlayerClass playerClass) {
        return Comparator.<ItemDefinition>comparingDouble(item -> item.slot() == ItemSlot.MAIN_HAND
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
