// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CharacterExperienceCurveTest {

    @Test
    void defaultCurveKeepsEarlyLevelsFastAndRaisesEndgameRequirements() {
        CharacterExperienceCurve curve = CharacterExperienceCurve.defaults();

        assertEquals(139L, curve.requiredExperience(1));
        assertEquals(593L, curve.requiredExperience(15));
        assertEquals(2_280L, curve.requiredExperience(40));
        assertEquals(4_315L, curve.requiredExperience(59));
    }

    @Test
    void defaultCurveTargetsThePlannedTotalProgressionBudget() {
        CharacterExperienceCurve curve = CharacterExperienceCurve.defaults();

        assertEquals(4_484L, curve.totalExperienceToLevel(15));
        assertEquals(39_488L, curve.totalExperienceToLevel(40));
        assertEquals(102_132L, curve.totalExperienceToLevel(60));
    }

    @Test
    void rejectsLevelsWithoutAValidNextLevelThreshold() {
        CharacterExperienceCurve curve = CharacterExperienceCurve.defaults();

        assertThrows(IllegalArgumentException.class, () -> curve.requiredExperience(0));
        assertThrows(IllegalArgumentException.class, () -> curve.requiredExperience(60));
    }

    @Test
    void rejectsNegativeCurveCoefficients() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CharacterExperienceCurve(-1, 18, 9));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CharacterExperienceCurve(120, -1, 9));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CharacterExperienceCurve(120, 18, -1));
    }
}
