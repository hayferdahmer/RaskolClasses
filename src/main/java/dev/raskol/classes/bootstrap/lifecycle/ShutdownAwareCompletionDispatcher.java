// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Отслеживает отложенные пользовательские callbacks, которые должны выполниться на главном потоке Paper.
 * При выключении плагина невыполненные callbacks подавляются и явно фиксируются в журнале.
 */
public final class ShutdownAwareCompletionDispatcher implements AutoCloseable {

    private final Object lock = new Object();
    private final Map<Long, PendingDispatch> pending = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Logger logger;
    private final TaskScheduler scheduler;
    private boolean closed;

    public ShutdownAwareCompletionDispatcher(Logger logger, TaskScheduler scheduler) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public boolean dispatch(String operation, Runnable action) {
        return dispatch(operation, action, () -> {});
    }

    /**
     * Ставит callback в очередь. onUndelivered не должен обращаться к Bukkit API.
     *
     * @return true, если задача принята планировщиком; false при закрытом dispatcher или отказе планировщика
     */
    public boolean dispatch(String operation, Runnable action, Runnable onUndelivered) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(onUndelivered, "onUndelivered");

        long id = sequence.incrementAndGet();
        PendingDispatch entry = new PendingDispatch(operation, action, onUndelivered);
        boolean rejectedByShutdown;
        synchronized (lock) {
            rejectedByShutdown = closed;
            if (!rejectedByShutdown) {
                pending.put(id, entry);
            }
        }
        if (rejectedByShutdown) {
            logUndelivered(operation, "dispatcher уже закрыт", null);
            runUndelivered(entry);
            return false;
        }

        try {
            scheduler.schedule(() -> execute(id));
            return true;
        } catch (RuntimeException exception) {
            PendingDispatch removed = remove(id);
            if (removed != null) {
                logUndelivered(operation, "Paper scheduler отклонил callback", exception);
                runUndelivered(removed);
            }
            return false;
        }
    }

    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    private void execute(long id) {
        PendingDispatch entry = remove(id);
        if (entry == null) {
            return;
        }
        try {
            entry.action().run();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Ошибка выполнения async completion: " + entry.operation(), exception);
        }
    }

    private PendingDispatch remove(long id) {
        synchronized (lock) {
            return pending.remove(id);
        }
    }

    @Override
    public void close() {
        List<PendingDispatch> undelivered;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            undelivered = new ArrayList<>(pending.values());
            pending.clear();
        }

        for (PendingDispatch entry : undelivered) {
            logUndelivered(entry.operation(), "плагин выключается; задача не будет выполнена", null);
            runUndelivered(entry);
        }
        if (!undelivered.isEmpty()) {
            logger.warning(
                    "Async completion: при выключении подавлено недоставленных callbacks: " + undelivered.size());
        }
    }

    private void logUndelivered(String operation, String reason, RuntimeException exception) {
        if (exception == null) {
            logger.warning("Async completion не доставлен [" + operation + "]: " + reason);
        } else {
            logger.log(Level.WARNING, "Async completion не доставлен [" + operation + "]: " + reason, exception);
        }
    }

    private void runUndelivered(PendingDispatch entry) {
        try {
            entry.onUndelivered().run();
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE, "Ошибка обработки недоставленного async completion: " + entry.operation(), exception);
        }
    }

    @FunctionalInterface
    public interface TaskScheduler {
        void schedule(Runnable task);
    }

    private record PendingDispatch(String operation, Runnable action, Runnable onUndelivered) {}
}
