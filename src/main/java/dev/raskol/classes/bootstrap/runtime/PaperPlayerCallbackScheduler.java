// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

/** Paper-адаптер одноразового синхронного callback. */
public final class PaperPlayerCallbackScheduler implements PlayerCallbackScheduler {

    private final Plugin plugin;

    public PaperPlayerCallbackScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle schedule(long delayTicks, Runnable callback) {
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks не может быть отрицательным");
        }
        Objects.requireNonNull(callback, "callback");
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, callback, delayTicks);
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
