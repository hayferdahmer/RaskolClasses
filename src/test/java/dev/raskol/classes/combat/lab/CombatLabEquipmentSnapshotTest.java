// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.config.ItemConfigLoader;
import dev.raskol.classes.item.config.ItemTemplateExpander;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.SetDefinition;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemRegistry;

@DisplayName("Combat Lab: equipment snapshot and duel contracts")
class CombatLabEquipmentSnapshotTest {

    private static ItemRegistry registry;

    @BeforeAll
    static void buildRegistryFromProductionTemplates() {
        List<ItemTemplateExpander.Template> templates = ItemTemplateExpander.starterTemplates().stream()
                .map(template -> new ItemTemplateExpander.Template(
                        template.id(),
                        template.playerClass(),
                        template.slot(),
                        template.weaponType(),
                        template.distribution(),
                        template.names(),
                        template.playerClass().name().toLowerCase(java.util.Locale.ROOT) + "_regalia",
                        template.customModelData()))
                .toList();
        List<ItemDefinition> definitions = ItemTemplateExpander.expand(
                templates, Map.of(), List.of(20, 40, 60), List.of(ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC));
        Map<String, SetDefinition> sets = new java.util.HashMap<>();
        for (PlayerClass playerClass : PlayerClass.values()) {
            String id = playerClass.name().toLowerCase(java.util.Locale.ROOT) + "_regalia";
            sets.put(
                    id,
                    new SetDefinition(
                            id,
                            playerClass.getDisplayName() + " regalia",
                            Map.of(
                                    2,
                                    new SetDefinition.Bonus(Map.of(ItemStat.HP_BONUS, 10.0), "2 предмета"),
                                    4,
                                    new SetDefinition.Bonus(Map.of(ItemStat.HP_BONUS, 20.0), "4 предмета"))));
        }
        registry = new ItemRegistry(definitions, sets);
    }

    @Test
    @DisplayName("экипировка соблюдает порог уровня и выбирает лучший доступный item-level")
    void equipmentUsesLevelRequirements() {
        var belowGearLevel = CombatLabEquipmentSnapshot.from(registry, PlayerClass.WARRIOR, 15, ItemRarity.RARE);
        var levelThirty = CombatLabEquipmentSnapshot.from(registry, PlayerClass.WARRIOR, 30, ItemRarity.RARE);
        var levelSixty = CombatLabEquipmentSnapshot.from(registry, PlayerClass.WARRIOR, 60, ItemRarity.RARE);

        assertTrue(belowGearLevel.equippedItemIds().isEmpty());
        assertEquals(5, levelThirty.equippedItemIds().size());
        assertEquals(5, levelSixty.equippedItemIds().size());
        assertTrue(levelThirty.equippedItemIds().stream().allMatch(id -> id.contains("_20_rare")));
        assertTrue(levelSixty.equippedItemIds().stream().allMatch(id -> id.contains("_60_rare")));
    }

    @Test
    @DisplayName("классовые ограничения не позволяют надеть чужую экипировку")
    void equipmentIsClassLocked() {
        for (PlayerClass playerClass : PlayerClass.values()) {
            var snapshot = CombatLabEquipmentSnapshot.from(registry, playerClass, 60, ItemRarity.RARE);
            assertEquals(5, snapshot.equippedItemIds().size());
            for (String id : snapshot.equippedItemIds()) {
                var definition = registry.get(id);
                assertNotNull(definition);
                assertEquals(playerClass, definition.playerClass());
            }
        }
    }

    @Test
    @DisplayName("выбор оружия детерминирован и учитывает production WeaponMastery")
    void weaponSelectionUsesMastery() {
        var first = CombatLabEquipmentSnapshot.from(registry, PlayerClass.ROGUE, 60, ItemRarity.RARE);
        var second = CombatLabEquipmentSnapshot.from(registry, PlayerClass.ROGUE, 60, ItemRarity.RARE);

        assertEquals(first, second);
        assertEquals(WeaponType.DAGGER, first.weaponType());
        assertEquals(1.0, first.weaponMasteryMultiplier(), 1e-9);
        assertEquals("PHYSICAL", first.autoAttackType());
    }

    @Test
    @DisplayName("предметные статы и активные пороги сетов попадают в снимок")
    void itemStatsAndSetBonusesAreAggregated() {
        var snapshot = CombatLabEquipmentSnapshot.from(registry, PlayerClass.WARRIOR, 60, ItemRarity.RARE);

        assertTrue(snapshot.value(ItemStat.ATTACK_DAMAGE) > 0.0);
        assertTrue(snapshot.value(ItemStat.WEAPON_POWER) > 0.0);
        assertTrue(snapshot.value(ItemStat.HP_BONUS) > 20.0);
        assertTrue(snapshot.activeSetIds().contains("warrior_regalia"));
    }

    @Test
    @DisplayName("магическое оружие сохраняет тип MAGIC")
    void casterWeaponUsesMagicAutoAttack() {
        var snapshot = CombatLabEquipmentSnapshot.from(registry, PlayerClass.WARLOCK, 60, ItemRarity.RARE);

        assertEquals(WeaponType.STAFF, snapshot.weaponType());
        assertEquals("MAGIC", snapshot.autoAttackType());
        assertEquals(0.75, snapshot.weaponMasteryMultiplier(), 1e-9);
    }

    @Test
    @DisplayName("настоящий items.yml загружается через production loader без поднятия Paper сервера")
    void productionItemsYamlLoadsForHeadlessRunner() throws Exception {
        ItemRegistry production = ItemConfigLoader.load(new File("src/main/resources/items.yml"));
        assertTrue(production.size() > 0);
        for (PlayerClass playerClass : PlayerClass.values()) {
            var snapshot = CombatLabEquipmentSnapshot.from(
                    production, playerClass, 60, ItemRarity.EPIC, null, 20261123L);
            assertFalse(snapshot.equippedItemIds().isEmpty(), playerClass + " должен получить экипировку");
            assertNotNull(snapshot.mainHandItemId(), playerClass + " должен получить основное оружие");
        }
    }

    @Test
    @DisplayName("экипировка меняет боевые характеристики и участвует в расчёте урона")
    void equipmentChangesDuelDamage() {
        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        var warriorEquipment = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.WARRIOR, 60, ItemRarity.RARE, null, 12345L);
        var mageEquipment = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.MAGE, 60, ItemRarity.RARE, null, 54321L);
        var equippedWarrior =
                CombatLabCombatantSnapshot.from(config, PlayerClass.WARRIOR, 60, warriorEquipment);
        var equippedMage = CombatLabCombatantSnapshot.from(config, PlayerClass.MAGE, 60, mageEquipment);
        var unarmedWarrior = CombatLabCombatantSnapshot.from(config, PlayerClass.WARRIOR, 60);
        var unarmedMage = CombatLabCombatantSnapshot.from(config, PlayerClass.MAGE, 60);
        var rules = CombatLabDuelEngine.Rules.from(config);

        assertTrue(equippedWarrior.maxHealth() >= unarmedWarrior.maxHealth());
        assertTrue(equippedWarrior.weaponPower() > unarmedWarrior.weaponPower());
        assertTrue(equippedMage.spellPower() > unarmedMage.spellPower());
        var equipped = CombatLabDuelEngine.duelEquipped(equippedWarrior, equippedMage, rules, 777L);
        var repeated = CombatLabDuelEngine.duelEquipped(equippedWarrior, equippedMage, rules, 777L);
        var control = CombatLabDuelEngine.duel(unarmedWarrior, unarmedMage, rules, 777L);
        assertEquals(equipped, repeated);
        assertTrue(Math.abs(equipped.firstDamageDealt() - control.firstDamageDealt()) > 1e-9);
    }
}
