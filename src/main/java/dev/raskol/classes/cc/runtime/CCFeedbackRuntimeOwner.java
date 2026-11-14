// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
 // SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.bootstrap.lifecycle.TaskScope;

/**
 * Владелец временных ресурсов CC feedback.
 * Состояние CC/DR и периодический тик остаются вне этого компонента.
 */
public final class CCFeedbackRuntimeOwner implements AutoCloseable {
    private final CCFeedbackScheduler scheduler;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private boolean closed;

    public CCFeedbackRuntimeOwner(CCFeedbackScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Заменяет набор ресурсов цели и назначает их автоматическую очистку.
     * При ошибке очистки предыдущей записи новая запись не публикуется.
     */
    public synchronized void replace(UUID targetId, List<? extends CCFeedbackResource> resources, long delayTicks) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(resources, "resources");
        if (closed) {
            throw new IllegalStateException("CC feedback runtime закрыт");
        }
        List<CCFeedbackResource> owned = List.copyOf(resources);
        if (owned.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("CC feedback resource не может быть null");
        }
        clear(targetId);
        Entry entry = new Entry(owned);
        entries.put(targetId, entry);
        try {
            TaskHandle task = Objects.requireNonNull(
                    scheduler.schedule(() -> expire(targetId, entry), Math.max(1L, delayTicks)),
                    "cleanupTask");
            entry.tasks.register(task);
        } catch (RuntimeException failure) {
            if (entries.get(targetId) == entry) {
                entries.remove(targetId);
            }
            try {
                removeResources(entry);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                entries.put(targetId, entry);
            }
            throw failure;
        }
    }

    /** Явная очистка конкретной цели. */
    public synchronized void clear(UUID targetId) {
        if (targetId == null) {
            return;
        }
        Entry entry = entries.get(targetId);
        if (entry == null) {
            return;
        }
        cleanupEntry(targetId, entry);
    }

    private synchronized void expire(UUID targetId, Entry expected) {
        if (entries.get(targetId) != expected) {
            removeResources(expected);
            return;
        }
        cleanupEntry(targetId, expected);
    }

    private void cleanupEntry(UUID targetId, Entry entry) {
        RuntimeException failure = null;
        try {
            entry.tasks.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            removeResources(entry);
        } catch (RuntimeException exception) {
            failure = merge(failure, exception);
        }
        if (entry.isEmpty()) {
            entries.remove(targetId, entry);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void removeResources(Entry entry) {
        RuntimeException failure = null;
        for (int i = entry.resources.size() - 1; i >= 0; i--) {
            CCFeedbackResource resource = entry.resources.get(i);
            try {
                resource.remove();
                entry.resources.remove(i);
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        if (closed && entries.isEmpty()) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (Map.Entry<UUID, Entry> current : List.copyOf(entries.entrySet())) {
            try {
                cleanupEntry(current.getKey(), current.getValue());
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized int trackedTargetCount() {
        return entries.size();
    }

    public synchronized int trackedResourceCount() {
        return entries.values().stream().mapToInt(entry -> entry.resources.size()).sum();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private static RuntimeException merge(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return new IllegalStateException("Не удалось полностью очистить CC feedback resources", next);
        }
        current.addSuppressed(next);
        return current;
    }

    private static final class Entry {
        private final List<CCFeedbackResource> resources;
        private final TaskScope tasks = new TaskScope();

        private Entry(List<CCFeedbackResource> resources) {
            this.resources = new ArrayList<>(resources);
        }

        private boolean isEmpty() {
            return resources.isEmpty() && tasks.size() == 0;
        }
    }
}
