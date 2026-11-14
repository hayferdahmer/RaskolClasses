// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Единственный владелец периодической runtime-задачи инсталляций. */
public final class InstallationRuntimeOwner implements AutoCloseable {

    private final InstallationTaskScheduler scheduler;
    private final TaskScope taskScope = new TaskScope();

    private boolean startAttempted;
    private boolean running;
    private boolean closed;

    public InstallationRuntimeOwner(InstallationTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Запускает sweep ровно один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Runtime инсталляций уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            taskScope.register(Objects.requireNonNull(scheduler.scheduleSweep(), "sweepTask"));
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

    /** Останавливает sweep; неподтверждённая отмена остаётся доступной для повтора. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        taskScope.close();
        running = false;
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
