// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

import java.util.Objects;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import dev.raskol.classes.cc.CCService;

/** Paper-адаптер регистрации только listener CCService. */
public final class PaperCCRuntimeRegistration implements CCRuntimeRegistration {
    private final PluginManager pluginManager;
    private final Plugin plugin;
    private final CCService service;

    public PaperCCRuntimeRegistration(PluginManager pluginManager, Plugin plugin, CCService service) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public void register() {
        pluginManager.registerEvents(service, plugin);
    }

    @Override
    public void unregister() {
        HandlerList.unregisterAll(service);
    }
}
