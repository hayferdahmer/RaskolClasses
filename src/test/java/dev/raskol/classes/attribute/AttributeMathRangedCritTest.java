// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ranged critical chance math")
class AttributeMathRangedCritTest {

    @Test
    @DisplayName("ranged chance uses its own base, agility coefficient and cap")
    void rangedChanceUsesDedicatedFormula() {
        assertEquals(10.0, AttributeMath.critRanged(100.0, 5.0, 0.05, 40.0), 0.0001);
        assertEquals(8.0, AttributeMath.critRanged(100.0, 5.0, 0.05, 8.0), 0.0001);
    }

    @Test
    @DisplayName("invalid ranged chance inputs are safely neutralized")
    void invalidInputsAreNeutralized() {
        assertEquals(5.0, AttributeMath.critRanged(Double.NaN, 5.0, 0.05, 40.0), 0.0001);
        assertEquals(5.0, AttributeMath.critRanged(100.0, 5.0, Double.NaN, 40.0), 0.0001);
        assertEquals(0.0, AttributeMath.critRanged(100.0, Double.NaN, 0.05, 40.0), 0.0001);
    }
}
