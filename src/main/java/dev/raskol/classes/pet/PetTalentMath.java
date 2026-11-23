// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet;

/** Чистая математика ранговых боевых талантов питомца. */
final class PetTalentMath {

    private PetTalentMath() {}

    static double chance(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }

    /** Конвертирует proc-шанс Spec2 из доли 0..1 в проценты для rollPercent. */
    static double chancePercentFromFraction(double fraction) {
        return Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) * 100.0 : 0.0;
    }

    /** Базовый урон сущности не содержит talent-проценты: они применяются в событии попадания. */
    static double runtimeBaseDamage(double baseDamage, double power, double buffMultiplier) {
        return PetMath.damage(baseDamage, power, 0.0, buffMultiplier);
    }

    static double outgoingDamage(double baseDamage, double physicalBonusPct, boolean critical, boolean empoweredCritical) {
        double safeBase = Double.isFinite(baseDamage) ? Math.max(0.0, baseDamage) : 0.0;
        return safeBase * outgoingDamageMultiplier(physicalBonusPct, critical, empoweredCritical);
    }

    static double outgoingDamageMultiplier(double physicalBonusPct, boolean critical, boolean empoweredCritical) {
        double bonus = Double.isFinite(physicalBonusPct) ? Math.max(0.0, Math.min(300.0, physicalBonusPct)) : 0.0;
        double multiplier = 1.0 + bonus / 100.0;
        if (critical) {
            multiplier *= empoweredCritical ? 2.0 : 1.5;
        }
        return multiplier;
    }
}
