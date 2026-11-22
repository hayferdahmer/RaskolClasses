// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CharacterExperienceSpawnPolicyTest {

    @Test
    void acceptsOnlyApprovedNaturalAndWorldEncounterSpawnReasons() {
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "NATURAL"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("SKELETON", "CHUNK_GEN"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("PILLAGER", "PATROL"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("RAVAGER", "RAID"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "REINFORCEMENTS"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("IRON_GOLEM", "VILLAGE_DEFENSE"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("VINDICATOR", "VILLAGE_INVASION"));
    }

    @Test
    void rejectsSpawnerEggCommandAndOtherFarmableSpawnReasons() {
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "SPAWNER"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "SPAWNER_EGG"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "DISPENSE_EGG"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "COMMAND"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "CUSTOM"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("COW", "BREEDING"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("SLIME", "SLIME_SPLIT"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "TRIAL_SPAWNER"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn(null, "NATURAL"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", null));
    }

    @Test
    void allowsBossExceptionsOnlyForTheirMatchingSpawnReason() {
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("WITHER", "BUILD_WITHER"));
        assertTrue(CharacterExperienceSpawnPolicy.isEligibleSpawn("ENDER_DRAGON", "DEFAULT"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ZOMBIE", "BUILD_WITHER"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("WITHER", "DEFAULT"));
        assertFalse(CharacterExperienceSpawnPolicy.isEligibleSpawn("ENDER_DRAGON", "COMMAND"));
    }

    @Test
    void awardsOnlyForMarkedNonPlayerEntitiesKilledByAPlayerWithPositiveReward() {
        assertTrue(CharacterExperienceSpawnPolicy.shouldAwardExperience(false, true, true, 8L));
        assertFalse(CharacterExperienceSpawnPolicy.shouldAwardExperience(true, true, true, 8L));
        assertFalse(CharacterExperienceSpawnPolicy.shouldAwardExperience(false, false, true, 8L));
        assertFalse(CharacterExperienceSpawnPolicy.shouldAwardExperience(false, true, false, 8L));
        assertFalse(CharacterExperienceSpawnPolicy.shouldAwardExperience(false, true, true, 0L));
        assertFalse(CharacterExperienceSpawnPolicy.shouldAwardExperience(false, true, true, -1L));
    }
}
