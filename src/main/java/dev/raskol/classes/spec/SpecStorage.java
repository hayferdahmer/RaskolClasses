// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Персист выбранных спеков (1.4.0, Пакет 1).
 * Файл: plugins/RaskolClasses/specs.yml (uuid → spec_id).
 * Асинхронное сохранение на выходе и выгрузке.
 */
public final class SpecStorage {

    private final RaskolClasses plugin;
    private final File file;
    private final Map<UUID, Spec> choices = new ConcurrentHashMap<>();

    public SpecStorage(RaskolClasses plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "specs.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String uuidKey : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                String specId = cfg.getString(uuidKey, "");
                Spec spec = Spec.fromId(specId);
                if (spec != null) {
                    choices.put(uuid, spec);
                }
            } catch (IllegalArgumentException ignored) {
                // битый ключ — пропускаем
            }
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Spec> entry : choices.entrySet()) {
            cfg.set(entry.getKey().toString(), entry.getValue().id());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить specs.yml: " + e.getMessage());
        }
    }

    public Spec get(UUID uuid) {
        return choices.get(uuid);
    }

    public void set(UUID uuid, Spec spec) {
        choices.put(uuid, spec);
        save();
    }

    public boolean hasSpec(UUID uuid) {
        return choices.containsKey(uuid);
    }

    public void remove(UUID uuid) {
        choices.remove(uuid);
        save();
    }
}
