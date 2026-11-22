// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Управляет одноразовыми задачами каналов Чернокнижника по UUID кастера. */
public final class WarlockChannelTaskOwner implements AutoCloseable {

    private final Map<UUID, List<TaskHandle>> tasksByCaster = new HashMap<>();
    private boolean closed;

    /** Регистрирует handle сразу после создания Bukkit-задачи. */
    public synchronized void register(UUID casterId, TaskHandle task) {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(task, "task");
        if (closed) {
            throw new IllegalStateException("Warlock channel task owner is closed");
        }
        tasksByCaster.computeIfAbsent(casterId, ignored -> new ArrayList<>()).add(task);
    }

    /** Отменяет задачи одного кастера; неуспешные handles остаются для повтора. */
    public synchronized void cancel(UUID casterId) {
        if (casterId == null) {
            return;
        }
        List<TaskHandle> tasks = tasksByCaster.get(casterId);
        if (tasks == null) {
            return;
        }
        cancelTracked(tasks);
        if (tasks.isEmpty()) {
            tasksByCaster.remove(casterId);
        }
    }

    /** Удаляет handles завершившегося канала без повторной отмены. */
    public synchronized void complete(UUID casterId) {
        if (casterId != null) {
            tasksByCaster.remove(casterId);
        }
    }

    public synchronized Set<UUID> casterIds() {
        return Set.copyOf(tasksByCaster.keySet());
    }

    public synchronized int trackedCasterCount() {
        return tasksByCaster.size();
    }

    public synchronized int trackedTaskCount() {
        return tasksByCaster.values().stream().mapToInt(List::size).sum();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        RuntimeException failure = null;
        for (UUID casterId : List.copyOf(tasksByCaster.keySet())) {
            try {
                cancel(casterId);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to cancel one or more Warlock channel tasks");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void cancelTracked(List<TaskHandle> tasks) {
        RuntimeException failure = null;
        for (TaskHandle task : List.copyOf(tasks)) {
            try {
                if (!task.isCancelled()) {
                    task.cancel();
                }
                if (task.isCancelled()) {
                    removeIdentity(tasks, task);
                }
            } catch (RuntimeException exception) {
                if (task.isCancelled()) {
                    removeIdentity(tasks, task);
                }
                if (failure == null) {
                    failure = new IllegalStateException("Failed to cancel one or more Warlock channel tasks");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void removeIdentity(List<TaskHandle> tasks, TaskHandle target) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index) == target) {
                tasks.remove(index);
                return;
            }
        }
    }
}
