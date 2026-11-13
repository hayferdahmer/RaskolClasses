// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
 // SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.foliant.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Владелец listener и периодического dirty-flush Фолианта.
 *
 * <p>Снимки YAML и запись в общий AsyncStorageWriter остаются обязанностью
 * FoliantService. Этот owner не сохраняет данные и не закрывает writer.</p>
 */
public final class FoliantRuntimeOwner implements AutoCloseable {

    private final FoliantRuntimeRegistration registration;
    private final FoliantRuntimeScheduler scheduler;
    private final long periodTicks;
    private final TaskScope tasks = new TaskScope();

    private boolean startAttempted;
    private boolean registrationAttempted;
    private boolean running;
    private boolean closed;

    public FoliantRuntimeOwner(
            FoliantRuntimeRegistration registration,
            FoliantRuntimeScheduler scheduler,
            long periodTicks) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks должен быть положительным");
        }
        this.periodTicks = periodTicks;
    }

    /** Регистрирует listener и запускает периодический flush один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Foliant runtime уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            registrationAttempted = true;
            registration.register();
            TaskHandle task = Objects.requireNonNull(scheduler.scheduleLockFlush(periodTicks), "flushTask");
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

    /** Останавливает producer flush и снимает listener; ошибки допускают повторный close. */
    @Override
    public synchronized void close() {
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
            return new IllegalStateException("Не удалось полностью остановить Foliant runtime", next);
        }
        current.addSuppressed(next);
        return current;
    }
}
