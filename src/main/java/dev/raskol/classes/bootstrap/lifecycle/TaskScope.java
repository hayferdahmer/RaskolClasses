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
    private final Object cancellationLock = new Object();
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
     * Задачи, отмену которых не удалось подтвердить, остаются в области
     * для повторной попытки.
     */
    public void cancelAll() {
        synchronized (cancellationLock) {
            List<TaskHandle> snapshot;
            synchronized (lock) {
                snapshot = List.copyOf(tasks);
            }

            List<TaskHandle> completed = new ArrayList<>();
            RuntimeException failure = null;

            for (TaskHandle task : snapshot) {
                try {
                    task.cancel();
                    if (task.isCancelled()) {
                        completed.add(task);
                    } else {
                        failure = appendFailure(
                                failure,
                                new IllegalStateException("Задача осталась активной после отмены"));
                    }
                } catch (RuntimeException exception) {
                    failure = appendFailure(failure, exception);
                    try {
                        if (task.isCancelled()) {
                            completed.add(task);
                        }
                    } catch (RuntimeException statusException) {
                        failure = appendFailure(failure, statusException);
                    }
                }
            }

            synchronized (lock) {
                tasks.removeIf(task -> containsIdentity(completed, task));
            }

            if (failure != null) {
                throw failure;
            }
        }
    }

    /** Возвращает количество задач, которыми область ещё владеет. */
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
     * Если отмена части задач завершилась ошибкой, повторный close()
     * повторит попытку для оставшихся задач.
     */
    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
        }
        cancelAll();
    }

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) {
            current = new IllegalStateException("Не удалось отменить одну или несколько задач");
        }
        current.addSuppressed(next);
        return current;
    }

    private static boolean containsIdentity(List<TaskHandle> handles, TaskHandle candidate) {
        for (TaskHandle handle : handles) {
            if (handle == candidate) {
                return true;
            }
        }
        return false;
    }
}
