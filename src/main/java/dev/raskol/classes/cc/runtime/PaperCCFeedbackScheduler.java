// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
 // SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер однократной отложенной очистки CC feedback. */
public final class PaperCCFeedbackScheduler implements CCFeedbackScheduler {
    private final Plugin plugin;

    public PaperCCFeedbackScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle schedule(Runnable cleanup, long delayTicks) {
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskLater(plugin, Objects.requireNonNull(cleanup, "cleanup"), Math.max(1L, delayTicks));
        return new BukkitTaskHandle(task);
    }

    private record BukkitTaskHandle(BukkitTask task) implements TaskHandle {
        private BukkitTaskHandle {
            Objects.requireNonNull(task, "task");
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
