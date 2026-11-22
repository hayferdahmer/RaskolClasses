// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;
import java.util.function.LongSupplier;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Владелец периодического BossBar producer и жизненного цикла созданных полос.
 *
 * <p>Сам BossBarService сохраняет только логику отображения и состояние полос.
 * При перезагрузке конфигурации область задач заменяется, поскольку закрытый
 * TaskScope нельзя повторно открыть.</p>
 */
public final class BossBarRuntimeOwner implements AutoCloseable {

    private final BossBarTaskScheduler scheduler;
    private final LongSupplier periodTicks;
    private final Runnable clearBars;

    private TaskScope tasks = new TaskScope();
    private boolean startAttempted;
    private boolean running;
    private boolean closed;

    public BossBarRuntimeOwner(BossBarTaskScheduler scheduler, LongSupplier periodTicks, Runnable clearBars) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.periodTicks = Objects.requireNonNull(periodTicks, "periodTicks");
        this.clearBars = Objects.requireNonNull(clearBars, "clearBars");
    }

    /** Запускает producer один раз; повторный вызов работающего owner безопасен. */
    public synchronized void start() {
        ensureOpen();
        if (running) {
            return;
        }
        if (startAttempted) {
            throw new IllegalStateException("Запуск BossBar runtime уже предпринимался");
        }
        startAttempted = true;
        schedule();
    }

    /** Перезапускает producer и очищает полосы после применения новой конфигурации. */
    public synchronized void restart() {
        ensureOpen();
        RuntimeException failure = null;
        try {
            tasks.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        running = tasks.size() > 0;
        try {
            clearBars.run();
        } catch (RuntimeException exception) {
            failure = merge(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
        tasks = new TaskScope();
        startAttempted = true;
        schedule();
    }

    private void schedule() {
        long period = Math.max(1L, periodTicks.getAsLong());
        try {
            TaskHandle task = Objects.requireNonNull(scheduler.schedule(period), "bossBarTask");
            tasks.register(task);
            running = true;
        } catch (RuntimeException failure) {
            try {
                tasks.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            running = tasks.size() > 0;
            throw failure;
        }
    }

    /** Отменяет producer и независимо пытается удалить все созданные BossBar. */
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
        try {
            clearBars.run();
        } catch (RuntimeException exception) {
            failure = merge(failure, exception);
        }
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

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("BossBar runtime уже закрыт");
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
