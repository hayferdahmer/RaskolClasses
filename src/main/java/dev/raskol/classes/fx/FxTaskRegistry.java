// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.fx;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.scheduler.BukkitTask;

/** Реестр handles задач, принадлежащих FxService. */
final class FxTaskRegistry implements AutoCloseable {

    private final Set<BukkitTask> tasks = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    synchronized void register(BukkitTask task) {
        Objects.requireNonNull(task, "task");
        tasks.add(task);
        if (closed) {
            IllegalStateException failure = new IllegalStateException("FX task registry закрыт");
            try {
                cancel(task);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    synchronized void release(BukkitTask task) {
        if (task != null) {
            tasks.remove(task);
        }
    }

    synchronized void cancel(BukkitTask task) {
        if (task == null || !tasks.contains(task)) {
            return;
        }
        try {
            if (!task.isCancelled()) {
                task.cancel();
            }
        } catch (RuntimeException failure) {
            if (task.isCancelled()) {
                tasks.remove(task);
            }
            throw failure;
        }
        if (task.isCancelled()) {
            tasks.remove(task);
        }
    }

    synchronized boolean isTracked(BukkitTask task) {
        return task != null && tasks.contains(task);
    }

    synchronized int trackedCount() {
        return tasks.size();
    }

    synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        RuntimeException failure = null;
        for (BukkitTask task : List.copyOf(tasks)) {
            try {
                cancel(task);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Не удалось отменить все FX tasks");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
