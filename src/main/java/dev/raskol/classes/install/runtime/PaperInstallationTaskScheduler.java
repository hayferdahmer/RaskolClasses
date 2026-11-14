// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер периодического sweep инсталляций. */
public final class PaperInstallationTaskScheduler implements InstallationTaskScheduler {

    private static final long INITIAL_DELAY_TICKS = 20L;
    private static final long PERIOD_TICKS = 20L;

    private final Plugin plugin;
    private final Runnable sweep;

    public PaperInstallationTaskScheduler(Plugin plugin, Runnable sweep) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sweep = Objects.requireNonNull(sweep, "sweep");
    }

    @Override
    public TaskHandle scheduleSweep() {
        BukkitTask task = plugin.getServer()
                .getScheduler()
                .runTaskTimer(plugin, sweep, INITIAL_DELAY_TICKS, PERIOD_TICKS);
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
