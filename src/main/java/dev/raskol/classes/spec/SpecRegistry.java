// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Загрузка баланса спеков из specs.yml (1.4.0).
 * FIX 1.4.0.2: самовосстановление — если файл побит (старым багом хранения
 * он перезаписывался uuid-ключами и терял секцию specs:), пересоздаём его
 * из шаблона в jar и перечитываем. Без этого активки/пассивки молча мертвы.
 */
public final class SpecRegistry {

    public record SpecDef(
            Spec spec,
            String passiveId,
            String passiveDescription,
            String activeId,
            String activeDescription,
            int activeCost,
            int activeCooldown,
            Map<String, Object> passiveParams,
            Map<String, Object> activeParams
    ) {
        public double passiveDouble(String key, double def) {
            Object v = passiveParams.get(key);
            return v instanceof Number n ? n.doubleValue() : def;
        }

        public int passiveInt(String key, int def) {
            Object v = passiveParams.get(key);
            return v instanceof Number n ? n.intValue() : def;
        }

        public double activeDouble(String key, double def) {
            Object v = activeParams.get(key);
            return v instanceof Number n ? n.doubleValue() : def;
        }

        public int activeInt(String key, int def) {
            Object v = activeParams.get(key);
            return v instanceof Number n ? n.intValue() : def;
        }
    }

    private final RaskolClasses plugin;
    private final Map<Spec, SpecDef> definitions = new EnumMap<>(Spec.class);

    public SpecRegistry(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "specs.yml");
        if (!file.exists()) {
            plugin.saveResource("specs.yml", false);
        }

        int loaded = parse(YamlConfiguration.loadConfiguration(file));
        if (loaded == 0) {
            // Файл существует, но секций specs.* нет — побит. Лечим пересозданием.
            plugin.getLogger().warning("specs.yml повреждён или пуст — "
                    + "пересоздаю из шаблона (баланс спеков)");
            if (file.delete()) {
                plugin.saveResource("specs.yml", false);
                loaded = parse(YamlConfiguration.loadConfiguration(file));
            }
        }
        if (loaded == 0) {
            plugin.getLogger().severe("specs.yml не читается даже из шаблона — "
                    + "спеки отключены");
        }
        plugin.getLogger().info("Загружено " + definitions.size() + " специализаций");
    }

    /** Разбирает конфиг в definitions; возвращает число загруженных спеков. */
    private int parse(FileConfiguration cfg) {
        definitions.clear();
        int loaded = 0;

        for (Spec spec : Spec.values()) {
            ConfigurationSection section = cfg.getConfigurationSection("specs." + spec.id());
            if (section == null) {
                continue;
            }
            loaded++;

            String passiveId = section.getString("passive.id", spec.id() + "_passive");
            String passiveDesc = section.getString("passive.description", "");
            String activeId = section.getString("active.id", spec.id() + "_active");
            String activeDesc = section.getString("active.description", "");
            int activeCost = section.getInt("active.cost", 30);
            int activeCooldown = section.getInt("active.cooldown", 30);

            Map<String, Object> passiveParams = new HashMap<>();
            ConfigurationSection passiveSection = section.getConfigurationSection("passive");
            if (passiveSection != null) {
                for (String key : passiveSection.getKeys(false)) {
                    if (!key.equals("id") && !key.equals("description")) {
                        passiveParams.put(key, passiveSection.get(key));
                    }
                }
            }

            Map<String, Object> activeParams = new HashMap<>();
            ConfigurationSection activeSection = section.getConfigurationSection("active");
            if (activeSection != null) {
                for (String key : activeSection.getKeys(false)) {
                    if (!key.equals("id") && !key.equals("description")
                            && !key.equals("cost") && !key.equals("cooldown")) {
                        activeParams.put(key, activeSection.get(key));
                    }
                }
            }

            definitions.put(spec, new SpecDef(spec, passiveId, passiveDesc,
                    activeId, activeDesc, activeCost, activeCooldown,
                    passiveParams, activeParams));
        }
        return loaded;
    }

    public SpecDef get(Spec spec) {
        return definitions.get(spec);
    }

    /** Для диагностики: сколько дефов реально загружено. */
    public int size() {
        return definitions.size();
    }
}
