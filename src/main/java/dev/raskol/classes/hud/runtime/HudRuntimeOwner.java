// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Владелец регистрации событий и периодической работы RaskolHUD.
 *
 * <p>Класс не зависит от Bukkit API: Paper-адаптеры предоставляют порты
 * планировщика и событий сессии, а владелец управляет их жизненным циклом.</p>
 */
public final class HudRuntimeOwner implements AutoCloseable {

    private final HudTaskScheduler scheduler;
    private final HudSessionEvents sessionEvents;
    private final HudOutputCleanup outputCleanup;
    private final Runnable refreshAction;
    private final long initialDelayTicks;
    private final long periodTicks;
    private final TaskScope tasks = new TaskScope();

    private HudRegistration sessionRegistration;
    private boolean registered;
    private boolean started;
    private boolean closing;
    private boolean closed;

    public HudRuntimeOwner(
            HudTaskScheduler scheduler,
            HudSessionEvents sessionEvents,
            HudOutputCleanup outputCleanup,
            Runnable refreshAction,
            long initialDelayTicks,
            long periodTicks) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sessionEvents = Objects.requireNonNull(sessionEvents, "sessionEvents");
        this.outputCleanup = Objects.requireNonNull(outputCleanup, "outputCleanup");
        this.refreshAction = Objects.requireNonNull(refreshAction, "refreshAction");
        if (initialDelayTicks < 0) {
            throw new IllegalArgumentException("initialDelayTicks не может быть отрицательным");
        }
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks должен быть положительным");
        }
        this.initialDelayTicks = initialDelayTicks;
        this.periodTicks = periodTicks;
    }

    /** Регистрирует очистку per-player состояния при выходе игрока. */
    public synchronized void register() {
        ensureOpen();
        if (registered) {
            return;
        }

        HudRegistration registration = Objects.requireNonNull(
                sessionEvents.onQuit(outputCleanup::clearPlayer), "session registration");
        sessionRegistration = registration;
        registered = true;
    }

    /**
     * Запускает периодическое обновление. Повторный вызов не создаёт
     * дополнительную задачу.
     */
    public synchronized void start() {
        ensureOpen();
        if (!registered) {
            throw new IllegalStateException("События HUD должны быть зарегистрированы до запуска");
        }
        if (started) {
            return;
        }

        try {
            TaskHandle task = Objects.requireNonNull(
                    scheduler.scheduleRepeating(refreshAction, initialDelayTicks, periodTicks),
                    "scheduled HUD task");
            tasks.register(task);
            started = true;
        } catch (RuntimeException failure) {
            appendFailure(failure, tasks::cancelAll);
            started = tasks.size() > 0;
            appendFailure(failure, outputCleanup::clearAll);
            throw failure;
        }
    }

    /**
     * Останавливает producer до очистки output. Область задач остаётся
     * открытой, поэтому runtime можно запустить повторно после reload.
     */
    public synchronized void stop() {
        RuntimeException failure = null;
        try {
            tasks.cancelAll();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        started = tasks.size() > 0;
        try {
            outputCleanup.clearAll();
        } catch (RuntimeException exception) {
            failure = merge(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized boolean isRegistered() {
        return registered;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /** Закрывает runtime, продолжая cleanup после отдельных ошибок. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closing = true;

        RuntimeException failure = null;
        try {
            stop();
        } catch (RuntimeException exception) {
            failure = exception;
        }

        if (sessionRegistration != null) {
            try {
                sessionRegistration.close();
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
            if (failure == null) {
                sessionRegistration = null;
                registered = false;
            }
        }

        try {
            tasks.close();
        } catch (RuntimeException exception) {
            failure = merge(failure, exception);
        }
        started = tasks.size() > 0;

        if (failure != null) {
            throw failure;
        }
        closed = true;
        closing = false;
    }

    private void ensureOpen() {
        if (closed || closing) {
            throw new IllegalStateException("RaskolHUD runtime уже закрыт");
        }
    }

    private static void appendFailure(RuntimeException primary, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
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
