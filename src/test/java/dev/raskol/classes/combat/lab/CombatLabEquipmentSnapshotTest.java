// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.config.ItemConfigLoader;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemRegistry;

@DisplayName("Combat Lab: production equipment snapshots")
class CombatLabEquipmentSnapshotTest {

    private static ItemRegistry registry;

    @BeforeAll
    static void loadProductionItems() throws Exception {
        registry = ItemConfigLoader.load(new File("src/main/resources/items.yml"));
    }

    @Test
    @DisplayName("экипировка использует production items.yml и соблюдает порог уровня")
    void equipmentUsesProductionDefinitionsAndLevelRequirements() {
        var belowGearLevel = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.WARRIOR, 15, ItemRarity.RARE);
        var levelThirty = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.WARRIOR, 30, ItemRarity.RARE);
        var levelSixty = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.WARRIOR, 60, ItemRarity.RARE);

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
            assertFalse(snapshot.equippedItemIds().isEmpty());
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
        var first = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.ROGUE, 60, ItemRarity.RARE);
        var second = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.ROGUE, 60, ItemRarity.RARE);

        assertEquals(first, second);
        assertEquals(WeaponType.DAGGER, first.weaponType());
        assertEquals(1.0, first.weaponMasteryMultiplier(), 1e-9);
        assertEquals("PHYSICAL", first.autoAttackType());
    }

    @Test
    @DisplayName("предметные статы и активные пороги сетов попадают в снимок")
    void itemStatsAndSetBonusesAreAggregated() {
        var snapshot = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.WARRIOR, 60, ItemRarity.RARE);

        assertTrue(snapshot.value(ItemStat.ATTACK_DAMAGE) > 0.0);
        assertTrue(snapshot.value(ItemStat.WEAPON_POWER) > 0.0);
        assertTrue(snapshot.value(ItemStat.HP_BONUS) > 0.0);
        assertFalse(snapshot.activeSetIds().isEmpty());
        assertTrue(snapshot.activeSetIds().contains("warrior_regalia"));
    }

    @Test
    @DisplayName("магическое оружие сохраняет тип MAGIC и не получает физический mastery")
    void casterWeaponUsesMagicAutoAttack() {
        var snapshot = CombatLabEquipmentSnapshot.from(
                registry, PlayerClass.WARLOCK, 60, ItemRarity.RARE);

        assertEquals(WeaponType.WAND, snapshot.weaponType());
        assertEquals("MAGIC", snapshot.autoAttackType());
        assertEquals(1.0, snapshot.weaponMasteryMultiplier(), 1e-9);
    }
}
