// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.runtime;

import java.util.Objects;

/**
 * Координирует lifecycle listener и отложенных уведомлений CooldownManager.
 *
 * <p>Состояние кулдаунов и их persistence остаются в CooldownManager.
 * Этот владелец отвечает только за регистрацию listener и остановку
 * принадлежащих менеджеру Bukkit-задач уведомлений.</p>
 */
public final class CooldownRuntimeOwner implements AutoCloseable {

    private final CooldownRuntimeRegistration registration;
    private final Runnable cancelNotificationTasks;

    private boolean startAttempted;
    private boolean registrationAttempted;
    private boolean notificationTasksCancelled;
    private boolean running;
    private boolean closed;

    public CooldownRuntimeOwner(CooldownRuntimeRegistration registration, Runnable cancelNotificationTasks) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.cancelNotificationTasks = Objects.requireNonNull(cancelNotificationTasks, "cancelNotificationTasks");
    }

    /** Регистрирует listener ровно один раз. */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (startAttempted || closed) {
            throw new IllegalStateException("Runtime кулдаунов уже запускался или закрыт");
        }

        startAttempted = true;
        registrationAttempted = true;
        try {
            registration.register();
            running = true;
        } catch (RuntimeException startupFailure) {
            unregisterAfterFailure(startupFailure);
            throw startupFailure;
        }
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

    /**
     * Сначала отменяет уведомления, затем снимает listener.
     * Ошибка одной операции не пропускает вторую; неуспешная операция
     * повторяется при следующем close().
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!startAttempted) {
            closed = true;
            return;
        }

        RuntimeException failure = null;

        if (!notificationTasksCancelled) {
            try {
                cancelNotificationTasks.run();
                notificationTasksCancelled = true;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }

        if (registrationAttempted) {
            try {
                registration.unregister();
                registrationAttempted = false;
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

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) {
            current = new IllegalStateException("Не удалось полностью остановить runtime кулдаунов");
        }
        current.addSuppressed(next);
        return current;
    }
}
