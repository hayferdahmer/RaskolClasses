// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.flavor.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер синхронного планирования ауры королевских вкусов. */
public final class PaperFlavorTaskScheduler implements FlavorTaskScheduler {

    private static final long AURA_PERIOD_TICKS = 40L;

    private final Plugin plugin;
    private final Runnable auraAction;

    public PaperFlavorTaskScheduler(Plugin plugin, Runnable auraAction) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.auraAction = Objects.requireNonNull(auraAction, "auraAction");
    }

    @Override
    public TaskHandle scheduleAura() {
        BukkitTask task = plugin.getServer()
                .getScheduler()
                .runTaskTimer(plugin, auraAction, AURA_PERIOD_TICKS, AURA_PERIOD_TICKS);
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
