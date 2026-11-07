// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.raskol.classes.storage.SafeStorage;

/**
 * 1.14.0 «Спек 2.0»: персист spec2-storage.yml (атомарно, .bak, фолбэк).
 * Схема v2:
 *   players.<uuid>.main: <specId>
 *   players.<uuid>.ranks.<treeId>.<nodeId>: <rank>
 *
 * 1.14.4 (Волна 4, П10): файл переименован spec2.yml → spec2-storage.yml.
 * Одноразовая миграция в конструкторе: если старый файл существует, а нового
 * ещё нет — renameTo. Содержимое не меняется, схема остаётся v2. Если renameTo
 * не удался — логируем warning и заводим новый пустой файл через SafeStorage.
 * 1.15.5 (Волна 2, аудит 2.6-91): dirty-flag. setMain/setRanks помечают store
 *         грязным; saveIfDirty() пишет файл ТОЛЬКО при наличии изменений —
 *         вызывается SessionListener.onQuit (окно потери данных закрывается
 *         выходом игрока) и autosave-задачей (без лишней перезаписи чистого файла).
 * 1.15.6 (Волна 5, аудит 2.6-96): storedTreeIds/removeTree — очистка хранилища
 *         от рангов деревьев ЧУЖИХ классов: при смене класса (LP-группы) старые
 *         ранги раньше оставались в файле навсегда и файл рос без границы.
 *         Вызывает Spec2Service.reconcile для онлайн-игроков.
 */
public final class Spec2Storage {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final String OLD_FILE_NAME = "spec2.yml";
    private static final String NEW_FILE_NAME = "spec2-storage.yml";

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration store;
    private volatile boolean dirty = false;

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
                LOGGER.warning("spec2: не удалось переименовать " + OLD_FILE_NAME + " → " + NEW_FILE_NAME
                        + ", будет создан новый пустой файл");
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
        dirty = true;
    }

    public Map<String, Integer> getRanks(UUID uuid, String treeId) {
        Map<String, Integer> out = new HashMap<>();
        ConfigurationSection sec = store.getConfigurationSection("players." + uuid + ".ranks." + treeId);
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
        dirty = true;
    }

    /**
     * 1.15.6 (Волна 5, аудит 2.6-96): id деревьев, по которым у игрока есть
     * сохранённые ранги (секция players.<uuid>.ranks.*). Пустой список = записей нет.
     */
    public List<String> storedTreeIds(UUID uuid) {
        ConfigurationSection sec = store.getConfigurationSection("players." + uuid + ".ranks");
        if (sec == null) {
            return List.of();
        }
        return new ArrayList<>(sec.getKeys(false));
    }

    /**
     * 1.15.6 (Волна 5, аудит 2.6-96): удалить ранги ЦЕЛИКОМ одного дерева
     * (дерево чужого класса после смены класса). Помечает store dirty.
     */
    public void removeTree(UUID uuid, String treeId) {
        store.set("players." + uuid + ".ranks." + treeId, null);
        dirty = true;
    }

    /** Принудительная запись (onDisable, autosave). Сбрасывает dirty. */
    public void save() {
        SafeStorage.saveAtomic(store, file, LOGGER);
        dirty = false;
    }

    /**
     * 1.15.5 (аудит 2.6-91): запись только при наличии изменений.
     * Вызов: SessionListener.onQuit (закрыть окно потери при краше до autosave).
     * @return true если файл был записан
     */
    public boolean saveIfDirty() {
        if (!dirty) {
            return false;
        }
        save();
        return true;
    }

    /** Диагностика: есть ли несохранённые изменения (для /rc health). */
    public boolean isDirty() {
        return dirty;
    }
}
