// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pet talent combat math")
class PetTalentMathTest {

    @Test
    @DisplayName("chance is finite and clamped to the valid probability range")
    void chanceIsClamped() {
        assertEquals(0.0, PetTalentMath.chance(-5.0), 0.0001);
        assertEquals(25.0, PetTalentMath.chance(25.0), 0.0001);
        assertEquals(100.0, PetTalentMath.chance(150.0), 0.0001);
        assertEquals(0.0, PetTalentMath.chance(Double.NaN), 0.0001);
    }

    @Test
    @DisplayName("pet physical rank bonus and critical talent compose once")
    void damageMultipliersCompose() {
        assertEquals(1.20, PetTalentMath.outgoingDamageMultiplier(20.0, false, false), 0.0001);
        assertEquals(1.80, PetTalentMath.outgoingDamageMultiplier(20.0, true, false), 0.0001);
        assertEquals(2.40, PetTalentMath.outgoingDamageMultiplier(20.0, true, true), 0.0001);
        assertEquals(1.00, PetTalentMath.outgoingDamageMultiplier(Double.NaN, false, false), 0.0001);
    }
}
