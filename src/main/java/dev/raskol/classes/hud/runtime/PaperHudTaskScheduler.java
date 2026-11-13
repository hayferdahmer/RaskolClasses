// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер синхронного планировщика RaskolHUD. */
public final class PaperHudTaskScheduler implements HudTaskScheduler {

    private final Plugin plugin;

    public PaperHudTaskScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle scheduleRepeating(Runnable action, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(action, "action");
        if (initialDelayTicks < 0) {
            throw new IllegalArgumentException("initialDelayTicks не может быть отрицательным");
        }
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks должен быть положительным");
        }

        BukkitTask task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, action, initialDelayTicks, periodTicks);
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
