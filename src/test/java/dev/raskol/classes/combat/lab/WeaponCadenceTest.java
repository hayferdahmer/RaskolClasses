// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.WeaponMastery;
import dev.raskol.classes.item.model.WeaponType;

@DisplayName("Combat Lab: runtime weapon cadence")
class WeaponCadenceTest {

    @Test
    @DisplayName("безоружный интервал соответствует базовым 4 атакам в секунду")
    void unarmedUsesPlayerDefaultAttribute() {
        WeaponCadence.Profile profile = WeaponCadence.unarmed();
        assertEquals(0.25, profile.intervalSeconds(), 1.0e-9);
        assertEquals("PLAYER_UNARMED_DEFAULT_4_ATTACKS_PER_SECOND", profile.modelId());
    }

    @Test
    @DisplayName("полный заряд лука и перезарядка арбалета моделируются отдельно от melee attribute")
    void rangedCadenceUsesChargeAndReloadTime() {
        assertEquals(1.0, WeaponCadence.forWeapon(WeaponType.BOW, "BOW", 50.0).intervalSeconds(), 1.0e-9);
        assertEquals(
                1.25,
                WeaponCadence.forWeapon(WeaponType.CROSSBOW, "CROSSBOW", 50.0).intervalSeconds(),
                1.0e-9);
    }

    @Test
    @DisplayName("материальные скорости Paper 26.2 задают корректный интервал")
    void materialProfilesMatchVanillaAttackSpeed() {
        assertEquals(
                0.625,
                WeaponCadence.forWeapon(WeaponType.SWORD, "IRON_SWORD", 0.0).intervalSeconds(),
                1.0e-9);
        assertEquals(
                1.0 / 0.9,
                WeaponCadence.forWeapon(WeaponType.AXE, "IRON_AXE", 0.0).intervalSeconds(),
                1.0e-9);
        assertEquals(
                1.0 / 0.6, WeaponCadence.forWeapon(WeaponType.MACE, "MACE", 0.0).intervalSeconds(), 1.0e-9);
        assertEquals(
                1.0 / 1.1,
                WeaponCadence.forWeapon(WeaponType.SPEAR, "TRIDENT", 0.0).intervalSeconds(),
                1.0e-9);
        assertEquals(
                0.25,
                WeaponCadence.forWeapon(WeaponType.STAFF, "BLAZE_ROD", 0.0).intervalSeconds(),
                1.0e-9);
    }

    @Test
    @DisplayName("бонус ATTACK_SPEED умножает итоговую скорость материала")
    void itemSpeedBonusMultipliesMaterialAttackSpeed() {
        WeaponCadence.Profile normal = WeaponCadence.forWeapon(WeaponType.SWORD, "IRON_SWORD", 0.0);
        WeaponCadence.Profile boosted = WeaponCadence.forWeapon(WeaponType.SWORD, "IRON_SWORD", 25.0);
        assertEquals(normal.intervalSeconds() / 1.25, boosted.intervalSeconds(), 1.0e-9);
        assertEquals("PAPER_26_2_MATERIAL_ATTACK_SPEED", normal.modelId());
    }

    @Test
    @DisplayName("копьё является родным оружием охотника и чужим для остальных классов")
    void spearMasteryIsExplicit() {
        assertEquals(WeaponMastery.NATIVE, WeaponMastery.masteryMultiplier(PlayerClass.HUNTER, WeaponType.SPEAR));
        for (PlayerClass playerClass : PlayerClass.values()) {
            if (playerClass != PlayerClass.HUNTER) {
                assertEquals(WeaponMastery.FOREIGN, WeaponMastery.masteryMultiplier(playerClass, WeaponType.SPEAR));
            }
        }
    }
}
