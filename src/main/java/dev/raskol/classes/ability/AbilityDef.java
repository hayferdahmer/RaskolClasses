// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability;

import dev.raskol.classes.combat.school.School;

/**
 * Immutable-описание способности. Числа приходят из config.yml,
 * слот = порядковый номер (1-based). Длительности эффектов (D1/D2)
 * читаются способностями напрямую из конфига через RaskolConfig,
 * а не хранятся здесь.
 * 1.12.3: поле school — природа урона способности (для иммунитетов/множителей
 *         школ в пути B; deriveSchool-фолбэк — PHYSICAL/MAGIC-канал по профилю).
 */
public record AbilityDef(
        String id,
        String displayName,
        int slot,
        int unlockLevel,
        int cost,
        long cooldownMillis,
        School school
) {
    public AbilityDef(String id, String displayName, int slot,
                      int unlockLevel, int cost, long cooldownMillis) {
        this(id, displayName, slot, unlockLevel, cost, cooldownMillis, null);
    }
}
