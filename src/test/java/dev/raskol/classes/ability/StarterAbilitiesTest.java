// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.classsystem.PlayerClass;

@DisplayName("StarterAbilities: стартовая способность класса")
class StarterAbilitiesTest {

    @Test
    @DisplayName("каждый класс получает ровно одну закреплённую стартовую способность")
    void everyClassHasOneUniqueStarterAbility() {
        assertEquals("tyr_strike", StarterAbilities.idFor(PlayerClass.WARRIOR));
        assertEquals("wolf_mark", StarterAbilities.idFor(PlayerClass.HUNTER));
        assertEquals("saint_tear", StarterAbilities.idFor(PlayerClass.PRIEST));
        assertEquals("fire_prometheus", StarterAbilities.idFor(PlayerClass.MAGE));
        assertEquals("shadow_cloak", StarterAbilities.idFor(PlayerClass.ROGUE));
        assertEquals("black_word", StarterAbilities.idFor(PlayerClass.WARLOCK));

        Set<String> ids = new HashSet<>();
        for (PlayerClass playerClass : EnumSet.allOf(PlayerClass.class)) {
            assertTrue(ids.add(StarterAbilities.idFor(playerClass)), "стартовая способность не должна дублироваться");
        }
        assertEquals(PlayerClass.values().length, ids.size());
    }

    @Test
    @DisplayName("стартовая способность всегда открыта с первого уровня даже при старом config unlock=10")
    void starterUnlockOverridesLegacyConfiguration() {
        for (PlayerClass playerClass : PlayerClass.values()) {
            String starter = StarterAbilities.idFor(playerClass);
            assertEquals(1, StarterAbilities.unlockLevel(playerClass, starter, 10));
        }
    }

    @Test
    @DisplayName("прочие способности сохраняют настроенный уровень открытия")
    void nonStarterUnlockLevelIsPreserved() {
        assertEquals(25, StarterAbilities.unlockLevel(PlayerClass.WARRIOR, "balder_skin", 25));
        assertEquals(50, StarterAbilities.unlockLevel(PlayerClass.HUNTER, "piercing_shot", 50));
        assertFalse(StarterAbilities.isStarter(PlayerClass.WARRIOR, "wolf_mark"));
    }
}
