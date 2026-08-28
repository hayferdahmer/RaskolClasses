// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.resource.ResourceService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassProvider {

    private static final long CACHE_TTL_MILLIS = 5_000L;

    private record CacheEntry(PlayerClass playerClass, long expiresAt) {
    }

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final LuckPermsBackend backend;

    public ClassProvider(RaskolClasses plugin, RaskolConfig config) {
        LuckPermsBackend resolved = null;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        if (pluginManager.getPlugin("LuckPerms") != null) {
            try {
                resolved = new LuckPermsBackend(this, plugin);
                resolved.setConfig(config);
            } catch (NoClassDefFoundError | Exception e) {
                plugin.getLogger().warning("LuckPerms API недоступен: " + e.getMessage());
                resolved = null;
            }
        }
        this.backend = resolved;
    }

    public boolean isAvailable() {
        return backend != null;
    }

    public PlayerClass getClassOf(Player player) {
        if (backend == null) {
            return null;
        }
        UUID id = player.getUniqueId();
        CacheEntry entry = cache.get(id);
        if (entry != null && entry.expiresAt() > System.currentTimeMillis()) {
            return entry.playerClass();
        }
        PlayerClass resolved = backend.resolve(player);
        cache.put(id, new CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MILLIS));
        return resolved;
    }

    public PlayerClass getCachedClass(UUID playerId) {
        CacheEntry entry = cache.get(playerId);
        if (entry == null || entry.expiresAt() <= System.currentTimeMillis()) {
            return null;
        }
        return entry.playerClass();
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
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
