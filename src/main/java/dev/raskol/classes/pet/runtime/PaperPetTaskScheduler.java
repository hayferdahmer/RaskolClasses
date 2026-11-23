// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import java.util.Objects;

import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;
import dev.raskol.classes.pet.PetService;

/** Paper-адаптер расписания задач PetService. */
public final class PaperPetTaskScheduler implements PetTaskScheduler {

    private final PetService petService;

    public PaperPetTaskScheduler(PetService petService) {
        this.petService = Objects.requireNonNull(petService, "petService");
    }

    @Override
    public TaskHandle scheduleTickTask() {
        return new CompositeTaskHandle(
                new BukkitTaskHandle(petService.startTickTask()),
                new BukkitTaskHandle(petService.startAttackTask()));
    }

    @Override
    public TaskHandle scheduleOrphanScan() {
        return new BukkitTaskHandle(petService.scheduleOrphanScan());
    }

    private static final class CompositeTaskHandle implements TaskHandle {

        private final java.util.List<TaskHandle> tasks;

        private CompositeTaskHandle(TaskHandle... tasks) {
            this.tasks = java.util.List.of(tasks);
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

    private static final class BukkitTaskHandle implements TaskHandle {

        private final BukkitTask task;

        private BukkitTaskHandle(BukkitTask task) {
            this.task = Objects.requireNonNull(task, "task");
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
