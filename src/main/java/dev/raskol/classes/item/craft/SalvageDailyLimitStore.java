// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.storage.AsyncStorageWriter;
import dev.raskol.classes.storage.SafeStorage;

/**
 * Персистентный дневной лимит разбора предметов.
 *
 * Снимок YAML формируется на основном потоке, а запись выполняется общим
 * последовательным файловым исполнителем. Пока снимок не подтверждён,
 * новые резервирования отклоняются, чтобы не потерять обновления.
 */
final class SalvageDailyLimitStore {

    enum ConsumeResult {
        CONSUMED,
        LIMIT_REACHED,
        STORAGE_ERROR,
        BUSY
    }

    private record DailyCount(long day, int count) {}

    private final File file;
    private final Logger logger;
    private final AsyncStorageWriter writer;
    private final Map<UUID, DailyCount> counts = new HashMap<>();
    private boolean reservationInFlight;

    SalvageDailyLimitStore(File file, Logger logger, AsyncStorageWriter writer) {
        this.file = file;
        this.logger = logger;
        this.writer = writer;
        load();
    }

    private void load() {
        YamlConfiguration stored = SafeStorage.loadWithFallback(file, logger);
        for (String key : stored.getKeys(false)) {
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
    }

    /**
     * Сначала сохраняет неизменяемый снимок лимита, затем подтверждает резервирование.
     * Одновременно допускается только одна незавершённая запись.
     */
    synchronized CompletableFuture<ConsumeResult> tryConsume(
            UUID playerId, long today, int limit) {
        if (playerId == null || limit < 1) {
            throw new IllegalArgumentException("playerId and positive limit are required");
        }
        if (reservationInFlight) {
            return CompletableFuture.completedFuture(ConsumeResult.BUSY);
        }

        Map<UUID, DailyCount> next = new HashMap<>();
        counts.forEach((id, value) -> {
            if (value.day() == today) {
                next.put(id, value);
            }
        });

        DailyCount current = next.get(playerId);
        int used = current == null ? 0 : current.count();
        if (used >= limit) {
            return CompletableFuture.completedFuture(ConsumeResult.LIMIT_REACHED);
        }
        next.put(playerId, new DailyCount(today, used + 1));

        YamlConfiguration snapshot = new YamlConfiguration();
        next.forEach((id, value) -> {
            String key = id.toString();
            snapshot.set(key + ".day", value.day());
            snapshot.set(key + ".count", value.count());
        });

        reservationInFlight = true;
        CompletableFuture<ConsumeResult> result = new CompletableFuture<>();
        writer.submit(file, snapshot.saveToString()).whenComplete((saved, error) -> {
            synchronized (SalvageDailyLimitStore.this) {
                if (error == null && Boolean.TRUE.equals(saved)) {
                    counts.clear();
                    counts.putAll(next);
                    reservationInFlight = false;
                    result.complete(ConsumeResult.CONSUMED);
                } else {
                    reservationInFlight = false;
                    result.complete(ConsumeResult.STORAGE_ERROR);
                }
            }
        });
        return result;
    }
}
