// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Единственный владелец listener и задач runtime петов.
 *
 * <p>При остановке сначала удаляет петов из мира, затем отменяет задачи
 * и только после этого снимает listener.</p>
 */
public final class PetRuntimeOwner implements AutoCloseable {

    private final PetRuntimeRegistration registration;
    private final PetTaskScheduler scheduler;
    private final Runnable despawnAll;
    private final TaskScope taskScope = new TaskScope();

    private boolean startAttempted;
    private boolean listenerRegistrationAttempted;
    private boolean running;
    private boolean despawnCompleted;
    private boolean closed;

    public PetRuntimeOwner(
            PetRuntimeRegistration registration,
            PetTaskScheduler scheduler,
            Runnable despawnAll) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.despawnAll = Objects.requireNonNull(despawnAll, "despawnAll");
    }

    /** Запускает listener и обе задачи ровно один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Runtime петов уже запускался или закрыт");
        }

        startAttempted = true;
        try {
            listenerRegistrationAttempted = true;
            registration.register();
            taskScope.register(Objects.requireNonNull(scheduler.scheduleTickTask(), "tickTask"));
            taskScope.register(Objects.requireNonNull(scheduler.scheduleOrphanScan(), "orphanScanTask"));
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
        running = false;
    }

    private void unregisterAfterFailure(RuntimeException startupFailure) {
        if (!listenerRegistrationAttempted) {
            return;
        }
        try {
            registration.unregister();
            listenerRegistrationAttempted = false;
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Останавливает runtime, продолжая cleanup после отдельных ошибок.
     * Повторный вызов повторяет только те операции, завершение которых
     * не удалось подтвердить.
     */
    public synchronized void stop() {
        if (closed) {
            return;
        }
        if (!startAttempted) {
            taskScope.close();
            closed = true;
            return;
        }

        RuntimeException failure = null;

        if (!despawnCompleted) {
            try {
                despawnAll.run();
                despawnCompleted = true;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }

        try {
            taskScope.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }

        if (listenerRegistrationAttempted) {
            try {
                registration.unregister();
                listenerRegistrationAttempted = false;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
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
            current = new IllegalStateException("Не удалось полностью остановить runtime петов");
        }
        current.addSuppressed(next);
        return current;
    }
}
