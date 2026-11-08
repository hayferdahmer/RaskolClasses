// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Проверки миграции и сохранения legacy-данных. */
@DisplayName("Spec2StorageMigration: сохранность legacy-данных")
class Spec2StorageMigrationTest {

    private static final Logger LOGGER = Logger.getLogger("Spec2StorageMigrationTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("legacy копируется и удаляется только после успешной записи")
    void migratesLegacyAfterSuccessfulCopy() throws IOException {
        File legacy = write("spec2.yml", "players:\n  test:\n    main: fire\n");
        YamlConfiguration loaded = Spec2StorageMigration.load(tempDir.toFile(), LOGGER);
        File current = tempDir.resolve("spec2-storage.yml").toFile();

        assertEquals("fire", loaded.getString("players.test.main"));
        assertTrue(current.isFile());
        assertFalse(legacy.exists());
        assertEquals("fire", YamlConfiguration.loadConfiguration(current).getString("players.test.main"));
    }

    @Test
    @DisplayName("если оба файла заполнены, новый используется, legacy сохраняется")
    void keepsLegacyWhenBothFilesContainData() throws IOException {
        File legacy = write("spec2.yml", "players:\n  legacy:\n    main: old\n");
        write("spec2-storage.yml", "players:\n  current:\n    main: new\n");

        YamlConfiguration loaded = Spec2StorageMigration.load(tempDir.toFile(), LOGGER);

        assertEquals("new", loaded.getString("players.current.main"));
        assertTrue(legacy.exists());
    }

    @Test
    @DisplayName("при отказе записи новой копии legacy-файл остаётся")
    void keepsLegacyWhenTargetCannotBeWritten() throws IOException {
        File legacy = write("spec2.yml", "players:\n  preserved:\n    main: frost\n");
        Path blockedTarget = tempDir.resolve("spec2-storage.yml");
        Files.createDirectory(blockedTarget);
        Files.writeString(blockedTarget.resolve("keep-me"), "directory must not be replaced");

        YamlConfiguration loaded = Spec2StorageMigration.load(tempDir.toFile(), LOGGER);

        assertEquals("frost", loaded.getString("players.preserved.main"));
        assertTrue(legacy.exists());
    }

    @Test
    @DisplayName("повреждённый legacy-файл сохраняется, используется читаемое новое хранилище")
    void preservesUnreadableLegacy() throws IOException {
        File legacy = write("spec2.yml", "players: [unterminated\n");
        write("spec2-storage.yml", "players:\n  current:\n    main: arcane\n");

        YamlConfiguration loaded = Spec2StorageMigration.load(tempDir.toFile(), LOGGER);

        assertEquals("arcane", loaded.getString("players.current.main"));
        assertTrue(legacy.exists());
    }

    @Test
    @DisplayName("пустой legacy-файл удаляется без создания записей")
    void removesEmptyLegacyFile() throws IOException {
        File legacy = write("spec2.yml", "{}\n");

        YamlConfiguration loaded = Spec2StorageMigration.load(tempDir.toFile(), LOGGER);

        assertTrue(loaded.getKeys(true).isEmpty());
        assertFalse(legacy.exists());
    }

    private File write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }
}
