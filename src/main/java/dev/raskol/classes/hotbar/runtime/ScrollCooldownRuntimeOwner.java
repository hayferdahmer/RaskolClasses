// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hotbar.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Единственный владелец producer и кэшей ScrollCooldownTask. */
public final class ScrollCooldownRuntimeOwner implements AutoCloseable {

    private final ScrollCooldownTaskScheduler scheduler;
    private final Runnable clearCaches;
    private final TaskScope taskScope = new TaskScope();

    private boolean startAttempted;
    private boolean running;
    private boolean cachesCleared;
    private boolean closed;

    public ScrollCooldownRuntimeOwner(ScrollCooldownTaskScheduler scheduler, Runnable clearCaches) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clearCaches = Objects.requireNonNull(clearCaches, "clearCaches");
    }

    /** Запускает периодический producer ровно один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Scroll cooldown runtime уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            taskScope.register(Objects.requireNonNull(scheduler.scheduleTick(), "scrollCooldownTask"));
            running = true;
        } catch (RuntimeException startupFailure) {
            try {
                taskScope.close();
            } catch (RuntimeException cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            throw startupFailure;
        }
    }

    /**
     * Останавливает producer и очищает кэши даже при ошибке отмены.
     * Не подтверждённая отмена остаётся в TaskScope для следующего close().
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        RuntimeException failure = null;
        try {
            taskScope.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        running = false;

        if (!cachesCleared) {
            try {
                clearCaches.run();
                cachesCleared = true;
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Не удалось очистить ScrollCooldownTask caches");
                }
                failure.addSuppressed(exception);
            }
        }

        if (failure != null) {
            throw failure;
        }
        closed = true;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public int ownedTaskCount() {
        return taskScope.size();
    }
}
