// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

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

/** Проверки восстановления YAML и атомарной записи. */
@DisplayName("SafeStorage: восстановление и атомарная запись")
class SafeStorageTest {

    private static final Logger LOGGER = Logger.getLogger("SafeStorageTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("отсутствие файла и sidecar-файлов означает новое хранилище")
    void missingStorageStartsEmpty() {
        assertTrue(SafeStorage.loadWithFallback(tempDir.resolve("fresh.yml").toFile(), LOGGER)
                .getKeys(true).isEmpty());
    }

    @Test
    @DisplayName("основной файл имеет приоритет, устаревший tmp удаляется")
    void mainFileWinsAndStaleTmpIsRemoved() throws IOException {
        File file = write("main.yml", "value: main\n");
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), "value: tmp\n");

        YamlConfiguration loaded = SafeStorage.loadWithFallback(file, LOGGER);

        assertEquals("main", loaded.getString("value"));
        assertFalse(tmp.exists());
    }

    @Test
    @DisplayName("при отсутствии основного файла восстанавливается tmp")
    void missingMainRestoresTmp() throws IOException {
        File file = tempDir.resolve("tmp-recovery.yml").toFile();
        Files.writeString(new File(file.getParentFile(), file.getName() + ".tmp").toPath(), "value: latest\n");

        assertEquals("latest", SafeStorage.loadWithFallback(file, LOGGER).getString("value"));
    }

    @Test
    @DisplayName("повреждённый основной файл восстанавливается из bak")
    void corruptMainRestoresBackup() throws IOException {
        File file = write("backup-recovery.yml", "value: [unterminated\n");
        Files.writeString(new File(file.getParentFile(), file.getName() + ".bak").toPath(), "value: backup\n");

        assertEquals("backup", SafeStorage.loadWithFallback(file, LOGGER).getString("value"));
    }

    @Test
    @DisplayName("если все копии повреждены, возвращается пустая конфигурация")
    void unusableCopiesReturnEmptyConfiguration() throws IOException {
        File file = write("broken.yml", "value: [broken\n");
        Files.writeString(new File(file.getParentFile(), file.getName() + ".tmp").toPath(), "value: [broken\n");
        Files.writeString(new File(file.getParentFile(), file.getName() + ".bak").toPath(), "value: [broken\n");

        assertTrue(SafeStorage.loadWithFallback(file, LOGGER).getKeys(true).isEmpty());
    }

    @Test
    @DisplayName("повторное сохранение оставляет предыдущую версию в bak")
    void atomicSaveCreatesBackup() throws IOException {
        File file = tempDir.resolve("atomic.yml").toFile();
        YamlConfiguration first = new YamlConfiguration();
        first.set("value", "first");
        assertTrue(SafeStorage.saveAtomic(first, file, LOGGER));

        YamlConfiguration second = new YamlConfiguration();
        second.set("value", "second");
        assertTrue(SafeStorage.saveAtomic(second, file, LOGGER));

        assertEquals("second", YamlConfiguration.loadConfiguration(file).getString("value"));
        File backup = new File(file.getParentFile(), file.getName() + ".bak");
        assertEquals("first", YamlConfiguration.loadConfiguration(backup).getString("value"));
        assertFalse(new File(file.getParentFile(), file.getName() + ".tmp").exists());
    }

    @Test
    @DisplayName("ошибка записи возвращает false")
    void atomicSaveReportsFailure() throws IOException {
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "file");
        File target = parentFile.resolve("nested.yml").toFile();
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("value", "must-not-be-lost");

        assertFalse(SafeStorage.saveAtomic(configuration, target, LOGGER));
    }

    private File write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }
}
