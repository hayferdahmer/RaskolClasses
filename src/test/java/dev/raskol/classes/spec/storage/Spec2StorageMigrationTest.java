// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.character.profile.CharacterProfileStorage;
import dev.raskol.classes.storage.AsyncStorageWriter;

/** Проверки миграции Spec 2.0 в единый characters.yml. */
@DisplayName("Spec2StorageMigration: единый профиль")
class Spec2StorageMigrationTest {

    private static final Logger LOGGER = Logger.getLogger("Spec2StorageMigrationTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("legacy-файл переносится в профиль и архивируется после успешной записи")
    void migratesLegacyIntoCharacterProfile() throws IOException {
        UUID playerId = UUID.fromString("d7a1c2e3-4b5c-4d6e-8f90-a1b2c3d4e5f6");
        File legacy = write(
                "spec2.yml",
                "players:\n  " + playerId + ":\n    main: fire\n    ranks:\n      fire:\n        fireball: 2\n");

        migrate();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(
                tempDir.resolve("characters.yml").toFile());
        String profile = "profiles." + playerId;
        assertEquals("fire", saved.getString(profile + ".specialization.main"));
        assertEquals(2, saved.getInt(profile + ".specialization.ranks.fire.fireball"));
        assertEquals(1, saved.getInt(profile + ".level"));
        assertFalse(legacy.exists());
        assertTrue(tempDir.resolve("spec2.yml.migrated").toFile().isFile());
    }

    @Test
    @DisplayName("текущий файл имеет приоритет, а уникальные legacy-записи объединяются")
    void mergesCurrentAndLegacyWithCurrentPriority() throws IOException {
        UUID sharedId = UUID.fromString("a7a1c2e3-4b5c-4d6e-8f90-a1b2c3d4e5f6");
        UUID legacyOnlyId = UUID.fromString("b7a1c2e3-4b5c-4d6e-8f90-a1b2c3d4e5f6");
        write(
                "spec2-storage.yml",
                "players:\n  " + sharedId + ":\n    main: fire\n    ranks:\n      fire:\n        fireball: 2\n");
        write(
                "spec2.yml",
                "players:\n  " + sharedId
                        + ":\n    main: frost\n    ranks:\n      fire:\n        fireball: 1\n        pyroblast: 1\n  "
                        + legacyOnlyId + ":\n    main: holy\n");

        migrate();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tempDir.resolve("characters.yml").toFile());
        String shared = "profiles." + sharedId + ".specialization";
        assertEquals("fire", saved.getString(shared + ".main"));
        assertEquals(2, saved.getInt(shared + ".ranks.fire.fireball"));
        assertEquals(1, saved.getInt(shared + ".ranks.fire.pyroblast"));
        assertEquals("holy", saved.getString("profiles." + legacyOnlyId + ".specialization.main"));
        assertTrue(tempDir.resolve("spec2-storage.yml.migrated").toFile().isFile());
        assertTrue(tempDir.resolve("spec2.yml.migrated").toFile().isFile());
    }

    @Test
    @DisplayName("schema 2, прогресс и журнал квестов сохраняются при переходе к schema 3")
    void preservesCharacterProgressAndQuestLedgerDuringMigration() throws IOException {
        UUID playerId = UUID.fromString("c7a1c2e3-4b5c-4d6e-8f90-a1b2c3d4e5f6");
        YamlConfiguration characters = new YamlConfiguration();
        String profile = "profiles." + playerId;
        characters.set("schema-version", 2);
        characters.set(profile + ".schema-version", 1);
        characters.set(profile + ".level", 23);
        characters.set(profile + ".experience", 1234L);
        characters.set(profile + ".created-at", 100L);
        characters.set(profile + ".updated-at", 200L);
        characters.set("processed-quest-rewards." + playerId, List.of("quest:crypt-depths:" + playerId));
        Files.writeString(tempDir.resolve("characters.yml"), characters.saveToString());
        write("spec2-storage.yml", "players:\n  " + playerId + ":\n    main: discipline\n");

        migrate();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tempDir.resolve("characters.yml").toFile());
        assertEquals(3, saved.getInt("schema-version"));
        assertEquals(23, saved.getInt(profile + ".level"));
        assertEquals(1234L, saved.getLong(profile + ".experience"));
        assertEquals(100L, saved.getLong(profile + ".created-at"));
        assertEquals("discipline", saved.getString(profile + ".specialization.main"));
        assertEquals(
                List.of("quest:crypt-depths:" + playerId), saved.getStringList("processed-quest-rewards." + playerId));
        YamlConfiguration backup = YamlConfiguration.loadConfiguration(
                tempDir.resolve("characters.yml.bak").toFile());
        assertEquals("discipline", backup.getString(profile + ".specialization.main"));
    }

    @Test
    @DisplayName("повреждённый legacy архивируется только если текущая копия пригодна")
    void archivesUnreadableLegacyAfterMigratingCurrentCopy() throws IOException {
        UUID playerId = UUID.fromString("e7a1c2e3-4b5c-4d6e-8f90-a1b2c3d4e5f6");
        write("spec2-storage.yml", "players:\n  " + playerId + ":\n    main: arcane\n");
        File legacy = write("spec2.yml", "players: [unterminated\n");

        migrate();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tempDir.resolve("characters.yml").toFile());
        assertEquals("arcane", saved.getString("profiles." + playerId + ".specialization.main"));
        assertFalse(legacy.exists());
        assertTrue(tempDir.resolve("spec2.yml.migrated").toFile().isFile());
    }

    @Test
    @DisplayName("если все источники повреждены, миграция останавливается и сохраняет оригинал")
    void refusesToArchiveWhenNoReadableSourceExists() throws IOException {
        File legacy = write("spec2.yml", "players: [unterminated\n");

        assertThrows(IllegalStateException.class, this::migrate);
        assertTrue(legacy.exists());
    }

    @Test
    @DisplayName("при неудачной записи characters.yml исходный Spec 2.0 файл остаётся")
    void keepsSourceWhenUnifiedFileCannotBeWritten() throws IOException {
        Path charactersDirectory = tempDir.resolve("characters.yml");
        Files.createDirectory(charactersDirectory);
        Files.writeString(charactersDirectory.resolve("keep-me"), "directory must not be replaced");
        UUID playerId = UUID.fromString("f7a1c2e3-4b5c-4d6e-8f90-a1b2c3d4e5f6");
        File source = write("spec2-storage.yml", "players:\n  " + playerId + ":\n    main: frost\n");

        assertThrows(IllegalStateException.class, this::migrate);
        assertTrue(source.exists());
    }

    private void migrate() {
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            CharacterProfileStorage profiles =
                    new CharacterProfileStorage(tempDir.resolve("characters.yml").toFile(), writer, LOGGER);
            Spec2StorageMigration.migrate(tempDir.toFile(), profiles, LOGGER);
            assertTrue(writer.closeAndAwait(java.time.Duration.ofSeconds(5)));
        }
    }

    private File write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }
}
