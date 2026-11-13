// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute.runtime;

import java.util.Objects;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import dev.raskol.classes.attribute.HpAttributeSync;

/** Paper-адаптер регистрации HpAttributeSync. */
public final class PaperHpAttributeSyncRegistration implements HpAttributeSyncRegistration {

    private final PluginManager pluginManager;
    private final Plugin plugin;
    private final HpAttributeSync listener;

    public PaperHpAttributeSyncRegistration(PluginManager pluginManager, Plugin plugin, HpAttributeSync listener) {
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
