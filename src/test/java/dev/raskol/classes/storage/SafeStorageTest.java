// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Проверки восстановления YAML, карантина повреждённых копий и атомарной записи. */
@DisplayName("SafeStorage: восстановление и атомарная запись")
class SafeStorageTest {

    private static final Logger LOGGER = Logger.getLogger("SafeStorageTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("отсутствие всех копий означает новое хранилище")
    void missingStorageStartsEmpty() {
        SafeStorage.LoadResult result =
                SafeStorage.load(tempDir.resolve("fresh.yml").toFile(), LOGGER);

        assertEquals(SafeStorage.LoadStatus.NEW, result.status());
        assertTrue(result.configuration().getKeys(true).isEmpty());
    }

    @Test
    @DisplayName("основной файл имеет приоритет, устаревший tmp удаляется")
    void mainFileWinsAndStaleTmpIsRemoved() throws IOException {
        File file = write("main.yml", "value: main\n");
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), "value: tmp\n");

        SafeStorage.LoadResult result = SafeStorage.load(file, LOGGER);

        assertEquals(SafeStorage.LoadStatus.LOADED_MAIN, result.status());
        assertEquals("main", result.configuration().getString("value"));
        assertFalse(tmp.exists());
    }

    @Test
    @DisplayName("при отсутствии основного файла восстанавливается tmp")
    void missingMainRestoresTmp() throws IOException {
        File file = tempDir.resolve("tmp-recovery.yml").toFile();
        Files.writeString(new File(file.getParentFile(), file.getName() + ".tmp").toPath(), "value: latest\n");

        SafeStorage.LoadResult result = SafeStorage.load(file, LOGGER);

        assertEquals(SafeStorage.LoadStatus.RECOVERED_TMP, result.status());
        assertEquals("latest", result.configuration().getString("value"));
    }

    @Test
    @DisplayName("повреждённый основной файл сохраняется в карантин, затем восстанавливается bak")
    void corruptMainIsQuarantinedAndBackupRestored() throws IOException {
        File file = write("backup-recovery.yml", "value: [unterminated\n");
        Files.writeString(new File(file.getParentFile(), file.getName() + ".bak").toPath(), "value: backup\n");

        SafeStorage.LoadResult result = SafeStorage.load(file, LOGGER);

        assertEquals(SafeStorage.LoadStatus.RECOVERED_BACKUP, result.status());
        assertEquals("backup", result.configuration().getString("value"));
        assertTrue(Files.exists(file.toPath()), "Оригинал повреждённого файла не удаляется");
        assertEquals(1, countFilesEndingWith(".quarantine"));
        assertEquals(1, countFilesEndingWith(".quarantine.json"));
    }

    @Test
    @DisplayName("если все копии повреждены, загрузка завершается ошибкой и сохраняет доказательства")
    void unusableCopiesFailClosedAndAreQuarantined() throws IOException {
        File file = write("broken.yml", "value: [broken\n");
        Files.writeString(new File(file.getParentFile(), file.getName() + ".tmp").toPath(), "value: [broken\n");
        Files.writeString(new File(file.getParentFile(), file.getName() + ".bak").toPath(), "value: [broken\n");

        SafeStorage.StorageLoadException exception = assertThrows(
                SafeStorage.StorageLoadException.class, () -> SafeStorage.load(file, LOGGER));

        assertEquals(SafeStorage.LoadStatus.CORRUPT, exception.status());
        assertTrue(Files.exists(file.toPath()));
        assertEquals(3, countFilesEndingWith(".quarantine"));
        assertEquals(3, countFilesEndingWith(".quarantine.json"));
        assertThrows(SafeStorage.StorageLoadException.class, () -> SafeStorage.loadWithFallback(file, LOGGER));
    }

    @Test
    @DisplayName("сохранение не перезаписывает повреждённый основной файл")
    void atomicSaveDoesNotOverwriteCorruptMain() throws IOException {
        File file = write("do-not-overwrite.yml", "value: [broken\n");
        String original = Files.readString(file.toPath());
        YamlConfiguration replacement = new YamlConfiguration();
        replacement.set("value", "replacement");

        assertFalse(SafeStorage.saveAtomic(replacement, file, LOGGER));
        assertEquals(original, Files.readString(file.toPath()));
    }

    @Test
    @DisplayName("валидный снимок сохраняется, предыдущая версия остаётся в bak")
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

    private long countFilesEndingWith(String suffix) throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(suffix)).count();
        }
    }

    private File write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }
}
