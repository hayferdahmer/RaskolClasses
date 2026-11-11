// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.storage;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.raskol.classes.storage.AsyncStorageWriter;

/**
 * Хранилище специализаций с асинхронной записью неизменяемых YAML-снимков.
 *
 * Все изменения и сериализация выполняются на основном потоке. Фоновый
 * исполнитель получает только готовую строку и File. Dirty-состояние
 * снимается только после успешной записи соответствующего поколения.
 */
public final class Spec2Storage {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final String NEW_FILE_NAME = "spec2-storage.yml";

    private final File file;
    private final YamlConfiguration store;
    private final AsyncStorageWriter writer;
    private final Logger logger;
    private final AtomicLong persistedVersion = new AtomicLong();
    private long mutationVersion;

    public Spec2Storage(JavaPlugin plugin, AsyncStorageWriter writer) {
        File dir = plugin.getDataFolder();
        this.file = new File(dir, NEW_FILE_NAME);
        this.store = Spec2StorageMigration.load(dir, LOGGER);
        this.writer = writer;
        this.logger = LOGGER;
    }

    Spec2Storage(File file, YamlConfiguration store, AsyncStorageWriter writer, Logger logger) {
        this.file = file;
        this.store = store;
        this.writer = writer;
        this.logger = logger;
    }

    public String getMain(UUID uuid) {
        return store.getString("players." + uuid + ".main", null);
    }

    public void setMain(UUID uuid, String specId) {
        store.set("players." + uuid + ".main", specId);
        markDirty();
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
            for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    store.set(base + "." + entry.getKey(), entry.getValue());
                }
            }
        }
        markDirty();
    }

    /** Возвращает id деревьев, по которым у игрока сохранены ранги. */
    public List<String> storedTreeIds(UUID uuid) {
        ConfigurationSection sec = store.getConfigurationSection("players." + uuid + ".ranks");
        if (sec == null) {
            return List.of();
        }
        return new ArrayList<>(sec.getKeys(false));
    }

    /** Удаляет сохранённые ранги одного дерева и помечает снимок устаревшим. */
    public void removeTree(UUID uuid, String treeId) {
        store.set("players." + uuid + ".ranks." + treeId, null);
        markDirty();
    }

    private void markDirty() {
        mutationVersion++;
    }

    /**
     * Сериализует актуальное состояние на вызывающем потоке и ставит снимок
     * в последовательную очередь. true означает, что запись принята очередью;
     * подтверждение дисковой записи отражается в isDirty().
     */
    public boolean save() {
        if (!isDirty()) {
            return false;
        }

        long snapshotVersion = mutationVersion;
        String snapshot = store.saveToString();
        var future = writer.submit(file, snapshot);
        future.thenAccept(successful -> {
            if (successful) {
                persistedVersion.accumulateAndGet(snapshotVersion, Math::max);
            } else {
                logger.severe("Spec2Storage: не удалось сохранить снимок поколения " + snapshotVersion);
            }
        });
        return !future.isDone() || future.getNow(false);
    }

    /** Ставит запись в очередь только при наличии несохранённых изменений. */
    public boolean saveIfDirty() {
        return isDirty() && save();
    }

    /** Есть ли изменения новее последнего успешно записанного снимка. */
    public boolean isDirty() {
        return mutationVersion > persistedVersion.get();
    }

    /**
     * Финальный сброс при выключении: ставит последний снимок и дожидается
     * завершения всех операций writer. Вызывается из onDisable на главном потоке.
     */
    public boolean close() {
        saveIfDirty();
        boolean drained = writer.closeAndAwait(Duration.ofSeconds(10));
        return drained && !isDirty();
    }
}
