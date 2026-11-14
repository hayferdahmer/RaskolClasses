// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.resource.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Единственный владелец периодического producer обновления ресурсов. */
public final class ResourceRuntimeOwner implements AutoCloseable {

    private final ResourceTaskScheduler scheduler;
    private final TaskScope taskScope = new TaskScope();

    private boolean startAttempted;
    private boolean running;
    private boolean closed;

    public ResourceRuntimeOwner(ResourceTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Запускает периодическое обновление ресурсов ровно один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Resource runtime уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            taskScope.register(Objects.requireNonNull(scheduler.scheduleTick(), "resourceTickTask"));
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

    /** Отменяет producer; неподтверждённая отмена доступна для повторной попытки. */
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
