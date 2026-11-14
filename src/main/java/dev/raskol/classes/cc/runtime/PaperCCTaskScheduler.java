// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер CC producer с исходным периодом 1 tick и initial delay 1 tick. */
public final class PaperCCTaskScheduler implements CCTaskScheduler {
    private final Plugin plugin;

    public PaperCCTaskScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle scheduleTick(Runnable tick) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, tick, 1L, 1L);
        return new BukkitTaskHandle(task);
    }

    private record BukkitTaskHandle(BukkitTask task) implements TaskHandle {
        private BukkitTaskHandle {
            Objects.requireNonNull(task, "task");
        }
        @Override public void cancel() { task.cancel(); }
        @Override public boolean isCancelled() { return task.isCancelled(); }
    }
}
