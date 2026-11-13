// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Владелец отменяемых задач одного модуля.
 *
 * <p>Область не зависит от Bukkit API. Адаптеры планировщика передают сюда
 * реализации {@link TaskHandle}.</p>
 */
public final class TaskScope implements AutoCloseable {

    private final Object lock = new Object();
    private final List<TaskHandle> tasks = new ArrayList<>();
    private boolean closed;

    /**
     * Регистрирует задачу в области владения.
     *
     * @throws NullPointerException если задача равна null
     * @throws IllegalStateException если область уже закрыта
     */
    public void register(TaskHandle task) {
        Objects.requireNonNull(task, "task");

        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Область задач уже закрыта");
            }
            tasks.add(task);
        }
    }

    /**
     * Отменяет все зарегистрированные задачи, не закрывая область.
     * Ошибка одной задачи не препятствует отмене остальных.
     */
    public void cancelAll() {
        cancelSnapshot(snapshotAndClear(false));
    }

    /** Возвращает количество задач, которыми область владеет в данный момент. */
    public int size() {
        synchronized (lock) {
            return tasks.size();
        }
    }

    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    /**
     * Закрывает область и отменяет все её задачи.
     * Повторный вызов безопасен.
     */
    @Override
    public void close() {
        cancelSnapshot(snapshotAndClear(true));
    }

    private List<TaskHandle> snapshotAndClear(boolean close) {
        synchronized (lock) {
            if (close) {
                closed = true;
            }
            List<TaskHandle> snapshot = List.copyOf(tasks);
            tasks.clear();
            return snapshot;
        }
    }

    private static void cancelSnapshot(List<TaskHandle> snapshot) {
        RuntimeException failure = null;

        for (TaskHandle task : snapshot) {
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Не удалось отменить одну или несколько задач");
                }
                failure.addSuppressed(exception);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
