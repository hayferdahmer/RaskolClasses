// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Загрузка баланса спеков из specs.yml (1.4.0, Пакет 1).
 * Все числа — в конфиге, правки без перекомпиляции.
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
    ) {}

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

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        definitions.clear();

        for (Spec spec : Spec.values()) {
            ConfigurationSection section = cfg.getConfigurationSection("specs." + spec.id());
            if (section == null) {
                plugin.getLogger().warning("specs.yml: отсутствует секция для " + spec.id());
                continue;
            }

            String passiveId = section.getString("passive.id", spec.id() + "_passive");
            String passiveDesc = section.getString("passive.description", "");
            String activeId = section.getString("active.id", spec.id() + "_active");
            String activeDesc = section.getString("active.description", "");
            int activeCost = section.getInt("active.cost", 30);
            int activeCooldown = section.getInt("active.cooldown", 30);

            Map<String, Object> passiveParams = new java.util.HashMap<>();
            ConfigurationSection passiveSection = section.getConfigurationSection("passive");
            if (passiveSection != null) {
                for (String key : passiveSection.getKeys(false)) {
                    if (!key.equals("id") && !key.equals("description")) {
                        passiveParams.put(key, passiveSection.get(key));
                    }
                }
            }

            Map<String, Object> activeParams = new java.util.HashMap<>();
            ConfigurationSection activeSection = section.getConfigurationSection("active");
            if (activeSection != null) {
                for (String key : activeSection.getKeys(false)) {
                    if (!key.equals("id") && !key.equals("description") 
                            && !key.equals("cost") && !key.equals("cooldown")) {
                        activeParams.put(key, activeSection.get(key));
                    }
                }
            }

            definitions.put(spec, new SpecDef(
                    spec, passiveId, passiveDesc, activeId, activeDesc,
                    activeCost, activeCooldown, passiveParams, activeParams
            ));
        }

        plugin.getLogger().info("Загружено " + definitions.size() + " специализаций");
    }

    public SpecDef get(Spec spec) {
        return definitions.get(spec);
    }
}
