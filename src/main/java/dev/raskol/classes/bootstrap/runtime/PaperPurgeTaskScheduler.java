// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер глобальной purge-задачи. */
public final class PaperPurgeTaskScheduler implements PurgeTaskScheduler {

    private final Plugin plugin;
    private final Runnable purge;
    private final long intervalTicks;

    public PaperPurgeTaskScheduler(Plugin plugin, Runnable purge, long intervalTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.purge = Objects.requireNonNull(purge, "purge");
        if (intervalTicks < 1L) {
            throw new IllegalArgumentException("intervalTicks должен быть не меньше 1");
        }
        this.intervalTicks = intervalTicks;
    }

    @Override
    public TaskHandle schedulePurge() {
        return new BukkitTaskHandle(
                plugin.getServer().getScheduler().runTaskTimer(plugin, purge, intervalTicks, intervalTicks));
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
