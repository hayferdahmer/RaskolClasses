// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.character.profile.CharacterProfile;
import dev.raskol.classes.character.profile.CharacterProfileStorage;
import dev.raskol.classes.storage.AsyncStorageWriter;

class CharacterExperienceServiceTest {

    private static final Logger LOGGER = Logger.getLogger("CharacterExperienceServiceTest");

    @TempDir
    Path tempDir;

    @Test
    void awardsExperienceAcrossMultipleLevelsAndUpdatesTheProfile() {
        UUID playerId = UUID.fromString("a7d8a7d8-7d8a-4a7d-8d7a-a7d8a7d8a7d8");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterProgressionEngine.AwardResult result = service.award(playerId, 500L);

            assertEquals(new CharacterProgressionEngine.State(4, 19L), result.state());
            assertEquals(3, result.levelsGained());
            assertEquals(0L, result.unspentExperience());
            CharacterProfile profile = storage.find(playerId).orElseThrow();
            assertEquals(4, profile.level());
            assertEquals(19L, profile.experienceIntoLevel());
            org.junit.jupiter.api.Assertions.assertTrue(profile.updatedAtMillis() >= profile.createdAtMillis());
        }
    }

    @Test
    void reportsUnspentExperienceAfterReachingLevelCap() {
        UUID playerId = UUID.fromString("b8e9b8e9-8e9b-4b8e-9e8b-b8e9b8e9b8e9");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            storage.upsert(new CharacterProfile(playerId, 1, 59, 0L, 100L, 100L));
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterProgressionEngine.AwardResult result = service.award(playerId, 5_000L);

            assertEquals(new CharacterProgressionEngine.State(60, 0L), result.state());
            assertEquals(1, result.levelsGained());
            assertEquals(685L, result.unspentExperience());
            assertEquals(60, storage.find(playerId).orElseThrow().level());
        }
    }

    @Test
    void exposesRemainingExperienceForHudAndNoNextLevelAtCap() {
        UUID playerId = UUID.fromString("eb0beb0b-0beb-4eb0-8beb-eb0beb0beb0b");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            service.award(playerId, 500L);
            CharacterExperienceService.ProgressSnapshot snapshot = service.snapshot(playerId);

            assertEquals(4, snapshot.level());
            assertEquals(19L, snapshot.currentExperience());
            assertEquals(187L, snapshot.experienceToNextLevel().orElseThrow());

            storage.upsert(new CharacterProfile(playerId, 1, 60, 0L, 100L, 100L));
            CharacterExperienceService.ProgressSnapshot capped = service.snapshot(playerId);
            assertEquals(60, capped.level());
            assertEquals(0L, capped.currentExperience());
            org.junit.jupiter.api.Assertions.assertTrue(
                    capped.experienceToNextLevel().isEmpty());
        }
    }

    @Test
    void zeroAwardDoesNotMutateAnExistingProfile() {
        UUID playerId = UUID.fromString("c9fac9fa-9fac-4c9f-ac9f-c9fac9fac9fa");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterProfile original = storage.ensureProfile(playerId);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterProgressionEngine.AwardResult result = service.award(playerId, 0L);

            assertEquals(new CharacterProgressionEngine.State(1, 0L), result.state());
            assertEquals(original, storage.find(playerId).orElseThrow());
        }
    }

    @Test
    void rejectsNegativeAwardsWithoutChangingTheProfile() {
        UUID playerId = UUID.fromString("dafb dafb-dafb-4daf-8daf-dafb dafb dafb".replace(" ", ""));
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterProfile original = storage.ensureProfile(playerId);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            assertThrows(IllegalArgumentException.class, () -> service.award(playerId, -1L));
            assertEquals(original, storage.find(playerId).orElseThrow());
        }
    }
}
