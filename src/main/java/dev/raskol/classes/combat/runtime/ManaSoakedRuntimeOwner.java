// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Единственный владелец периодического producer механики ManaSoaked. */
public final class ManaSoakedRuntimeOwner implements AutoCloseable {

    private final ManaSoakedTaskScheduler scheduler;
    private final Runnable clearTracking;
    private final TaskScope taskScope = new TaskScope();

    private boolean startAttempted;
    private boolean running;
    private boolean trackingCleared;
    private boolean closed;

    public ManaSoakedRuntimeOwner(ManaSoakedTaskScheduler scheduler, Runnable clearTracking) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clearTracking = Objects.requireNonNull(clearTracking, "clearTracking");
    }

    /** Запускает периодический producer ровно один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("ManaSoaked runtime уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            taskScope.register(Objects.requireNonNull(scheduler.scheduleTick(), "manaSoakedTask"));
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
     * Останавливает producer и очищает только tracking-кэш сервиса.
     * Ошибки очистки и отмены остаются доступными для повторного close().
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

        if (!trackingCleared) {
            try {
                clearTracking.run();
                trackingCleared = true;
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Не удалось очистить ManaSoaked tracking");
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
