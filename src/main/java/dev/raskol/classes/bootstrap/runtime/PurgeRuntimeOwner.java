// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Единственный владелец глобального cross-domain purge producer. */
public final class PurgeRuntimeOwner implements AutoCloseable {

    private final PurgeTaskScheduler scheduler;
    private final TaskScope taskScope = new TaskScope();
    private boolean startAttempted;
    private boolean running;
    private boolean closed;

    public PurgeRuntimeOwner(PurgeTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Purge runtime уже запускался или закрыт");
        }
        startAttempted = true;
        try {
            taskScope.register(Objects.requireNonNull(scheduler.schedulePurge(), "purgeTask"));
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
