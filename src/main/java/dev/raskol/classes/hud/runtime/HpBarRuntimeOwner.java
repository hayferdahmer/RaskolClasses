// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;
import java.util.function.LongSupplier;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/** Владелец listener и периодического producer HP bar; здоровье и persistence остаются в сервисе. */
public final class HpBarRuntimeOwner implements AutoCloseable {
    private final HpBarRuntimeRegistration registration;
    private final HpBarTaskScheduler scheduler;
    private final Runnable tick;
    private final LongSupplier periodTicks;
    private final TaskScope tasks = new TaskScope();

    private boolean registrationAttempted;
    private boolean registered;
    private boolean startAttempted;
    private boolean running;
    private boolean closed;

    public HpBarRuntimeOwner(
            HpBarRuntimeRegistration registration,
            HpBarTaskScheduler scheduler,
            Runnable tick,
            LongSupplier periodTicks) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tick = Objects.requireNonNull(tick, "tick");
        this.periodTicks = Objects.requireNonNull(periodTicks, "periodTicks");
    }

    /** Регистрирует join/quit/respawn listener отдельно от периодического tick. */
    public synchronized void register() {
        ensureOpen();
        if (registered) {
            return;
        }
        if (registrationAttempted) {
            throw new IllegalStateException("Регистрация HP bar listener уже предпринималась");
        }
        registrationAttempted = true;
        try {
            registration.register();
            registered = true;
        } catch (RuntimeException failure) {
            unregisterAfterFailure(failure);
            throw failure;
        }
    }

    /** Запускает периодический HP tick после регистрации listener. */
    public synchronized void start() {
        ensureOpen();
        if (!registered) {
            throw new IllegalStateException("HP bar listener должен быть зарегистрирован до запуска tick");
        }
        if (running) {
            return;
        }
        if (startAttempted) {
            throw new IllegalStateException("Запуск HP bar runtime уже предпринимался");
        }
        startAttempted = true;
        try {
            TaskHandle task = Objects.requireNonNull(
                    scheduler.schedule(tick, Math.max(1L, periodTicks.getAsLong())), "hpBarTask");
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
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
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
            throw new IllegalStateException("HP bar runtime уже закрыт");
        }
    }
}
