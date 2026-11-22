// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.foliant.runtime;

import java.util.Objects;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import dev.raskol.classes.foliant.FoliantService;

/** Paper-адаптер регистрации listener Фолианта. */
public final class PaperFoliantRuntimeRegistration implements FoliantRuntimeRegistration {

    private final PluginManager pluginManager;
    private final Plugin plugin;
    private final FoliantService listener;

    public PaperFoliantRuntimeRegistration(PluginManager pluginManager, Plugin plugin, FoliantService listener) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void register() {
        pluginManager.registerEvents(listener, plugin);
    }

    @Override
    public void unregister() {
        HandlerList.unregisterAll(listener);
    }
}
