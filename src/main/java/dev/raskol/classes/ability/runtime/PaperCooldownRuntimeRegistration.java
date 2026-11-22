// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.runtime;

import java.util.Objects;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import dev.raskol.classes.ability.CooldownManager;

/** Paper-адаптер регистрации listener CooldownManager. */
public final class PaperCooldownRuntimeRegistration implements CooldownRuntimeRegistration {

    private final Plugin plugin;
    private final CooldownManager listener;

    public PaperCooldownRuntimeRegistration(Plugin plugin, CooldownManager listener) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void register() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(listener, plugin);
    }

    @Override
    public void unregister() {
        HandlerList.unregisterAll(listener);
    }
}
