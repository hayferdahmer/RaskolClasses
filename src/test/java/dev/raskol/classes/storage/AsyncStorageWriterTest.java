// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.nio.file.Files;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Проверки последовательной фоновой записи неизменяемых снимков. */
@DisplayName("AsyncStorageWriter: последовательная запись и завершение")
class AsyncStorageWriterTest {

    private static final Logger LOGGER = Logger.getLogger("AsyncStorageWriterTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("снимки одного файла записываются в порядке постановки в очередь")
    void writesSnapshotsInSubmissionOrder() throws Exception {
        File file = tempDir.resolve("ordered.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        try {
            assertTrue(writer.submit(file, "value: first\n").get(5, TimeUnit.SECONDS));
            assertTrue(writer.submit(file, "value: second\n").get(5, TimeUnit.SECONDS));
            assertEquals("second", YamlConfiguration.loadConfiguration(file).getString("value"));
        } finally {
            assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        }
    }

    @Test
    @DisplayName("закрытие дожидается всех уже поставленных записей")
    void closeDrainsQueuedWrites() throws Exception {
        File first = tempDir.resolve("first.yml").toFile();
        File second = tempDir.resolve("second.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        var firstResult = writer.submit(first, "value: first\n");
        var secondResult = writer.submit(second, "value: second\n");

        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        assertTrue(firstResult.get(5, TimeUnit.SECONDS));
        assertTrue(secondResult.get(5, TimeUnit.SECONDS));
        assertEquals(0, writer.pendingWrites());
    }

    @Test
    @DisplayName("ошибка записи не останавливает последующие операции")
    void continuesAfterFailedWrite() throws Exception {
        File blockedParent = tempDir.resolve("not-a-directory").toFile();
        try {
            Files.writeString(blockedParent.toPath(), "blocker");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        File invalidTarget = new File(blockedParent, "invalid.yml");
        File validTarget = tempDir.resolve("valid.yml").toFile();
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        try {
            assertFalse(writer.submit(invalidTarget, "invalid").get(5, TimeUnit.SECONDS));
            assertTrue(writer.submit(validTarget, "value: valid\n").get(5, TimeUnit.SECONDS));
        } finally {
            assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        }
    }

    @Test
    @DisplayName("после закрытия новые операции отклоняются")
    void rejectsWritesAfterClose() {
        AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER);
        assertTrue(writer.closeAndAwait(Duration.ofSeconds(5)));
        assertFalse(writer.submit(tempDir.resolve("late.yml").toFile(), "late").join());
    }
}
