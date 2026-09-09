// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Персист выбранных спеков (1.4.0, Пакет 1).
 * Файл: plugins/RaskolClasses/spec-choices.yml (uuid → spec_id).
 * ОТДЕЛЬНЫЙ файл от specs.yml (баланс) — чтобы сохранение выборов
 * никогда не перетирало конфигурацию спек.
 *
 * 1.6.10: атомарные сейвы через SafeStorage (tmp → .bak → atomic rename);
 * чтение с фолбэком: битый YAML → .bak → пустая конфигурация (с SEVERE в лог).
 * Сейв на каждое изменение (set/remove) сохранён — выбор спеки не теряется.
 */
public final class SpecStorage {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final RaskolClasses plugin;
    private final File file;
    private final Map<UUID, Spec> choices = new ConcurrentHashMap<>();

    public SpecStorage(RaskolClasses plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spec-choices.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        // 1.6.10: фолбэк на .bak при битом YAML; если и .bak мёртв — стартуем
        // с пустой конфигурацией, SEVERE в лог (оператор увидит инцидент)
        YamlConfiguration cfg = SafeStorage.loadWithFallback(file, LOGGER);
        for (String uuidKey : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                Spec spec = Spec.fromId(cfg.getString(uuidKey, ""));
                if (spec != null) {
                    choices.put(uuid, spec);
                }
            } catch (IllegalArgumentException ignored) {
                // битый ключ — пропускаем, не роняем старт
            }
        }
    }

    /** 1.6.10: атомарная запись с .bak-копией предыдущей версии. */
    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Spec> entry : choices.entrySet()) {
            cfg.set(entry.getKey().toString(), entry.getValue().id());
        }
        SafeStorage.saveAtomic(cfg, file, LOGGER);
    }

    public Spec get(UUID uuid) {
        return choices.get(uuid);
    }

    public void set(UUID uuid, Spec spec) {
        choices.put(uuid, spec);
        save(); // сейв на каждое изменение — выбор не теряется
    }

    public boolean hasSpec(UUID uuid) {
        return choices.containsKey(uuid);
    }

    /** Респец (Пакет 3). */
    public void remove(UUID uuid) {
        choices.remove(uuid);
        save(); // сейв на каждое изменение — отречение не теряется
    }
}
