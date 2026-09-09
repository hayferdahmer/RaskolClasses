// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.storage.SafeStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Кулдауны в миллисекундах (на игрока и способность) + персист (A1).
 * 1.6.10: чтение/запись через SafeStorage (атомарно, .bak, фолбэк).
 * 1.6.12: метрики trackedPlayers()/totalEntries() для /rc health.
 */
public final class CooldownManager implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final Map<UUID, Map<String, Long>> readyAt = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration store;

    private final Map<UUID, Map<String, BukkitTask>> tasks = new ConcurrentHashMap<>();
    private Plugin plugin;

    private boolean notifyEnabled = false;
    private long notifyMinMillis = 30_000L;
    private Sound notifySound = Sound.ENTITY_PLAYER_LEVELUP;
    private String notifyMessage = "{ability} — готова";

    public CooldownManager(File file) {
        this.file = file;
        this.store = SafeStorage.loadWithFallback(file, LOGGER);
    }

    public void attachScheduler(Plugin plugin,
                                boolean enabled,
                                int minCooldownSeconds,
                                String soundKey,
                                String message) {
        this.plugin = plugin;
        this.notifyEnabled = enabled;
        this.notifyMinMillis = Math.max(1L, minCooldownSeconds) * 1000L;
        this.notifySound = parseSound(soundKey, Sound.ENTITY_PLAYER_LEVELUP);
        this.notifyMessage = message == null || message.isEmpty()
                ? "{ability} — готова" : message;
    }

    public long getRemainingMillis(UUID playerId, String abilityId) {
        Map<String, Long> byAbility = readyAt.get(playerId);
        if (byAbility == null) {
            return 0L;
        }
        Long ready = byAbility.get(abilityId);
        if (ready == null) {
            return 0L;
        }
        return Math.max(0L, ready - System.currentTimeMillis());
    }

    public boolean isOnCooldown(UUID playerId, String abilityId) {
        return getRemainingMillis(playerId, abilityId) > 0L;
    }

    public void start(UUID playerId, String abilityId, long durationMillis) {
        start(playerId, abilityId, durationMillis, abilityId);
    }

    public void start(UUID playerId, String abilityId, long durationMillis, String displayName) {
        readyAt.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(abilityId, System.currentTimeMillis() + durationMillis);

        if (!notifyEnabled || plugin == null || durationMillis < notifyMinMillis) {
            return;
        }

        Map<String, BukkitTask> byAbility = tasks.computeIfAbsent(playerId,
                id -> new ConcurrentHashMap<>());
        BukkitTask old = byAbility.remove(abilityId);
        if (old != null) {
            old.cancel();
        }

        long delayTicks = Math.max(1L, durationMillis / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (getRemainingMillis(playerId, abilityId) > 0L) {
                return;
            }
            player.playSound(player.getLocation(), notifySound, 1.0f, 1.0f);
            String text = notifyMessage.replace("{ability}", displayName);
            player.sendActionBar(Component.text(text, NamedTextColor.GREEN));
        }, delayTicks);
        byAbility.put(abilityId, task);
    }

    public void cancel(UUID playerId, String abilityId) {
        Map<String, Long> byAbility = readyAt.get(playerId);
        if (byAbility != null) {
            byAbility.remove(abilityId);
        }
        cancelTask(playerId, abilityId);
    }

    public void clear() {
        readyAt.clear();
        tasks.values().forEach(m -> m.values().forEach(BukkitTask::cancel));
        tasks.clear();
    }

    /** 1.6.12: число игроков с записями кулдаунов (для /rc health). */
    public int trackedPlayers() {
        return readyAt.size();
    }

    /** 1.6.12: суммарное число записей кулдаунов (для /rc health). */
    public int totalEntries() {
        int total = 0;
        for (Map<String, Long> byAbility : readyAt.values()) {
            total += byAbility.size();
        }
        return total;
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        readyAt.forEach((playerId, byAbility) -> {
            byAbility.values().removeIf(ready -> ready <= now);
            if (byAbility.isEmpty()) {
                readyAt.remove(playerId);
            }
        });
    }

    /* ------------------------- персист (A1 + 1.6.10) ------------------------- */

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ConfigurationSection section = store.getConfigurationSection(playerId.toString());
        if (section == null) {
            return;
        }
        Map<String, Long> byAbility = readyAt.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        for (String abilityId : section.getKeys(false)) {
            long ready = section.getLong(abilityId, 0L);
            if (ready > now) {
                byAbility.put(abilityId, ready);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        savePlayer(playerId);
        readyAt.remove(playerId);
        cancelPlayerTasks(playerId);
    }

    public void savePlayer(UUID playerId) {
        writeSection(playerId);
        persist();
    }

    public void saveAll() {
        for (UUID playerId : readyAt.keySet()) {
            writeSection(playerId);
        }
        persist();
    }

    public void cancelAllTasks() {
        tasks.values().forEach(m -> m.values().forEach(BukkitTask::cancel));
        tasks.clear();
    }

    private void writeSection(UUID playerId) {
        String key = playerId.toString();
        store.set(key, null);
        Map<String, Long> byAbility = readyAt.get(playerId);
        if (byAbility == null) {
            return;
        }
        long now = System.currentTimeMillis();
        byAbility.forEach((abilityId, ready) -> {
            if (ready > now) {
                store.set(key + "." + abilityId, ready);
            }
        });
    }

    private void persist() {
        SafeStorage.saveAtomic(store, file, LOGGER);
    }

    private void cancelPlayerTasks(UUID playerId) {
        Map<String, BukkitTask> byAbility = tasks.remove(playerId);
        if (byAbility != null) {
            byAbility.values().forEach(BukkitTask::cancel);
        }
    }

    private void cancelTask(UUID playerId, String abilityId) {
        Map<String, BukkitTask> byAbility = tasks.get(playerId);
        if (byAbility == null) {
            return;
        }
        BukkitTask task = byAbility.remove(abilityId);
        if (task != null) {
            task.cancel();
        }
        if (byAbility.isEmpty()) {
            tasks.remove(playerId);
        }
    }

    private static Sound parseSound(String name, Sound fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Sound parsed = Registry.SOUNDS.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return parsed != null ? parsed : fallback;
    }
}
