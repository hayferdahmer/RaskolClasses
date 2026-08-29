// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

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
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Кулдауны в миллисекундах (на игрока и способность) + персист (A1):
 * cooldowns.yml хранит uuid → способность → readyAtEpoch. Сохранение на
 * выходе и выгрузке, восстановление на входе; очистка памяти на выходе (O2)
 * и периодический purge истёкших записей (O7).
 * Пакет 2: ready-нотификация — отложенный таск на конец КД (звук + actionbar).
 */
public final class CooldownManager implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final Map<UUID, Map<String, Long>> readyAt = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration store;

    // Пакет 2: отложенные таски ready-notify
    private final Map<UUID, Map<String, BukkitTask>> tasks = new ConcurrentHashMap<>();
    private Plugin plugin;

    // Пакет 2: конфиг ready-notify (кэшируются в attachScheduler)
    private boolean notifyEnabled = false;
    private long notifyMinMillis = 30_000L;
    private Sound notifySound = Sound.ENTITY_PLAYER_LEVELUP;
    private String notifyMessage = "{ability} — готова";

    public CooldownManager(File file) {
        this.file = file;
        this.store = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
    }

    /**
     * Пакет 2: привязка к плагину для планирования тасков. Вызывается один раз
     * в onEnable после создания CooldownManager. Читает конфиг ready-notify
     * и кэширует параметры.
     */
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

    /** Базовый start без нотификации (обратная совместимость). */
    public void start(UUID playerId, String abilityId, long durationMillis) {
        start(playerId, abilityId, durationMillis, abilityId);
    }

    /**
     * Пакет 2: start с нотификацией. Ставит КД и, если ready-notify включён,
     * КД ≥ порога — планирует таск на конец КД. Старый таск этой же абилки
     * отменяется (защита от «призраков» при re-cast).
     */
    public void start(UUID playerId, String abilityId, long durationMillis, String displayName) {
        readyAt.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(abilityId, System.currentTimeMillis() + durationMillis);

        if (!notifyEnabled || plugin == null || durationMillis < notifyMinMillis) {
            return;
        }

        // Отмена старого таска этой же абилки
        Map<String, BukkitTask> byAbility = tasks.computeIfAbsent(playerId,
                id -> new ConcurrentHashMap<>());
        BukkitTask old = byAbility.remove(abilityId);
        if (old != null) {
            old.cancel();
        }

        long delayTicks = Math.max(1L, durationMillis / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Защита: игрок может выйти — в onQuit таск отменяется, но на всякий
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (getRemainingMillis(playerId, abilityId) > 0L) {
                // КД ещё не истёк (перезапуск сервера сдвинул часы) — пропускаем
                return;
            }
            player.playSound(player.getLocation(), notifySound, 1.0f, 1.0f);
            String text = notifyMessage.replace("{ability}", displayName);
            player.sendActionBar(Component.text(text, NamedTextColor.GREEN));
        }, delayTicks);
        byAbility.put(abilityId, task);
    }

    /** Отмена кулдауна (используется при отмене каста). */
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

    /** O7: удалить истёкшие записи и опустевшие мапы игроков. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        readyAt.forEach((playerId, byAbility) -> {
            byAbility.values().removeIf(ready -> ready <= now);
            if (byAbility.isEmpty()) {
                readyAt.remove(playerId);
            }
        });
    }

    /* ------------------------- персист (A1) ------------------------- */

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
        savePlayer(playerId);        // A1: зафиксировать в файле
        readyAt.remove(playerId);    // O2: очистить память
        cancelPlayerTasks(playerId); // Пакет 2: убрать призрачные таски
    }

    public void savePlayer(UUID playerId) {
        writeSection(playerId);
        persist();
    }

    /** Полное сохранение — вызывается в onDisable. */
    public void saveAll() {
        for (UUID playerId : readyAt.keySet()) {
            writeSection(playerId);
        }
        persist();
    }

    /** Пакет 2: отмена всех тасков — вызывается в onDisable перед закрытием. */
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
        try {
            store.save(file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Не удалось сохранить cooldowns.yml", e);
        }
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
