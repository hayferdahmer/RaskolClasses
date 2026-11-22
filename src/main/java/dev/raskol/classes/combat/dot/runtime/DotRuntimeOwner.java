// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Владелец только runtime-жизненного цикла DoT: listener и периодического producer.
 * Реестр DotInstance, стеки, сроки жизни и расчёт урона остаются в DotService.
 */
public final class DotRuntimeOwner implements AutoCloseable {
    private final DotRuntimeRegistration registration;
    private final DotTaskScheduler scheduler;
    private final Runnable tick;
    private final TaskScope tasks = new TaskScope();
    private boolean startAttempted;
    private boolean registrationAttempted;
    private boolean running;
    private boolean closed;

    public DotRuntimeOwner(DotRuntimeRegistration registration, DotTaskScheduler scheduler, Runnable tick) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    public synchronized void start() {
        if (running) return;
        if (startAttempted || closed) throw new IllegalStateException("DoT runtime уже запускался или закрыт");
        startAttempted = true;
        try {
            registrationAttempted = true;
            registration.register();
            tasks.register(Objects.requireNonNull(scheduler.scheduleTick(tick), "dotTickTask"));
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
        if (!registrationAttempted) return;
        try {
            registration.unregister();
            registrationAttempted = false;
        } catch (RuntimeException cleanupFailure) {
            startupFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
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
        if (failure != null) throw failure;
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
        if (current == null) return new IllegalStateException("Не удалось полностью остановить DoT runtime", next);
        current.addSuppressed(next);
        return current;
    }
}
