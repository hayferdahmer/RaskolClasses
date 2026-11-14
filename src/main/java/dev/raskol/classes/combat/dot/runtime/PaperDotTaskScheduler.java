// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер секундного producer DoT (20 ticks, initial delay 20 ticks). */
public final class PaperDotTaskScheduler implements DotTaskScheduler {
    private final Plugin plugin;

    public PaperDotTaskScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle scheduleTick(Runnable tick) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, tick, 20L, 20L);
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
