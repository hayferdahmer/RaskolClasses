// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер синхронного планирования BossBar. */
public final class PaperBossBarTaskScheduler implements BossBarTaskScheduler {

    private final Plugin plugin;
    private final Runnable updateAction;

    public PaperBossBarTaskScheduler(Plugin plugin, Runnable updateAction) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.updateAction = Objects.requireNonNull(updateAction, "updateAction");
    }

    @Override
    public TaskHandle schedule(long periodTicks) {
        if (periodTicks < 1L) {
            throw new IllegalArgumentException("Период BossBar должен быть не меньше одного тика");
        }
        BukkitTask task =
                plugin.getServer().getScheduler().runTaskTimer(plugin, updateAction, periodTicks, periodTicks);
        return new PaperTaskHandle(task);
    }

    private static final class PaperTaskHandle implements TaskHandle {

        private final BukkitTask task;

        private PaperTaskHandle(BukkitTask task) {
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
