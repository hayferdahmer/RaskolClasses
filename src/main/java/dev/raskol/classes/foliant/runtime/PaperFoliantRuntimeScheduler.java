// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.foliant.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер периодического dirty-flush Фолианта. */
public final class PaperFoliantRuntimeScheduler implements FoliantRuntimeScheduler {

    private final Plugin plugin;
    private final Runnable flushAction;

    public PaperFoliantRuntimeScheduler(Plugin plugin, Runnable flushAction) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.flushAction = Objects.requireNonNull(flushAction, "flushAction");
    }

    @Override
    public TaskHandle scheduleLockFlush(long periodTicks) {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks должен быть положительным");
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, flushAction, periodTicks, periodTicks);
        return new PaperTaskHandle(task);
    }

    private static final class PaperTaskHandle implements TaskHandle {

        private final BukkitTask task;

        private PaperTaskHandle(BukkitTask task) {
            this.task = task;
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
