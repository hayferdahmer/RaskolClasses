// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    void snapshotReconcilesProgressWhenTheConfiguredCurveChanges() {
        UUID playerId = UUID.fromString("fc1cfc1c-1cfc-4fc1-8c1c-fc1cfc1cfc1c");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            storage.upsert(new CharacterProfile(playerId, 1, 1, 500L, 100L, 100L));
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterExperienceService.ProgressSnapshot snapshot = service.snapshot(playerId);

            assertEquals(4, snapshot.level());
            assertEquals(19L, snapshot.currentExperience());
            assertEquals(4, storage.find(playerId).orElseThrow().level());
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

    @Test
    void setsCharacterLevelAndProgressWithinTheConfiguredCurve() {
        UUID playerId = UUID.fromString("ed1ced1c-ed1c-4ed1-8ed1-ed1ced1ced1c");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterExperienceService.ProgressSnapshot result = service.set(playerId, 15, 100L);

            assertEquals(15, result.level());
            assertEquals(100L, result.currentExperience());
            assertEquals(493L, result.experienceToNextLevel().orElseThrow());
            assertEquals(15, storage.find(playerId).orElseThrow().level());
        }
    }

    @Test
    void rejectsInvalidLevelProgressAndMaxLevelExperience() {
        UUID playerId = UUID.fromString("f12df12d-f12d-4f12-8df1-f12df12df12d");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            assertThrows(IllegalArgumentException.class, () -> service.set(playerId, 0, 0L));
            assertThrows(IllegalArgumentException.class, () -> service.set(playerId, 61, 0L));
            assertThrows(IllegalArgumentException.class, () -> service.set(playerId, 15, -1L));
            assertThrows(IllegalArgumentException.class, () -> service.set(playerId, 15, 593L));
            assertThrows(IllegalArgumentException.class, () -> service.set(playerId, 60, 1L));
        }
    }

    @Test
    void awardsQuestCompletionOnlyOnceEvenWhenTheSameEventIsDeliveredAgain() {
        UUID playerId = UUID.fromString("9c2d9c2d-2d9c-4c2d-8d9c-2d9c2d9c2d9c");
        UUID completionId = UUID.fromString("8b1e8b1e-1e8b-4b1e-8b1e-1e8b1e8b1e8b");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterExperienceService.QuestAwardResult first =
                    service.awardQuestCompletion(playerId, "crypt-depths", completionId, 500L);
            CharacterProfile afterFirst = storage.find(playerId).orElseThrow();
            CharacterExperienceService.QuestAwardResult duplicate =
                    service.awardQuestCompletion(playerId, "crypt-depths", completionId, 500L);

            assertEquals(true, first.awarded());
            assertEquals(3, first.progression().levelsGained());
            assertEquals(false, duplicate.awarded());
            assertEquals(0, duplicate.progression().levelsGained());
            assertEquals(new CharacterProgressionEngine.State(4, 19L), duplicate.progression().state());
            assertEquals(afterFirst, storage.find(playerId).orElseThrow());
        }
    }

    @Test
    void questCompletionReceiptSurvivesStorageReloadAndDoesNotBlockAnotherCompletion() throws Exception {
        UUID playerId = UUID.fromString("7d3a7d3a-3a7d-4d3a-8d3a-3a7d3a7d3a7d");
        UUID completionId = UUID.fromString("6c2f6c2f-2f6c-4c2f-8c2f-2f6c2f6c2f6c");
        File file = new File(tempDir.toFile(), "characters.yml");
        CharacterProfile afterFirst;
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            assertEquals(true, service.awardQuestCompletion(playerId, "royal-crypt", completionId, 250L).awarded());
            afterFirst = storage.find(playerId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertTrue(storage.saveIfDirty());
            org.junit.jupiter.api.Assertions.assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            CharacterExperienceService.QuestAwardResult duplicate =
                    service.awardQuestCompletion(playerId, "royal-crypt", completionId, 250L);

            assertEquals(false, duplicate.awarded());
            assertEquals(afterFirst, storage.find(playerId).orElseThrow());

            CharacterExperienceService.QuestAwardResult nextCompletion =
                    service.awardQuestCompletion(
                            playerId,
                            "royal-crypt",
                            UUID.fromString("5b3e5b3e-3e5b-4b3e-8b3e-3e5b3e5b3e5b"),
                            250L);
            assertEquals(true, nextCompletion.awarded());
            assertEquals(4, storage.find(playerId).orElseThrow().level());
            assertEquals(19L, storage.find(playerId).orElseThrow().experienceIntoLevel());
        }
    }

    @Test
    void rejectsInvalidQuestRewardIdentifiersAndNonPositiveExperience() {
        UUID playerId = UUID.fromString("4a6d4a6d-6d4a-4a6d-8a6d-6d4a6d4a6d4a");
        UUID completionId = UUID.fromString("3b5e3b5e-5e3b-4b5e-8b5e-5e3b5e3b5e3b");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.awardQuestCompletion(playerId, "UpperCaseQuest", completionId, 100L));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.awardQuestCompletion(playerId, "quest-id", completionId, 0L));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.awardQuestCompletion(playerId, "quest-id", completionId, -1L));
            assertEquals(0, storage.profileCount());
        }
    }


    @Test
    void concurrentDeliveryOfTheSameQuestCompletionAwardsExperienceOnlyOnce() throws Exception {
        UUID playerId = UUID.fromString("1a5d1a5d-5d1a-4a5d-8a5d-5d1a5d1a5d1a");
        UUID completionId = UUID.fromString("2b6e2b6e-6e2b-4b6e-8b6e-6e2b6e2b6e2b");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage =
                    new CharacterProfileStorage(new File(tempDir.toFile(), "characters.yml"), writer, LOGGER);
            CharacterExperienceService service =
                    new CharacterExperienceService(storage, CharacterExperienceCurve.defaults());
            try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
                List<java.util.concurrent.Future<CharacterExperienceService.QuestAwardResult>> deliveries =
                        java.util.stream.IntStream.range(0, 16)
                                .mapToObj(ignored -> executor.submit(
                                        () -> service.awardQuestCompletion(
                                                playerId, "repeatable-hunt", completionId, 250L)))
                                .toList();

                long successfulAwards = 0L;
                for (var delivery : deliveries) {
                    if (delivery.get().awarded()) {
                        successfulAwards++;
                    }
                }

                assertEquals(1L, successfulAwards);
                assertEquals(2, storage.find(playerId).orElseThrow().level());
                assertEquals(111L, storage.find(playerId).orElseThrow().experienceIntoLevel());
            }
        }
    }

}
