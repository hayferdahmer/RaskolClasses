// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 1.9.0 → 1.14.0: персист купленных талантов — talents.yml через SafeStorage.
 * 1.14.0 (Б4): схема изменена на map {nodeId: rank} вместо list [nodeId, ...].
 *         Миграция: если в YAML list, конвертируется в map с rank=1 для всех узлов.
 *
 * Схема: players.<uuid>.<specId>: {nodeId: rank, ...}
 * Узлы хранятся ПО СПЕКАМ: после респека спеки купленное в старом дереве
 * остаётся записанным (вернёшься к спеке — таланты на месте), но неактивно,
 * пока спека не выбрана (активность решает TalentService).
 */
public final class TalentsStorage {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration store;

    public TalentsStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "talents.yml");
        this.store = SafeStorage.loadWithFallback(file, LOGGER);
    }

    /**
     * 1.14.0: купленные узлы спеки игроком как map {nodeId: rank}.
     * Миграция: если в YAML list (старый формат), конвертируется в map с rank=1.
     */
    public Map<String, Integer> getPurchased(UUID uuid, String specId) {
        String key = "players." + uuid + "." + specId;
        Object raw = store.get(key);
        if (raw == null) {
            return new HashMap<>();
        }
        // миграция: list → map
        if (raw instanceof List<?> list) {
            Map<String, Integer> migrated = new HashMap<>();
            for (Object o : list) {
                if (o != null) {
                    migrated.put(String.valueOf(o), 1);
                }
            }
            store.set(key, migrated);
            save();
            LOGGER.info("1.14.0 миграция talents: " + uuid + "/" + specId
                    + " list→map, узлов=" + migrated.size());
            return migrated;
        }
        // новый формат: map
        if (raw instanceof Map<?, ?> map) {
            Map<String, Integer> out = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nodeId = String.valueOf(entry.getKey());
                int rank = (entry.getValue() instanceof Number num) ? num.intValue() : 1;
                out.put(nodeId, rank);
            }
            return out;
        }
        return new HashMap<>();
    }

    /** 1.14.0: полная замена map купленных узлов спеки. */
    public void setPurchased(UUID uuid, String specId, Map<String, Integer> ranks) {
        String key = "players." + uuid + "." + specId;
        if (ranks == null || ranks.isEmpty()) {
            store.set(key, null);
        } else {
            store.set(key, new HashMap<>(ranks));
        }
    }

    /** 1.14.0: увеличить rank узла на 1 (вызывается после успешной покупки). */
    public void addNode(UUID uuid, String specId, String nodeId) {
        Map<String, Integer> ranks = getPurchased(uuid, specId);
        int rank = ranks.getOrDefault(nodeId, 0);
        ranks.put(nodeId, rank + 1);
        setPurchased(uuid, specId, ranks);
    }

    /** Очистить дерево спеки (платный сброс талантов). */
    public void clearSpec(UUID uuid, String specId) {
        setPurchased(uuid, specId, new HashMap<>());
    }

    /** Атомарный сейв (автосейв-таск + onDisable + после покупки/сброса). */
    public void save() {
        SafeStorage.saveAtomic(store, file, LOGGER);
    }
}
