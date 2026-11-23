// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.classsystem.PlayerClass;

@DisplayName("Combat Lab: headless unarmed probe")
class CombatLabDuelEngineTest {

    private static YamlConfiguration config;

    @BeforeAll
    static void loadProductionConfig() {
        config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    }

    @Test
    @DisplayName("снимки шести классов строятся на пяти контрольных уровнях")
    void snapshotsCoverAllClassesAndMilestones() {
        for (PlayerClass playerClass : PlayerClass.values()) {
            for (int level : new int[] {1, 15, 30, 45, 60}) {
                CombatLabCombatantSnapshot snapshot =
                        CombatLabCombatantSnapshot.from(config, playerClass, level);
                assertTrue(Double.isFinite(snapshot.maxHealth()));
                assertTrue(snapshot.maxHealth() > 0.0);
                assertTrue(Double.isFinite(snapshot.weaponPower()));
                assertTrue(Double.isFinite(snapshot.spellPower()));
                assertTrue(Double.isFinite(snapshot.healPower()));
                assertTrue(snapshot.dodgeChancePct() >= 0.0);
                assertTrue(snapshot.parryChancePct() >= 0.0);
            }
        }
    }

    @Test
    @DisplayName("HP снимка совпадает с production AttributeMath")
    void healthUsesProductionFormula() {
        CombatLabCombatantSnapshot warrior =
                CombatLabCombatantSnapshot.from(config, PlayerClass.WARRIOR, 30);
        double expected = AttributeMath.maxHp(
                warrior.strength(),
                30,
                true,
                config.getDouble("attributes.hp.base-hp"),
                config.getDouble("attributes.hp.per-str"),
                config.getDouble("attributes.hp.per-level"),
                config.getDouble("attributes.hp.main-str-bonus"));
        assertEquals(expected, warrior.maxHealth(), 1e-9);
    }

    @Test
    @DisplayName("одинаковые входные данные и seed дают одинаковую дуэль")
    void duelIsDeterministic() {
        CombatLabCombatantSnapshot warrior =
                CombatLabCombatantSnapshot.from(config, PlayerClass.WARRIOR, 30);
        CombatLabCombatantSnapshot mage =
                CombatLabCombatantSnapshot.from(config, PlayerClass.MAGE, 30);
        CombatLabDuelEngine.Rules rules = CombatLabDuelEngine.Rules.from(config);
        CombatLabDuelEngine.DuelResult first =
                CombatLabDuelEngine.duel(warrior, mage, rules, 42L);
        CombatLabDuelEngine.DuelResult second =
                CombatLabDuelEngine.duel(warrior, mage, rules, 42L);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("бой возвращает конечные значения и не превышает лимит времени")
    void duelReturnsBoundedResult() {
        CombatLabCombatantSnapshot warrior =
                CombatLabCombatantSnapshot.from(config, PlayerClass.WARRIOR, 60);
        CombatLabCombatantSnapshot priest =
                CombatLabCombatantSnapshot.from(config, PlayerClass.PRIEST, 60);
        CombatLabDuelEngine.DuelResult result = CombatLabDuelEngine.duel(
                warrior, priest, CombatLabDuelEngine.Rules.from(config), 20261123L);
        assertTrue(Double.isFinite(result.durationSeconds()));
        assertTrue(result.durationSeconds() > 0.0);
        assertTrue(result.durationSeconds() <= 60.0);
        assertTrue(result.firstDamageDealt() >= 0.0);
        assertTrue(result.secondDamageDealt() >= 0.0);
        assertTrue(result.firstHealing() >= 0.0);
        assertTrue(result.secondHealing() >= 0.0);
        assertTrue(result.firstHits() > 0);
        assertTrue(result.secondHits() > 0);
    }

    @Test
    @DisplayName("уровень вне диапазона отклоняется")
    void invalidLevelIsRejected() {
        boolean thrown = false;
        try {
            CombatLabCombatantSnapshot.from(config, PlayerClass.WARRIOR, 0);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    @DisplayName("профиль явно помечен как ограниченный unarmed probe")
    void profileIsNotTheFullCombatLab() {
        assertEquals("pvp-unarmed-melee-probe-v1", CombatLabDuelEngine.PROFILE_ID);
        assertNotNull(CombatLabDuelEngine.Rules.from(config));
    }
}
