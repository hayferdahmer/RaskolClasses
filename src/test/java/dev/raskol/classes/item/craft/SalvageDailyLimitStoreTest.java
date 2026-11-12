// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.raskol.classes.storage.AsyncStorageWriter;

/** Проверки асинхронного сохранения дневного лимита разбора. */
@DisplayName("Salvage: персистентность дневного лимита")
class SalvageDailyLimitStoreTest {

    private static final Logger LOGGER = Logger.getLogger("SalvageDailyLimitStoreTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("использованный лимит сохраняется после создания нового хранилища")
    void usageSurvivesStoreRecreation() throws Exception {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-daily.yml").toFile();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore first = new SalvageDailyLimitStore(file, LOGGER, writer);
            assertEquals(SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                    first.tryConsume(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS));

            SalvageDailyLimitStore restarted = new SalvageDailyLimitStore(file, LOGGER, writer);
            assertEquals(SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                    restarted.tryConsume(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS));
            assertEquals(SalvageDailyLimitStore.ConsumeResult.LIMIT_REACHED,
                    restarted.tryConsume(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("новые сутки начинают отдельный дневной счётчик")
    void newDayResetsUsage() throws Exception {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-new-day.yml").toFile();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore store = new SalvageDailyLimitStore(file, LOGGER, writer);
            assertEquals(SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                    store.tryConsume(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS));
            assertEquals(SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                    store.tryConsume(playerId, 20_001L, 1).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("параллельные запросы не создают несколько одновременных снимков")
    void concurrentRequestsAreRejectedWhileWriteIsInFlight() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore store = new SalvageDailyLimitStore(
                    tempDir.resolve("salvage-concurrent.yml").toFile(), LOGGER, writer);
            var first = store.tryConsume(playerId, 20_000L, 3);
            var second = store.tryConsume(playerId, 20_000L, 3);
            assertEquals(SalvageDailyLimitStore.ConsumeResult.BUSY, second.get(5, TimeUnit.SECONDS));
            assertEquals(SalvageDailyLimitStore.ConsumeResult.CONSUMED, first.get(5, TimeUnit.SECONDS));
            assertEquals(SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                    store.tryConsume(playerId, 20_000L, 3).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("ошибка сохранения блокирует разбор и не меняет лимит в памяти")
    void storageFailureFailsClosed() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "file");
        File target = parentFile.resolve("salvage-daily.yml").toFile();

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore store = new SalvageDailyLimitStore(target, LOGGER, writer);
            assertEquals(SalvageDailyLimitStore.ConsumeResult.STORAGE_ERROR,
                    store.tryConsume(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS));
            assertEquals(SalvageDailyLimitStore.ConsumeResult.STORAGE_ERROR,
                    store.tryConsume(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS));
        }
    }
}
