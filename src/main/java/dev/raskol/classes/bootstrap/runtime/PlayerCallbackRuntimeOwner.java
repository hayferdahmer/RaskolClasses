// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/**
 * Владеет одноразовыми callback по UUID игрока.
 *
 * <p>Новая задача игрока заменяет предыдущую. Завершившийся callback удаляется
 * до вызова пользовательской операции, а устаревший callback не выполняется.</p>
 */
public final class PlayerCallbackRuntimeOwner implements AutoCloseable {

    private final PlayerCallbackScheduler scheduler;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean closed;

    public PlayerCallbackRuntimeOwner(PlayerCallbackScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public synchronized void schedule(UUID playerId, long delayTicks, Runnable callback) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(callback, "callback");
        ensureOpen();
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks не может быть отрицательным");
        }

        cancel(playerId);
        Entry entry = new Entry();
        TaskHandle handle = Objects.requireNonNull(
                scheduler.schedule(delayTicks, () -> execute(playerId, entry, callback)), "callbackTask");
        entry.handle = handle;
        entries.put(playerId, entry);
    }

    private void execute(UUID playerId, Entry entry, Runnable callback) {
        synchronized (this) {
            if (closed || entries.get(playerId) != entry) {
                return;
            }
            entries.remove(playerId);
            entry.completed = true;
        }
        callback.run();
    }

    public synchronized void cancel(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Entry entry = entries.get(playerId);
        if (entry == null) {
            return;
        }
        try {
            entry.handle.cancel();
            if (entry.handle.isCancelled()) {
                entries.remove(playerId, entry);
            } else {
                throw new IllegalStateException("Одноразовый callback остался активным");
            }
        } catch (RuntimeException failure) {
            try {
                if (entry.handle.isCancelled()) {
                    entries.remove(playerId, entry);
                }
            } catch (RuntimeException statusFailure) {
                failure.addSuppressed(statusFailure);
            }
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        if (closed && entries.isEmpty()) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (Map.Entry<UUID, Entry> tracked : Map.copyOf(entries).entrySet()) {
            try {
                cancel(tracked.getKey());
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Не удалось отменить все player callbacks");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized int ownedTaskCount() {
        return entries.size();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Player callback runtime уже закрыт");
        }
    }

    private static final class Entry {
        private TaskHandle handle;
        private boolean completed;
    }
}
