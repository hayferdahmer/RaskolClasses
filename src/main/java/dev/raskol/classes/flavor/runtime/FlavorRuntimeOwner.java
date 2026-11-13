// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.flavor.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Владелец listener и периодической ауры королевских вкусов. */
public final class FlavorRuntimeOwner implements AutoCloseable {

    private final FlavorRuntimeRegistration registration;
    private final FlavorTaskScheduler scheduler;
    private final TaskScope tasks = new TaskScope();

    private boolean registrationAttempted;
    private boolean registered;
    private boolean startAttempted;
    private boolean running;
    private boolean closed;

    public FlavorRuntimeOwner(FlavorRuntimeRegistration registration, FlavorTaskScheduler scheduler) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Регистрирует listener отдельно от запуска aura producer. */
    public synchronized void register() {
        ensureOpen();
        if (registered) {
            return;
        }
        if (registrationAttempted) {
            throw new IllegalStateException("Регистрация flavor listener уже предпринималась");
        }

        registrationAttempted = true;
        try {
            registration.register();
            registered = true;
        } catch (RuntimeException failure) {
            try {
                registration.unregister();
                registrationAttempted = false;
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** Запускает aura producer после регистрации listener. */
    public synchronized void start() {
        ensureOpen();
        if (!registered) {
            throw new IllegalStateException("Flavor listener должен быть зарегистрирован до запуска ауры");
        }
        if (running) {
            return;
        }
        if (startAttempted) {
            throw new IllegalStateException("Запуск flavor runtime уже предпринимался");
        }

        startAttempted = true;
        try {
            TaskHandle task = Objects.requireNonNull(scheduler.scheduleAura(), "auraTask");
            tasks.register(task);
            running = true;
        } catch (RuntimeException failure) {
            try {
                tasks.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            running = tasks.size() > 0;
            unregisterAfterFailure(failure);
            throw failure;
        }
    }

    private void unregisterAfterFailure(RuntimeException failure) {
        if (!registrationAttempted) {
            return;
        }
        try {
            registration.unregister();
            registrationAttempted = false;
            registered = false;
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /** Останавливает aura producer и снимает listener с независимыми попытками cleanup. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        RuntimeException failure = null;

        try {
            tasks.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        running = tasks.size() > 0;

        if (registrationAttempted) {
            try {
                registration.unregister();
                registrationAttempted = false;
                registered = false;
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }

        if (failure != null) {
            throw failure;
        }
        closed = true;
    }

    public synchronized boolean isRegistered() {
        return registered;
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

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Flavor runtime уже закрыт");
        }
    }

    private static RuntimeException merge(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
