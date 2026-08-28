// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
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
 */
public final class CooldownManager implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final Map<UUID, Map<String, Long>> readyAt = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration store;

    public CooldownManager(File file) {
        this.file = file;
        this.store = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
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
        readyAt.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(abilityId, System.currentTimeMillis() + durationMillis);
    }

    /** Отмена кулдауна (используется при отмене каста). */
    public void cancel(UUID playerId, String abilityId) {
        Map<String, Long> byAbility = readyAt.get(playerId);
        if (byAbility != null) {
            byAbility.remove(abilityId);
        }
    }

    public void clear() {
        readyAt.clear();
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
        savePlayer(playerId);      // A1: зафиксировать в файле
        readyAt.remove(playerId);  // O2: очистить память
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
}