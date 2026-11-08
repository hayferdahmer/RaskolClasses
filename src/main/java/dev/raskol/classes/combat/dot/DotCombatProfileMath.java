// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot;

import dev.raskol.classes.api.combat.CombatProfile;
import dev.raskol.classes.combat.DamageType;
import dev.raskol.classes.combat.school.School;

/**
 * Чистая математика защиты CombatProfile для периодического урона.
 */
final class DotCombatProfileMath {

    private DotCombatProfileMath() {}

    /**
     * Возвращает итоговый множитель защиты профиля для одной школы DoT.
     * Значения пробития передаются в процентных пунктах.
     */
    static double multiplier(
            CombatProfile profile, School school, double channelPenPct, double schoolPenPct) {
        if (profile == null || school == null) {
            return 1.0;
        }

        if (profile.immunities().contains(school)) {
            return 0.0;
        }

        double vulnerability =
                1.0 + Math.max(-100.0, profile.vulnerabilities().getOrDefault(school, 0.0)) / 100.0;

        if (school.channel() == DamageType.TRUE) {
            return vulnerability;
        }

        boolean physical = school.channel() == DamageType.PHYSICAL;
        double channelResist = physical ? profile.physResist() : profile.magicResist();
        double schoolResist = profile.schoolResists().getOrDefault(school, 0.0);

        double safeChannelPen = safePenetration(channelPenPct);
        double safeSchoolPen = safePenetration(schoolPenPct);

        double resist = Math.max(0.0, Math.min(90.0, channelResist - safeChannelPen + schoolResist
                - safeSchoolPen));

        return (1.0 - resist / 100.0) * vulnerability;
    }

    private static double safePenetration(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }
}
