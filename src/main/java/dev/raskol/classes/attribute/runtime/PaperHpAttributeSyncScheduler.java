// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.attribute.HpAttributeSync;
import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер периодического HP sweep. */
public final class PaperHpAttributeSyncScheduler implements HpAttributeSyncScheduler {

    private final Plugin plugin;
    private final HpAttributeSync sync;

    public PaperHpAttributeSyncScheduler(Plugin plugin, HpAttributeSync sync) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sync = Objects.requireNonNull(sync, "sync");
    }

    @Override
    public TaskHandle scheduleSweep(long periodTicks) {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks должен быть положительным");
        }
        BukkitTask task =
                plugin.getServer().getScheduler().runTaskTimer(plugin, sync::syncAll, periodTicks, periodTicks);
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
