// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер синхронного периодического HP tick. */
public final class PaperHpBarTaskScheduler implements HpBarTaskScheduler {
    private final Plugin plugin;

    public PaperHpBarTaskScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle schedule(Runnable tick, long periodTicks) {
        Objects.requireNonNull(tick, "tick");
        long period = Math.max(1L, periodTicks);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, tick, period, period);
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
