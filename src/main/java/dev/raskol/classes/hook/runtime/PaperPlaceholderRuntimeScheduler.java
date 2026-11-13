// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook.runtime;

import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер периодического обновления снимков PlaceholderAPI. */
public final class PaperPlaceholderRuntimeScheduler implements PlaceholderRuntimeScheduler {

    private static final long REFRESH_PERIOD_TICKS = 5L;

    private final JavaPlugin plugin;
    private final Runnable refreshAction;

    public PaperPlaceholderRuntimeScheduler(JavaPlugin plugin, Runnable refreshAction) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.refreshAction = Objects.requireNonNull(refreshAction, "refreshAction");
    }

    @Override
    public TaskHandle scheduleRefresh() {
        BukkitTask task = plugin.getServer()
                .getScheduler()
                .runTaskTimer(plugin, refreshAction, REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
        return new BukkitTaskHandle(task);
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
