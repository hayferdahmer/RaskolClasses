// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Set;

/**
 * 1.6.7: харденинг конфига резистов и урона.
 * Проверяет на старте и при /rc reload:
 *  - resist.cap, resist.classes.*, resist.grants.*, resist.mana-soaked.*,
 *    resist.specs.* — диапазон 0..100 и конечность (ловит .nan/.inf);
 *  - damage-types.vanilla-map.* — значение из {physical, magic, true};
 *  - classes.*.abilities.*.damage-physical/magic и installations.*.damage-* — ≥ 0.
 * Каждое нарушение = WARNING с путём ключа; рантайм-безопасность обеспечивается
 * клампом итога в ResistService и фолбэками в CombatService.typeOf, поэтому
 * битое значение не роняет бой, а подменяется дефолтом/зажимом.
 */
public final class ConfigValidator {

    private static final Set<String> DAMAGE_TYPES = Set.of("physical", "magic", "true");

    private final RaskolClasses plugin;
    private int problems;

    public ConfigValidator(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Полный прогон проверок. Возвращает число проблем. */
    public int validate() {
        problems = 0;
        FileConfiguration cfg = plugin.getConfig();

        checkRange(cfg, "resist.cap", 0.0, 100.0);

        ConfigurationSection classes = cfg.getConfigurationSection("resist.classes");
        if (classes != null) {
            for (String pc : classes.getKeys(false)) {
                checkRange(cfg, "resist.classes." + pc + ".physical", 0.0, 100.0);
                checkRange(cfg, "resist.classes." + pc + ".magic", 0.0, 100.0);
            }
        }

        ConfigurationSection grants = cfg.getConfigurationSection("resist.grants");
        if (grants != null) {
            for (String id : grants.getKeys(false)) {
                checkRange(cfg, "resist.grants." + id + ".physical", 0.0, 100.0);
                checkRange(cfg, "resist.grants." + id + ".magic", 0.0, 100.0);
            }
        }

        checkRange(cfg, "resist.mana-soaked.physical", 0.0, 100.0);
        checkRange(cfg, "resist.mana-soaked.magic", 0.0, 100.0);

        ConfigurationSection specs = cfg.getConfigurationSection("resist.specs");
        if (specs != null) {
            for (String id : specs.getKeys(false)) {
                checkRange(cfg, "resist.specs." + id + ".physical", 0.0, 100.0);
                checkRange(cfg, "resist.specs." + id + ".magic", 0.0, 100.0);
            }
        }

        ConfigurationSection map = cfg.getConfigurationSection("damage-types.vanilla-map");
        if (map != null) {
            for (String cause : map.getKeys(false)) {
                String value = map.getString(cause, "");
                if (value == null || !DAMAGE_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
                    warn("damage-types.vanilla-map." + cause, String.valueOf(value),
                            "physical|magic|true");
                }
            }
        }

        ConfigurationSection cls = cfg.getConfigurationSection("classes");
        if (cls != null) {
            for (String pc : cls.getKeys(false)) {
                ConfigurationSection abilities = cls.getConfigurationSection(pc + ".abilities");
                if (abilities == null) {
                    continue;
                }
                for (String id : abilities.getKeys(false)) {
                    checkNonNegative(cfg, "classes." + pc + ".abilities." + id + ".damage-physical");
                    checkNonNegative(cfg, "classes." + pc + ".abilities." + id + ".damage-magic");
                }
            }
        }

        ConfigurationSection installs = cfg.getConfigurationSection("installations");
        if (installs != null) {
            for (String id : installs.getKeys(false)) {
                checkNonNegative(cfg, "installations." + id + ".damage-physical");
                checkNonNegative(cfg, "installations." + id + ".damage-magic");
            }
        }

        if (problems == 0) {
            plugin.getLogger().info("ConfigValidator: конфиг резистов/урона валиден.");
        } else {
            plugin.getLogger().warning("ConfigValidator: проблем в конфиге резистов/урона: "
                    + problems + " (см. WARNING выше; значения подменены дефолтом/зажимом).");
        }
        return problems;
    }

    /** Стартовая сводка эффективных баз резистов по классам. */
    public void logSummary() {
        StringBuilder sb = new StringBuilder("Базы резистов (маг/физ): ");
        for (PlayerClass pc : PlayerClass.values()) {
            sb.append(pc.name()).append(' ')
                    .append((int) plugin.getResists().baseMagic(pc)).append('/')
                    .append((int) plugin.getResists().basePhysical(pc)).append("; ");
        }
        sb.append("кап ").append((int) plugin.getResists().cap());
        plugin.getLogger().info(sb.toString());
    }

    private void checkRange(FileConfiguration cfg, String path, double min, double max) {
        if (!cfg.isSet(path)) {
            return;
        }
        double v = cfg.getDouble(path, min);
        if (!Double.isFinite(v) || v < min || v > max) {
            warn(path, String.valueOf(v), min + ".." + max);
        }
    }

    private void checkNonNegative(FileConfiguration cfg, String path) {
        if (!cfg.isSet(path)) {
            return;
        }
        double v = cfg.getDouble(path, 0.0);
        if (!Double.isFinite(v) || v < 0.0) {
            warn(path, String.valueOf(v), ">= 0");
        }
    }

    private void warn(String path, String value, String expected) {
        problems++;
        plugin.getLogger().warning("RaskolClasses конфиг: некорректно " + path + " = " + value
                + " (ожидание: " + expected + ") — применено значение по умолчанию/зажим.");
    }
}
