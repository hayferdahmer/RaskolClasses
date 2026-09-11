// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 1.9.0: персист купленных талантов — talents.yml через SafeStorage
 * (атомарно, .bak, фолбэк при битом файле).
 *
 * Схема: players.<uuid>.<specId>: [nodeId, nodeId, ...]
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

    /** Купленные узлы спеки игроком (никогда не null). */
    public List<String> getPurchased(UUID uuid, String specId) {
        List<String> out = new ArrayList<>();
        List<?> raw = store.getList("players." + uuid + "." + specId);
        if (raw != null) {
            for (Object o : raw) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }

    /** Полная замена списка купленных узлов спеки (покупка/сброс). */
    public void setPurchased(UUID uuid, String specId, List<String> nodeIds) {
        String key = "players." + uuid + "." + specId;
        if (nodeIds == null || nodeIds.isEmpty()) {
            store.set(key, null);
        } else {
            store.set(key, new ArrayList<>(nodeIds));
        }
    }

    /** Добавить узел (вызывается после успешной серверной валидации). */
    public void addNode(UUID uuid, String specId, String nodeId) {
        List<String> list = getPurchased(uuid, specId);
        if (!list.contains(nodeId)) {
            list.add(nodeId);
            setPurchased(uuid, specId, list);
        }
    }

    /** Очистить дерево спеки (платный сброс талантов). */
    public void clearSpec(UUID uuid, String specId) {
        setPurchased(uuid, specId, List.of());
    }

    /** Атомарный сейв (автосейв-таск + onDisable + после покупки/сброса). */
    public void save() {
        SafeStorage.saveAtomic(store, file, LOGGER);
    }
}
