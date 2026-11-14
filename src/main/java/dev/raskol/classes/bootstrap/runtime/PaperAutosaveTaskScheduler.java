// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер глобальной autosave-задачи. */
public final class PaperAutosaveTaskScheduler implements AutosaveTaskScheduler {

    private final Plugin plugin;
    private final Runnable autosave;
    private final long intervalTicks;

    public PaperAutosaveTaskScheduler(Plugin plugin, Runnable autosave, long intervalTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.autosave = Objects.requireNonNull(autosave, "autosave");
        if (intervalTicks < 1L) {
            throw new IllegalArgumentException("intervalTicks должен быть не меньше 1");
        }
        this.intervalTicks = intervalTicks;
    }

    @Override
    public TaskHandle scheduleAutosave() {
        return new BukkitTaskHandle(plugin.getServer().getScheduler()
                .runTaskTimer(plugin, autosave, intervalTicks, intervalTicks));
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
