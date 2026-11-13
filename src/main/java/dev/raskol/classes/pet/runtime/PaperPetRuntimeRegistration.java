// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import java.util.Objects;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper-адаптер регистрации listener петов. */
public final class PaperPetRuntimeRegistration implements PetRuntimeRegistration {

    private final JavaPlugin plugin;
    private final Listener listener;
    private boolean registered;

    public PaperPetRuntimeRegistration(JavaPlugin plugin, Listener listener) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(listener, plugin);
    }

    @Override
    public synchronized void unregister() {
        if (!registered) {
            return;
        }
        HandlerList.unregisterAll(listener);
        registered = false;
    }
}
