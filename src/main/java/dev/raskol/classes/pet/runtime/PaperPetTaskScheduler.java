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
        BukkitTask lifecycleTask = petService.startTickTask();
        try {
            BukkitTask attackTask = petService.startAttackTask();
            return new CompositeTaskHandle(new BukkitTaskHandle(lifecycleTask), new BukkitTaskHandle(attackTask));
        } catch (RuntimeException startupFailure) {
            try {
                lifecycleTask.cancel();
            } catch (RuntimeException cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }
            throw startupFailure;
        }
    }

    @Override
    public TaskHandle scheduleOrphanScan() {
        return new BukkitTaskHandle(petService.scheduleOrphanScan());
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
