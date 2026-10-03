// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.storage;

import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 1.14.0 «Спек 2.0»: персист spec2.yml (атомарно, .bak, фолбэк).
 * Схема v2:
 *   players.<uuid>.main: <specId>
 *   players.<uuid>.ranks.<treeId>.<nodeId>: <rank>
 * Никаких миграций: на сервере система не была, файл создаётся с нуля.
 */
public final class Spec2Storage {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration store;

    public Spec2Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spec2.yml");
        this.store = SafeStorage.loadWithFallback(file, LOGGER);
    }

    public String getMain(UUID uuid) {
        return store.getString("players." + uuid + ".main", null);
    }

    public void setMain(UUID uuid, String specId) {
        store.set("players." + uuid + ".main", specId);
    }

    public Map<String, Integer> getRanks(UUID uuid, String treeId) {
        Map<String, Integer> out = new HashMap<>();
        ConfigurationSection sec = store.getConfigurationSection(
                "players." + uuid + ".ranks." + treeId);
        if (sec == null) {
            return out;
        }
        for (String key : sec.getKeys(false)) {
            out.put(key, sec.getInt(key, 0));
        }
        out.values().removeIf(v -> v == null || v <= 0);
        return out;
    }

    public void setRanks(UUID uuid, String treeId, Map<String, Integer> ranks) {
        String base = "players." + uuid + ".ranks." + treeId;
        store.set(base, null);
        if (ranks != null) {
            for (Map.Entry<String, Integer> e : ranks.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    store.set(base + "." + e.getKey(), e.getValue());
                }
            }
        }
    }

    public void save() {
        SafeStorage.saveAtomic(store, file, LOGGER);
    }
}
