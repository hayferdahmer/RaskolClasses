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
        double weaponMasteryMultiplier,
        double attackIntervalSeconds,
        String cadenceModel) {

    public static final CombatLabEquipmentSnapshot EMPTY = new CombatLabEquipmentSnapshot(
            Map.of(), List.of(), List.of(), null, null, "PHYSICAL", 1.0, 0.25, "PLAYER_UNARMED_DEFAULT");

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
        if (!Double.isFinite(attackIntervalSeconds)
                || attackIntervalSeconds <= 0.0
                || cadenceModel == null
                || cadenceModel.isBlank()) {
            throw new IllegalArgumentException("Некорректная модель интервала автоатаки");
        }
    }

    public static CombatLabEquipmentSnapshot from(
            ItemRegistry registry, PlayerClass playerClass, int level, ItemRarity rarity) {
        return from(registry, playerClass, level, rarity, null, 0L);
    }

    /** Выбирает предметы с учётом класса/специализации и использует production affix/set aggregators. */
    public static CombatLabEquipmentSnapshot from(
            ItemRegistry registry, PlayerClass playerClass, int level, ItemRarity rarity, String spec, long seed) {
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
                    .min(candidateOrder(playerClass, spec))
                    .map(item -> scaleToCharacterLevel(item, level))
                    .ifPresent(equipped::add);
        }

        List<Map<ItemStat, Double>> rolled = new ArrayList<>();
        for (ItemDefinition item : equipped) {
            long itemSeed = seed ^ (0x9E3779B97F4A7C15L * item.id().hashCode());
            rolled.add(AffixRoller.roll(item.affixPool(), item.affixCount(), item.itemLevel(), itemSeed));
        }

        // Повторяем аккумуляцию ItemHook: базовые статы + affix-rolls,
        // ATTACK_DAMAGE учитывается только для MAIN_HAND.
        Map<ItemStat, Double> aggregated = new EnumMap<>(ItemStat.class);
        for (int i = 0; i < equipped.size(); i++) {
            ItemDefinition item = equipped.get(i);
            boolean weaponSlot = item.slot() == ItemSlot.MAIN_HAND;
            item.baseStats().forEach((stat, value) -> {
                if (stat != ItemStat.ATTACK_DAMAGE || weaponSlot) aggregated.merge(stat, value, Double::sum);
            });
            rolled.get(i).forEach((stat, value) -> {
                if (stat != ItemStat.ATTACK_DAMAGE || weaponSlot) aggregated.merge(stat, value, Double::sum);
            });
        }

        SetBonusService setBonusService = new SetBonusService();
        setBonusService.definitions(registry.sets());
        UUID snapshotId = UUID.nameUUIDFromBytes(
                (playerClass.name() + ":" + level + ":" + spec + ":" + seed).getBytes(StandardCharsets.UTF_8));
        Map<ItemStat, Double> stats = setBonusService.apply(snapshotId, equipped, aggregated);
        stats.replaceAll((stat, value) -> Double.isFinite(value) ? value : 0.0);

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
        double mastery =
                mainHand == null ? WeaponMastery.FOREIGN : WeaponMastery.masteryMultiplier(playerClass, weaponType);
        WeaponCadence.Profile cadence = mainHand == null
                ? WeaponCadence.unarmed()
                : WeaponCadence.forWeapon(
                        weaponType, mainHand.material(), stats.getOrDefault(ItemStat.ATTACK_SPEED, 0.0));

        return new CombatLabEquipmentSnapshot(
                stats,
                equipped.stream().map(ItemDefinition::id).sorted().toList(),
                activeSets,
                mainHand == null ? null : mainHand.id(),
                weaponType,
                attackType,
                mastery,
                cadence.intervalSeconds(),
                cadence.modelId());
    }

    private static ItemDefinition scaleToCharacterLevel(ItemDefinition definition, int characterLevel) {
        if (definition.itemLevel() == characterLevel) return definition;
        double scale = (double) characterLevel / definition.itemLevel();
        Map<ItemStat, Double> scaledStats = new EnumMap<>(ItemStat.class);
        definition.baseStats().forEach((stat, value) -> scaledStats.put(stat, stat.clamp(value * scale)));
        return new ItemDefinition(
                definition.id(),
                definition.displayName(),
                definition.material(),
                definition.slot(),
                definition.weaponType(),
                definition.rarity(),
                characterLevel,
                definition.playerClass(),
                definition.spec(),
                definition.levelRequirement(),
                scaledStats,
                definition.affixPool(),
                definition.affixCount(),
                definition.setId(),
                definition.lore(),
                definition.glow(),
                definition.customModelData(),
                definition.template());
    }

    private static Comparator<ItemDefinition> candidateOrder(PlayerClass playerClass, String spec) {
        return Comparator.<ItemDefinition>comparingInt(item ->
                        spec != null && spec.equalsIgnoreCase(item.spec()) ? 0 : 1)
                .thenComparing(
                        Comparator.<ItemDefinition>comparingDouble(item -> item.slot() == ItemSlot.MAIN_HAND
                                        ? WeaponMastery.masteryMultiplier(playerClass, item.weaponType())
                                        : 1.0)
                                .reversed())
                .thenComparing(Comparator.comparingInt(ItemDefinition::itemLevel).reversed())
                .thenComparing(ItemDefinition::id);
    }

    public double value(ItemStat stat) {
        return stats.getOrDefault(stat, 0.0);
    }
}
