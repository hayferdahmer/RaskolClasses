// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Единственный владелец периодического обновления снимков и PlaceholderAPI expansions.
 */
public final class PlaceholderRuntimeOwner implements AutoCloseable {

    private final Runnable refreshNow;
    private final Runnable clearSnapshots;
    private final PlaceholderRuntimeRegistration registration;
    private final PlaceholderRuntimeScheduler scheduler;
    private final TaskScope taskScope = new TaskScope();

    private boolean startAttempted;
    private boolean registrationAttempted;
    private boolean running;
    private boolean closed;

    public PlaceholderRuntimeOwner(
            Runnable refreshNow,
            Runnable clearSnapshots,
            PlaceholderRuntimeRegistration registration,
            PlaceholderRuntimeScheduler scheduler) {
        this.refreshNow = Objects.requireNonNull(refreshNow, "refreshNow");
        this.clearSnapshots = Objects.requireNonNull(clearSnapshots, "clearSnapshots");
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Сначала публикует начальный снимок, затем запускает producer и expansions. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Placeholder runtime уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            refreshNow.run();
            TaskHandle task = Objects.requireNonNull(scheduler.scheduleRefresh(), "refreshTask");
            taskScope.register(task);
            registrationAttempted = true;
            registration.register();
            running = true;
        } catch (RuntimeException startupFailure) {
            rollbackStartup(startupFailure);
            throw startupFailure;
        }
    }

    private void rollbackStartup(RuntimeException startupFailure) {
        try {
            taskScope.close();
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }

        unregisterAfterFailure(startupFailure);
        try {
            clearSnapshots.run();
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }
        running = false;
    }

    private void unregisterAfterFailure(RuntimeException startupFailure) {
        if (!registrationAttempted) {
            return;
        }
        try {
            registration.unregister();
            registrationAttempted = false;
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }
    }

    /** Останавливает producer, снимает expansions и очищает опубликованные снимки. */
    public synchronized void stop() {
        if (closed) {
            return;
        }
        RuntimeException failure = null;

        try {
            taskScope.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }

        if (registrationAttempted) {
            try {
                registration.unregister();
                registrationAttempted = false;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }

        try {
            clearSnapshots.run();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }

        running = false;
        if (failure == null) {
            closed = true;
            return;
        }
        throw failure;
    }

    @Override
    public void close() {
        stop();
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

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) {
            current = new IllegalStateException("Не удалось полностью остановить Placeholder runtime");
        }
        current.addSuppressed(next);
        return current;
    }
}
