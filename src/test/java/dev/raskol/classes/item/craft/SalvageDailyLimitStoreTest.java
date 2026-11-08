// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Проверки сохранения дневного лимита разбора между перезапусками. */
@DisplayName("Salvage: персистентность дневного лимита")
class SalvageDailyLimitStoreTest {

    private static final Logger LOGGER = Logger.getLogger("SalvageDailyLimitStoreTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("использованный лимит сохраняется после создания нового сервиса")
    void usageSurvivesStoreRecreation() {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-daily.yml").toFile();

        SalvageDailyLimitStore first = new SalvageDailyLimitStore(file, LOGGER);
        assertEquals(
                SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                first.tryConsume(playerId, 20_000L, 2));

        SalvageDailyLimitStore restarted = new SalvageDailyLimitStore(file, LOGGER);
        assertEquals(
                SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                restarted.tryConsume(playerId, 20_000L, 2));
        assertEquals(
                SalvageDailyLimitStore.ConsumeResult.LIMIT_REACHED,
                restarted.tryConsume(playerId, 20_000L, 2));
    }

    @Test
    @DisplayName("новые сутки начинают отдельный дневной счётчик")
    void newDayResetsUsage() {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-new-day.yml").toFile();

        SalvageDailyLimitStore store = new SalvageDailyLimitStore(file, LOGGER);
        assertEquals(
                SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                store.tryConsume(playerId, 20_000L, 1));
        assertEquals(
                SalvageDailyLimitStore.ConsumeResult.CONSUMED,
                store.tryConsume(playerId, 20_001L, 1));
    }

    @Test
    @DisplayName("параллельные запросы не превышают дневной лимит")
    void concurrentRequestsCannotExceedLimit() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        SalvageDailyLimitStore store =
                new SalvageDailyLimitStore(tempDir.resolve("salvage-concurrent.yml").toFile(), LOGGER);
        java.util.concurrent.atomic.AtomicInteger consumed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(12);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Thread> threads = new java.util.ArrayList<>();

        for (int i = 0; i < 12; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (store.tryConsume(playerId, 20_000L, 3)
                            == SalvageDailyLimitStore.ConsumeResult.CONSUMED) {
                        consumed.incrementAndGet();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(3, consumed.get());
    }

    @Test
    @DisplayName("ошибка сохранения блокирует разбор")
    void storageFailureFailsClosed() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "file");
        File target = parentFile.resolve("salvage-daily.yml").toFile();

        SalvageDailyLimitStore store = new SalvageDailyLimitStore(target, LOGGER);
        assertEquals(
                SalvageDailyLimitStore.ConsumeResult.STORAGE_ERROR,
                store.tryConsume(playerId, 20_000L, 2));
    }
}
