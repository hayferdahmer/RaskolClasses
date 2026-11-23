// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import java.util.List;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Группирует несколько Paper-задач под одним lifecycle handle. */
final class CompositeTaskHandle implements TaskHandle {

    private final List<TaskHandle> tasks;

    CompositeTaskHandle(TaskHandle... tasks) {
        this.tasks = List.of(tasks);
    }

    @Override
    public void cancel() {
        RuntimeException failure = null;
        for (TaskHandle task : tasks) {
            try {
                task.cancel();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("Не удалось отменить все задачи петов");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isCancelled() {
        return tasks.stream().allMatch(TaskHandle::isCancelled);
    }
}
