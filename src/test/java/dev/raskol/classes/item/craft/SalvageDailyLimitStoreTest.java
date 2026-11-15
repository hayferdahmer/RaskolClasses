// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

/** Проверки двухфазного резервирования дневного лимита разбора. */
@DisplayName("Salvage: двухфазный дневной лимит")
class SalvageDailyLimitStoreTest {

    private static final Logger LOGGER = Logger.getLogger("SalvageDailyLimitStoreTest");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("подтверждённая попытка сохраняется после создания нового хранилища")
    void committedUsageSurvivesStoreRecreation() throws Exception {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-daily.yml").toFile();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore first = new SalvageDailyLimitStore(file, LOGGER, writer);
            var reservation = first.reserve(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS);
            assertEquals(SalvageDailyLimitStore.OperationResult.SUCCESS, reservation.result());
            assertNotNull(reservation.reservation());
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.SUCCESS,
                    first.commit(reservation.reservation()).get(5, TimeUnit.SECONDS));

            SalvageDailyLimitStore restarted = new SalvageDailyLimitStore(file, LOGGER, writer);
            var second = restarted.reserve(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS);
            assertEquals(SalvageDailyLimitStore.OperationResult.SUCCESS, second.result());
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.SUCCESS,
                    restarted.commit(second.reservation()).get(5, TimeUnit.SECONDS));
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.LIMIT_REACHED,
                    restarted.reserve(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS).result());
        }
    }

    @Test
    @DisplayName("освобождённый резерв не расходует дневную попытку")
    void releasedReservationDoesNotConsumeDailyLimit() throws Exception {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-release.yml").toFile();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore store = new SalvageDailyLimitStore(file, LOGGER, writer);
            var reservation = store.reserve(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS);
            assertEquals(SalvageDailyLimitStore.OperationResult.SUCCESS, reservation.result());
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.SUCCESS,
                    store.release(reservation.reservation()).get(5, TimeUnit.SECONDS));

            var retry = store.reserve(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS);
            assertEquals(SalvageDailyLimitStore.OperationResult.SUCCESS, retry.result());
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.SUCCESS,
                    store.commit(retry.reservation()).get(5, TimeUnit.SECONDS));
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.LIMIT_REACHED,
                    store.reserve(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS).result());
        }
    }

    @Test
    @DisplayName("незавершённый резерв после перезапуска консервативно занимает попытку")
    void pendingReservationSurvivesRestartAndCountsAgainstLimit() throws Exception {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-pending.yml").toFile();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore first = new SalvageDailyLimitStore(file, LOGGER, writer);
            var pending = first.reserve(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS);
            assertEquals(SalvageDailyLimitStore.OperationResult.SUCCESS, pending.result());

            SalvageDailyLimitStore restarted = new SalvageDailyLimitStore(file, LOGGER, writer);
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.LIMIT_REACHED,
                    restarted.reserve(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS).result());
        }
    }

    @Test
    @DisplayName("новые сутки начинают отдельный дневной счётчик")
    void newDayResetsUsage() throws Exception {
        UUID playerId = UUID.randomUUID();
        File file = tempDir.resolve("salvage-new-day.yml").toFile();
        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore store = new SalvageDailyLimitStore(file, LOGGER, writer);
            var first = store.reserve(playerId, 20_000L, 1).get(5, TimeUnit.SECONDS);
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.SUCCESS,
                    store.commit(first.reservation()).get(5, TimeUnit.SECONDS));
            var nextDay = store.reserve(playerId, 20_001L, 1).get(5, TimeUnit.SECONDS);
            assertEquals(SalvageDailyLimitStore.OperationResult.SUCCESS, nextDay.result());
        }
    }

    @Test
    @DisplayName("ошибка сохранения блокирует резервирование")
    void storageFailureFailsClosed() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "file");
        File target = parentFile.resolve("salvage-daily.yml").toFile();

        try (AsyncStorageWriter writer = new AsyncStorageWriter(LOGGER)) {
            SalvageDailyLimitStore store = new SalvageDailyLimitStore(target, LOGGER, writer);
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.STORAGE_ERROR,
                    store.reserve(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS).result());
            assertEquals(
                    SalvageDailyLimitStore.OperationResult.STORAGE_ERROR,
                    store.reserve(playerId, 20_000L, 2).get(5, TimeUnit.SECONDS).result());
        }
    }
}
