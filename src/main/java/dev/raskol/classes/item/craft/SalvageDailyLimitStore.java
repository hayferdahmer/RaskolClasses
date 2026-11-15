// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.storage.AsyncStorageWriter;
import dev.raskol.classes.storage.SafeStorage;

/**
 * Персистентный дневной лимит разбора с двухфазным резервированием.
 *
 * Резерв сначала записывается как pending. После изменения инвентаря он
 * переводится в использованную попытку. Незавершённые резервы после перезапуска
 * считаются использованными: это консервативно закрывает окно дублирования награды.
 */
final class SalvageDailyLimitStore {

    enum OperationResult {
        SUCCESS,
        LIMIT_REACHED,
        STORAGE_ERROR,
        BUSY,
        NOT_FOUND
    }

    record Reservation(UUID operationId, UUID playerId, long day) {}

    record ReservationAttempt(OperationResult result, Reservation reservation) {}

    private record DailyCount(long day, int count) {}

    private final File file;
    private final Logger logger;
    private final AsyncStorageWriter writer;
    private final Map<UUID, DailyCount> counts = new HashMap<>();
    private final Map<UUID, Reservation> reservations = new HashMap<>();
    private boolean writeInFlight;

    SalvageDailyLimitStore(File file, Logger logger, AsyncStorageWriter writer) {
        this.file = file;
        this.logger = logger;
        this.writer = writer;
        load();
    }

    private void load() {
        YamlConfiguration stored = SafeStorage.loadWithFallback(file, logger);
        for (String key : stored.getKeys(false)) {
            if ("pending".equals(key)) {
                continue;
            }
            try {
                UUID playerId = UUID.fromString(key);
                long day = stored.getLong(key + ".day", -1L);
                int count = stored.getInt(key + ".count", -1);
                if (day >= 0L && count > 0) {
                    counts.put(playerId, new DailyCount(day, count));
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Salvage: пропущена некорректная запись дневного лимита: " + key);
            }
        }

        ConfigurationSection pending = stored.getConfigurationSection("pending");
        if (pending == null) {
            return;
        }
        for (String key : pending.getKeys(false)) {
            try {
                UUID operationId = UUID.fromString(key);
                String playerValue = pending.getString(key + ".player");
                long day = pending.getLong(key + ".day", -1L);
                UUID playerId = UUID.fromString(playerValue);
                if (day >= 0L) {
                    reservations.put(operationId, new Reservation(operationId, playerId, day));
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Salvage: пропущен некорректный незавершённый резерв: " + key);
            }
        }
    }

    /** Записывает pending-резерв, не расходуя дневную попытку окончательно. */
    synchronized CompletableFuture<ReservationAttempt> reserve(UUID playerId, long today, int limit) {
        if (playerId == null || limit < 1) {
            throw new IllegalArgumentException("playerId and positive limit are required");
        }
        if (writeInFlight) {
            return CompletableFuture.completedFuture(
                    new ReservationAttempt(OperationResult.BUSY, null));
        }

        int used = countFor(playerId, today);
        if (used >= limit) {
            return CompletableFuture.completedFuture(
                    new ReservationAttempt(OperationResult.LIMIT_REACHED, null));
        }

        Reservation reservation = new Reservation(UUID.randomUUID(), playerId, today);
        Map<UUID, Reservation> nextReservations = new HashMap<>(reservations);
        nextReservations.put(reservation.operationId(), reservation);
        CompletableFuture<ReservationAttempt> result = new CompletableFuture<>();
        persist(counts, nextReservations).whenComplete((saved, error) -> {
            synchronized (SalvageDailyLimitStore.this) {
                if (error == null && Boolean.TRUE.equals(saved)) {
                    reservations.clear();
                    reservations.putAll(nextReservations);
                    result.complete(new ReservationAttempt(OperationResult.SUCCESS, reservation));
                } else {
                    result.complete(new ReservationAttempt(OperationResult.STORAGE_ERROR, null));
                }
            }
        });
        return result;
    }

    /** Подтверждает расход попытки после успешного изменения инвентаря. */
    synchronized CompletableFuture<OperationResult> commit(Reservation reservation) {
        if (reservation == null || !reservation.equals(reservations.get(reservation.operationId()))) {
            return CompletableFuture.completedFuture(OperationResult.NOT_FOUND);
        }
        if (writeInFlight) {
            return CompletableFuture.completedFuture(OperationResult.BUSY);
        }

        Map<UUID, DailyCount> nextCounts = new HashMap<>(counts);
        DailyCount current = nextCounts.get(reservation.playerId());
        int used = current != null && current.day() == reservation.day() ? current.count() : 0;
        nextCounts.put(reservation.playerId(), new DailyCount(reservation.day(), used + 1));

        Map<UUID, Reservation> nextReservations = new HashMap<>(reservations);
        nextReservations.remove(reservation.operationId());
        CompletableFuture<OperationResult> result = new CompletableFuture<>();
        persist(nextCounts, nextReservations).whenComplete((saved, error) -> {
            synchronized (SalvageDailyLimitStore.this) {
                if (error == null && Boolean.TRUE.equals(saved)) {
                    counts.clear();
                    counts.putAll(nextCounts);
                    reservations.clear();
                    reservations.putAll(nextReservations);
                    result.complete(OperationResult.SUCCESS);
                } else {
                    result.complete(OperationResult.STORAGE_ERROR);
                }
            }
        });
        return result;
    }

    /** Освобождает резерв, если разбор не дошёл до изменения инвентаря. */
    synchronized CompletableFuture<OperationResult> release(Reservation reservation) {
        if (reservation == null || !reservation.equals(reservations.get(reservation.operationId()))) {
            return CompletableFuture.completedFuture(OperationResult.NOT_FOUND);
        }
        if (writeInFlight) {
            return CompletableFuture.completedFuture(OperationResult.BUSY);
        }

        Map<UUID, Reservation> nextReservations = new HashMap<>(reservations);
        nextReservations.remove(reservation.operationId());
        CompletableFuture<OperationResult> result = new CompletableFuture<>();
        persist(counts, nextReservations).whenComplete((saved, error) -> {
            synchronized (SalvageDailyLimitStore.this) {
                if (error == null && Boolean.TRUE.equals(saved)) {
                    reservations.clear();
                    reservations.putAll(nextReservations);
                    result.complete(OperationResult.SUCCESS);
                } else {
                    result.complete(OperationResult.STORAGE_ERROR);
                }
            }
        });
        return result;
    }

    private int countFor(UUID playerId, long day) {
        DailyCount current = counts.get(playerId);
        int used = current != null && current.day() == day ? current.count() : 0;
        for (Reservation reservation : reservations.values()) {
            if (reservation.playerId().equals(playerId) && reservation.day() == day) {
                used++;
            }
        }
        return used;
    }

    private synchronized CompletableFuture<Boolean> persist(
            Map<UUID, DailyCount> nextCounts, Map<UUID, Reservation> nextReservations) {
        if (writeInFlight) {
            return CompletableFuture.completedFuture(false);
        }
        YamlConfiguration snapshot = new YamlConfiguration();
        nextCounts.forEach((id, value) -> {
            String key = id.toString();
            snapshot.set(key + ".day", value.day());
            snapshot.set(key + ".count", value.count());
        });
        nextReservations.forEach((id, reservation) -> {
            String key = "pending." + id;
            snapshot.set(key + ".player", reservation.playerId().toString());
            snapshot.set(key + ".day", reservation.day());
        });

        writeInFlight = true;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        writer.submit(file, snapshot.saveToString()).whenComplete((saved, error) -> {
            synchronized (SalvageDailyLimitStore.this) {
                writeInFlight = false;
                result.complete(error == null && Boolean.TRUE.equals(saved));
            }
        });
        return result;
    }
}
