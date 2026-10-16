// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * Миграция конфигурации между версиями.
 *
 * 1.14.8 (Волна 6.2, P0-8-01): migrate148() переносит секции avoidance и hp-display
 * из attributes.* в корень. Без миграции на серверах, обновляющихся с 1.14.6,
 * код читает корень (где ключей нет) и берёт дефолты — баланс уклонения
 * молча меняется (parry-k 300→150, dodge-формулы).
 */
public final class ConfigMigrator {

    private ConfigMigrator() {
    }

    /**
     * Миграция 1.14.7 → 1.14.8: перенос avoidance/hp-display в корень.
     * Вызывается в onEnable после saveDefaultConfig(). Идемпотентна.
     *
     * @param plugin экземпляр плагина
     * @return true если миграция выполнена и файл сохранён
     */
    public static boolean migrate148(RaskolClasses plugin) {
        FileConfiguration config = plugin.getConfig();
        boolean migrated = false;

        // Ключи для миграции: attributes.avoidance.* → avoidance.*
        List<String> avoidanceKeys = List.of(
                "parry-k", "dodge-k", "parry-max", "dodge-max",
                "parry-cooldown-ticks", "dodge-cooldown-ticks",
                "block-visuals", "block-sound"
        );

        for (String key : avoidanceKeys) {
            String oldPath = "attributes.avoidance." + key;
            String newPath = "avoidance." + key;
            if (config.isSet(oldPath) && !config.isSet(newPath)) {
                Object value = config.get(oldPath);
                config.set(newPath, value);
                plugin.getLogger().warning("Миграция 1.14.8: " + oldPath + " → " + newPath
                        + " (значение " + value + ")");
                migrated = true;
            }
        }

        // Ключи для миграции: attributes.hp-display.* → hp-display.*
        List<String> hpDisplayKeys = List.of(
                "show-bar", "bar-title", "bar-color", "bar-style",
                "show-actionbar", "actionbar-format"
        );

        for (String key : hpDisplayKeys) {
            String oldPath = "attributes.hp-display." + key;
            String newPath = "hp-display." + key;
            if (config.isSet(oldPath) && !config.isSet(newPath)) {
                Object value = config.get(oldPath);
                config.set(newPath, value);
                plugin.getLogger().warning("Миграция 1.14.8: " + oldPath + " → " + newPath
                        + " (значение " + value + ")");
                migrated = true;
            }
        }

        if (migrated) {
            try {
                config.save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.getLogger().warning("Миграция 1.14.8 завершена: config.yml обновлён. "
                        + "Рекомендуется удалить старые секции attributes.avoidance и attributes.hp-display вручную.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить config.yml после миграции", e);
                return false;
            }
        }

        return migrated;
    }
}
