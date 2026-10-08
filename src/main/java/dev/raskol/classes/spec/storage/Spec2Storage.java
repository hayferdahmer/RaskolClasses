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
 *
 * 1.14.4 (Волна 4, П10): файл переименован spec2.yml → spec2-storage.yml.
 * Одноразовая миграция в конструкторе: если старый файл существует, а нового
 * ещё нет — renameTo. Содержимое не меняется, схема остаётся v2. Если renameTo
 * не удался — логируем warning и заводим новый пустой файл через SafeStorage.
 */
public final class Spec2Storage {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final String OLD_FILE_NAME = "spec2.yml";
    private static final String NEW_FILE_NAME = "spec2-storage.yml";

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration store;

    public Spec2Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        File oldFile = new File(dir, OLD_FILE_NAME);
        File newFile = new File(dir, NEW_FILE_NAME);

        if (oldFile.exists() && !newFile.exists()) {
            boolean renamed = oldFile.renameTo(newFile);
            if (renamed) {
                LOGGER.info("spec2: migrated " + OLD_FILE_NAME + " → " + NEW_FILE_NAME);
            } else {
                LOGGER.warning("spec2: не удалось переименовать " + OLD_FILE_NAME
                        + " → " + NEW_FILE_NAME + ", будет создан новый пустой файл");
            }
        }

        this.file = newFile;
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
