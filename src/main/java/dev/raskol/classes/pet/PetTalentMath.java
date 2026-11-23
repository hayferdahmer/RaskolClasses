// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet;

/** Чистая математика ранговых боевых талантов питомца. */
final class PetTalentMath {

    private PetTalentMath() {}

    static double chance(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
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
