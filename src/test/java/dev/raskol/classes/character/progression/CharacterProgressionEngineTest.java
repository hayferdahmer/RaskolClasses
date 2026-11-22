// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CharacterProgressionEngineTest {

    @Test
    void carriesRemainderAcrossMultipleLevels() {
        CharacterProgressionEngine.AwardResult result = CharacterProgressionEngine.award(
                new CharacterProgressionEngine.State(1, 80), 250, level -> level * 100L);

        assertEquals(new CharacterProgressionEngine.State(3, 30), result.state());
        assertEquals(2, result.levelsGained());
        assertEquals(0, result.unspentExperience());
    }

    @Test
    void returnsExperienceRemainingAfterReachingMaximumLevel() {
        CharacterProgressionEngine.AwardResult result = CharacterProgressionEngine.award(
                new CharacterProgressionEngine.State(59, 80), 50, level -> 100L);

        assertEquals(new CharacterProgressionEngine.State(60, 0), result.state());
        assertEquals(1, result.levelsGained());
        assertEquals(30, result.unspentExperience());
    }

    @Test
    void doesNotAdvanceBeyondMaximumLevel() {
        CharacterProgressionEngine.State maximum =
                new CharacterProgressionEngine.State(CharacterProgressionEngine.MAX_LEVEL, 0);

        CharacterProgressionEngine.AwardResult result =
                CharacterProgressionEngine.award(maximum, 75, level -> 100L);

        assertEquals(maximum, result.state());
        assertEquals(0, result.levelsGained());
        assertEquals(75, result.unspentExperience());
    }

    @Test
    void rejectsNegativeExperience() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterProgressionEngine.award(
                        new CharacterProgressionEngine.State(1, 0), -1, level -> 100L));
    }

    @Test
    void rejectsNonPositiveLevelThreshold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterProgressionEngine.award(
                        new CharacterProgressionEngine.State(1, 0), 100, level -> 0));
    }

    @Test
    void rejectsInvalidInitialState() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterProgressionEngine.State(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProgressionEngine.State(61, 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProgressionEngine.State(60, 1));
    }
}
