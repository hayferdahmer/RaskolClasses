// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.storage.AsyncStorageWriter;

class CharacterProfileStorageTest {

    private static final Logger LOGGER = Logger.getLogger("CharacterProfileStorageTest");

    @TempDir
    Path tempDir;

    @Test
    void createsLegacyPlayerAtLevelOneExactlyOnce() {
        UUID playerId = UUID.fromString("d5c2d5c2-5c2d-4d5c-8c2d-5c2d5c2d5c2d");
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            File file = new File(tempDir.toFile(), "characters.yml");
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER, () -> 100L);

            CharacterProfile first = storage.ensureProfile(playerId);
            CharacterProfile second = storage.ensureProfile(playerId);

            assertEquals(1, first.level());
            assertEquals(0, first.experienceIntoLevel());
            assertEquals(first, second);
            assertEquals(100L, first.createdAtMillis());
            assertEquals(100L, first.updatedAtMillis());
        }
    }

    @Test
    void persistsProfileAndReloadsItWithoutResettingProgress() throws Exception {
        UUID playerId = UUID.fromString("8a4d8a4d-4d8a-4d8a-8d4a-4d8a4d8a4d8a");
        File file = new File(tempDir.toFile(), "characters.yml");
        CharacterProfile expected;
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER, () -> 100L);
            storage.ensureProfile(playerId);
            expected = new CharacterProfile(playerId, 1, 7, 425L, 100L, 200L);
            storage.upsert(expected);
            org.junit.jupiter.api.Assertions.assertTrue(storage.saveIfDirty());
            org.junit.jupiter.api.Assertions.assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage reloaded = new CharacterProfileStorage(file, writer, LOGGER, () -> 300L);

            assertEquals(expected, reloaded.find(playerId).orElseThrow());
            assertNotEquals(300L, reloaded.find(playerId).orElseThrow().createdAtMillis());
        }
    }

    @Test
    void migratesLegacySchemaWithoutResettingExistingProfileFields() throws Exception {
        UUID playerId = UUID.fromString("7b3a7b3a-3a7b-4a3a-8b3a-3a7b3a7b3a7b");
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("profiles." + playerId + ".level", 23);
        java.nio.file.Files.writeString(file.toPath(), legacy.saveToString());

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER, () -> 500L);
            CharacterProfile migrated = storage.find(playerId).orElseThrow();

            assertEquals(23, migrated.level());
            assertEquals(0L, migrated.experienceIntoLevel());
            assertEquals(500L, migrated.createdAtMillis());
            assertEquals(500L, migrated.updatedAtMillis());
            org.junit.jupiter.api.Assertions.assertTrue(storage.isDirty());
            org.junit.jupiter.api.Assertions.assertTrue(storage.saveIfDirty());
            org.junit.jupiter.api.Assertions.assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage reloaded = new CharacterProfileStorage(file, writer, LOGGER, () -> 900L);
            assertEquals(23, reloaded.find(playerId).orElseThrow().level());
            org.junit.jupiter.api.Assertions.assertFalse(reloaded.isDirty());
        }
    }

    @Test
    void loadsValidBackupWhenPrimaryFileIsCorrupt() throws Exception {
        UUID playerId = UUID.fromString("6a5d6a5d-5d6a-4a5d-8d6a-5d6a5d6a5d6a");
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration backup = new YamlConfiguration();
        String base = "profiles." + playerId;
        backup.set("schema-version", 1);
        backup.set(base + ".schema-version", 1);
        backup.set(base + ".level", 12);
        backup.set(base + ".experience", 200L);
        backup.set(base + ".created-at", 100L);
        backup.set(base + ".updated-at", 200L);
        File backupFile = new File(tempDir.toFile(), "characters.yml.bak");
        java.nio.file.Files.writeString(backupFile.toPath(), backup.saveToString());
        java.nio.file.Files.writeString(file.toPath(), "schema-version: [broken");

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER, () -> 300L);

            CharacterProfile restored = storage.find(playerId).orElseThrow();
            assertEquals(12, restored.level());
            assertEquals(200L, restored.experienceIntoLevel());
            org.junit.jupiter.api.Assertions.assertTrue(storage.isDirty());
            org.junit.jupiter.api.Assertions.assertTrue(storage.saveIfDirty());
            org.junit.jupiter.api.Assertions.assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }

        YamlConfiguration restored = YamlConfiguration.loadConfiguration(file);
        assertEquals(2, restored.getInt("schema-version"));
        assertEquals(12, restored.getInt("profiles." + playerId + ".level"));
        assertEquals(200L, restored.getLong("profiles." + playerId + ".experience"));
    }

    @Test
    void rejectsUnsupportedFutureSchemaWithoutRewritingIt() throws Exception {
        File file = new File(tempDir.toFile(), "characters.yml");
        String original = "schema-version: 99\nprofiles: {}\n";
        java.nio.file.Files.writeString(file.toPath(), original);

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            assertThrows(
                    IllegalStateException.class, () -> new CharacterProfileStorage(file, writer, LOGGER, () -> 100L));
        }
        assertEquals(original, java.nio.file.Files.readString(file.toPath()));
    }

    @Test
    void rejectsOutOfRangeProfileInsteadOfClampingItToLevelOne() throws Exception {
        UUID playerId = UUID.fromString("2c4e2c4e-4e2c-4c4e-8c4e-4e2c4e2c4e2c");
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        String base = "profiles." + playerId;
        yaml.set("schema-version", 1);
        yaml.set(base + ".schema-version", 1);
        yaml.set(base + ".level", 0);
        yaml.set(base + ".experience", 0L);
        yaml.set(base + ".created-at", 100L);
        yaml.set(base + ".updated-at", 100L);
        java.nio.file.Files.writeString(file.toPath(), yaml.saveToString());

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            assertThrows(
                    IllegalStateException.class, () -> new CharacterProfileStorage(file, writer, LOGGER, () -> 200L));
        }
    }

    @Test
    void rejectsMalformedExistingProfileInsteadOfSilentlyResettingIt() throws Exception {
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("profiles.not-a-uuid.level", 42);
        java.nio.file.Files.writeString(file.toPath(), yaml.saveToString());

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            assertThrows(
                    IllegalStateException.class, () -> new CharacterProfileStorage(file, writer, LOGGER, () -> 100L));
        }
    }

    @Test
    void recordsQuestRewardAndProfileInOneDurableSnapshot() throws Exception {
        UUID playerId = UUID.fromString("4a8d4a8d-8d4a-4a8d-8d4a-4a8d4a8d4a8d");
        String rewardId = "quest:crypt-depths:4a8d4a8d-8d4a-4a8d-8d4a-4a8d4a8d4a8d";
        File file = new File(tempDir.toFile(), "characters.yml");
        CharacterProfile expected = new CharacterProfile(playerId, 1, 3, 42L, 100L, 200L);

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER, () -> 100L);
            storage.ensureProfile(playerId);

            org.junit.jupiter.api.Assertions.assertTrue(
                    storage.recordQuestRewardIfAbsent(playerId, rewardId, expected));
            org.junit.jupiter.api.Assertions.assertFalse(
                    storage.recordQuestRewardIfAbsent(
                            playerId,
                            rewardId,
                            new CharacterProfile(playerId, 1, 4, 0L, 100L, 300L)));
            assertEquals(expected, storage.find(playerId).orElseThrow());
            org.junit.jupiter.api.Assertions.assertTrue(storage.saveIfDirty());
            org.junit.jupiter.api.Assertions.assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage reloaded = new CharacterProfileStorage(file, writer, LOGGER, () -> 400L);

            org.junit.jupiter.api.Assertions.assertFalse(
                    reloaded.recordQuestRewardIfAbsent(playerId, rewardId, expected));
            assertEquals(expected, reloaded.find(playerId).orElseThrow());
            org.junit.jupiter.api.Assertions.assertFalse(reloaded.isDirty());
        }
    }

    @Test
    void migratesStorageSchemaOneToTwoWithoutChangingProfileData() throws Exception {
        UUID playerId = UUID.fromString("5b9e5b9e-9e5b-4b9e-9e5b-9e5b9e5b9e5b");
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        String base = "profiles." + playerId;
        yaml.set("schema-version", 1);
        yaml.set(base + ".schema-version", 1);
        yaml.set(base + ".level", 27);
        yaml.set(base + ".experience", 1234L);
        yaml.set(base + ".created-at", 100L);
        yaml.set(base + ".updated-at", 200L);
        java.nio.file.Files.writeString(file.toPath(), yaml.saveToString());

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage storage = new CharacterProfileStorage(file, writer, LOGGER, () -> 500L);

            assertEquals(27, storage.find(playerId).orElseThrow().level());
            assertEquals(1234L, storage.find(playerId).orElseThrow().experienceIntoLevel());
            org.junit.jupiter.api.Assertions.assertTrue(storage.isDirty());
            org.junit.jupiter.api.Assertions.assertTrue(storage.saveIfDirty());
            org.junit.jupiter.api.Assertions.assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals(2, migrated.getInt("schema-version"));
        assertEquals(1, migrated.getInt(base + ".schema-version"));
        assertEquals(27, migrated.getInt(base + ".level"));
        assertEquals(1234L, migrated.getLong(base + ".experience"));
    }


    @Test
    void rejectsMalformedQuestRewardLedgerInsteadOfIgnoringIt() throws Exception {
        UUID playerId = UUID.fromString("3c7f3c7f-7f3c-4c7f-8c7f-3c7f3c7f3c7f");
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        String profile = "profiles." + playerId;
        yaml.set("schema-version", 2);
        yaml.set(profile + ".schema-version", 1);
        yaml.set(profile + ".level", 1);
        yaml.set(profile + ".experience", 0L);
        yaml.set(profile + ".created-at", 100L);
        yaml.set(profile + ".updated-at", 100L);
        yaml.set("processed-quest-rewards." + playerId, List.of(42));
        java.nio.file.Files.writeString(file.toPath(), yaml.saveToString());

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            assertThrows(
                    IllegalStateException.class, () -> new CharacterProfileStorage(file, writer, LOGGER, () -> 200L));
        }
    }

    @Test
    void rejectsQuestRewardReceiptsWithoutACharacterProfile() throws Exception {
        UUID playerId = UUID.fromString("2b6e2b6e-6e2b-4b6e-8b6e-6e2b6e2b6e2b");
        File file = new File(tempDir.toFile(), "characters.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("processed-quest-rewards." + playerId, List.of("quest:crypt-depths:" + playerId));
        java.nio.file.Files.writeString(file.toPath(), yaml.saveToString());

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            assertThrows(
                    IllegalStateException.class, () -> new CharacterProfileStorage(file, writer, LOGGER, () -> 200L));
        }
    }

}
