// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Владелец listener и периодического sweep для HpAttributeSync. */
public final class HpAttributeSyncRuntimeOwner implements AutoCloseable {

    private final HpAttributeSyncRegistration registration;
    private final HpAttributeSyncScheduler scheduler;
    private final long periodTicks;
    private final TaskScope tasks = new TaskScope();

    private boolean startAttempted;
    private boolean registrationAttempted;
    private boolean running;
    private boolean closed;

    public HpAttributeSyncRuntimeOwner(
            HpAttributeSyncRegistration registration, HpAttributeSyncScheduler scheduler, long periodTicks) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks должен быть положительным");
        }
        this.periodTicks = periodTicks;
    }

    /** Регистрирует listener и запускает sweep один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("HP attribute runtime уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            registrationAttempted = true;
            registration.register();
            TaskHandle task = Objects.requireNonNull(scheduler.scheduleSweep(periodTicks), "sweepTask");
            tasks.register(task);
            running = true;
        } catch (RuntimeException startupFailure) {
            rollback(startupFailure);
            throw startupFailure;
        }
    }

    private void rollback(RuntimeException startupFailure) {
        try {
            tasks.close();
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }
        unregisterAfterFailure(startupFailure);
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

    /** Останавливает sweep, затем снимает listener; неуспешные действия можно повторить. */
    public synchronized void stop() {
        if (closed) {
            return;
        }
        if (!startAttempted) {
            tasks.close();
            closed = true;
            return;
        }

        RuntimeException failure = null;
        try {
            tasks.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }

        if (registrationAttempted) {
            try {
                registration.unregister();
                registrationAttempted = false;
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }

        running = false;
        if (failure != null) {
            throw failure;
        }
        closed = true;
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
        return tasks.size();
    }

    private static RuntimeException merge(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return new IllegalStateException("Не удалось полностью остановить HP attribute runtime", next);
        }
        current.addSuppressed(next);
        return current;
    }
}
