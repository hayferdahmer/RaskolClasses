// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.hook.RaskolCoreHook;
import dev.raskol.classes.resource.ResourceService;

/**
 * 1.14.14 (фаза 2): удалён вызов backend.setConfig(config) — поле и сеттер удалены
 * из LuckPermsBackend как мёртвые (SpotBugs URF_UNREAD_FIELD). Параметр RaskolConfig
 * в конструкторе остаётся — он передаётся в RaskolCoreHook.
 */
public final class ClassProvider {

    private final ClassCache cache = new ClassCache();
    private final LuckPermsBackend backend;
    private final RaskolCoreHook coreHook;

    public ClassProvider(RaskolClasses plugin, RaskolConfig config) {
        LuckPermsBackend resolved = null;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        if (pluginManager.getPlugin("LuckPerms") != null) {
            try {
                resolved = new LuckPermsBackend(this, plugin);
                // 1.14.14 (фаза 2): вызов setConfig(config) удалён — мёртвое поле
                // в LuckPermsBackend убрано. RaskolConfig передаётся только в CoreHook.
            } catch (NoClassDefFoundError | Exception e) {
                plugin.getLogger().warning("LuckPerms API недоступен: " + e.getMessage());
                resolved = null;
            }
        }
        this.backend = resolved;
        // 1.3.2: Core-адаптер; читает класс из паспорта Core, LP остаётся фолбэком
        this.coreHook = new RaskolCoreHook(plugin, config);
    }

    /** LP-бэкенд жив. */
    public boolean isAvailable() {
        return backend != null;
    }

    /** 1.3.2: источник класса для /rc debug. */
    public String sourceOf() {
        if (coreHook.isAvailable()) return "core";
        if (backend != null) return "lp";
        return "off";
    }

    public PlayerClass getClassOf(Player player) {
        UUID id = player.getUniqueId();
        ClassCache.Entry entry = cache.get(id);
        if (entry != null) {
            return entry.playerClass();
        }

        // 1.3.2: сначала Core, потом LP-фолбэк
        if (coreHook.isAvailable()) {
            PlayerClass fromCore = coreHook.resolveClass(id);
            if (fromCore != null) {
                cache.put(id, fromCore);
                return fromCore;
            }
        }

        if (backend == null) {
            return null;
        }
        PlayerClass resolved = backend.resolve(player);
        cache.put(id, resolved);
        return resolved;
    }

    public PlayerClass getCachedClass(UUID playerId) {
        ClassCache.Entry entry = cache.get(playerId);
        return entry == null ? null : entry.playerClass();
    }

    public void invalidate(UUID playerId) {
        cache.invalidate(playerId);
    }

    public void setResourceService(ResourceService resourceService) {
        if (backend != null) {
            backend.setResourceService(resourceService);
        }
    }

    public void shutdown() {
        if (backend != null) {
            backend.unsubscribe();
        }
        cache.clear();
    }
}
