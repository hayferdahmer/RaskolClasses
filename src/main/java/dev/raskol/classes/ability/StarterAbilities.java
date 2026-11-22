// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import java.util.Map;

import dev.raskol.classes.classsystem.PlayerClass;

/** Каноническое соответствие стартовой способности выбранному классу. */
public final class StarterAbilities {

    private static final Map<PlayerClass, String> STARTERS = Map.of(
            PlayerClass.WARRIOR, "tyr_strike",
            PlayerClass.HUNTER, "wolf_mark",
            PlayerClass.PRIEST, "saint_tear",
            PlayerClass.MAGE, "fire_prometheus",
            PlayerClass.ROGUE, "shadow_cloak",
            PlayerClass.WARLOCK, "black_word");

    private StarterAbilities() {}

    /** Возвращает единственную стартовую способность класса. */
    public static String idFor(PlayerClass playerClass) {
        String id = STARTERS.get(playerClass);
        if (id == null) {
            throw new IllegalArgumentException("Неизвестный класс для стартовой способности: " + playerClass);
        }
        return id;
    }

    public static boolean isStarter(PlayerClass playerClass, String abilityId) {
        return playerClass != null && abilityId != null && abilityId.equals(STARTERS.get(playerClass));
    }

    /** Старые config.yml не должны возвращать стартовой способности требование 10-го уровня. */
    public static int unlockLevel(PlayerClass playerClass, String abilityId, int configuredLevel) {
        return isStarter(playerClass, abilityId) ? 1 : configuredLevel;
    }
}
