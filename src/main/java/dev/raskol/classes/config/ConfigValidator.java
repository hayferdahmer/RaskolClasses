// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.ResistService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

/**
 * 1.9.1: харденинг конфига (расширение с 1.6.7).
 * Проверяет на старте и при /rc reload:
 *  - resist.cap, resist.classes.*, resist.grants.*, resist.mana-soaked.*,
 *    resist.specs.* — диапазон 0..100 и конечность (ловит .nan/.inf);
 *  - damage-types.vanilla-map.* — значение из {physical, magic, true};
 *  - classes.*.abilities.*.damage-physical/magic и installations.*.damage-* — ≥ 0;
 *  - talents.* — start-level ≥ 0, points-per-level ≥ 1, max-points ≥ 0,
 *    reset-base/reset-per-point ≥ 0;
 *  - character-level.* — top-n ≥ 1, fallback ≥ 0;
 *  - combat.burst-* — window-seconds ≥ 0, window-pct 0..100;
 *  - installations.frost_rune.* — radius ≥ 0, duration ≥ 0, cooldown ≥ 0,
 *    damage-* ≥ 0, slow-ramp-every ≥ 1, slow-max-tier ≥ 0, mage-* ≥ 0.
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

        // === resist ===
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

        // === damage-types ===
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

        // === classes.*.abilities ===
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

        // === installations ===
        ConfigurationSection installs = cfg.getConfigurationSection("installations");
        if (installs != null) {
            for (String id : installs.getKeys(false)) {
                checkNonNegative(cfg, "installations." + id + ".damage-physical");
                checkNonNegative(cfg, "installations." + id + ".damage-magic");
            }
        }

        // === 1.9.0: talents ===
        checkNonNegative(cfg, "talents.start-level");
        checkPositive(cfg, "talents.points-per-level", 1);
        checkNonNegative(cfg, "talents.max-points");
        checkNonNegative(cfg, "talents.reset-base");
        checkNonNegative(cfg, "talents.reset-per-point");

        // === 1.8.0: character-level ===
        checkPositive(cfg, "character-level.top-n", 1);
        checkNonNegative(cfg, "character-level.fallback");

        // === combat.burst-* ===
        checkNonNegative(cfg, "combat.burst-window-seconds");
        checkRange(cfg, "combat.burst-window-pct", 0.0, 100.0);

        // === installations.frost_rune ===
        checkNonNegative(cfg, "installations.frost_rune.radius");
        checkNonNegative(cfg, "installations.frost_rune.duration");
        checkNonNegative(cfg, "installations.frost_rune.cooldown");
        checkNonNegative(cfg, "installations.frost_rune.damage-base");
        checkNonNegative(cfg, "installations.frost_rune.damage-ramp");
        checkNonNegative(cfg, "installations.frost_rune.damage-cap");
        checkPositive(cfg, "installations.frost_rune.slow-ramp-every", 1);
        checkNonNegative(cfg, "installations.frost_rune.slow-max-tier");
        checkNonNegative(cfg, "installations.frost_rune.mage-mana-per-sec");
        checkNonNegative(cfg, "installations.frost_rune.mage-int-mult");

        if (problems == 0) {
            plugin.getLogger().info("ConfigValidator: конфиг валиден (resist/damage/talents/character-level/burst/frost_rune).");
        } else {
            plugin.getLogger().warning("ConfigValidator: проблем в конфиге: "
                    + problems + " (см. WARNING выше; значения подменены дефолтом/зажимом).");
        }
        return problems;
    }

    /**
     * Стартовая сводка эффективных баз резистов по классам.
     * Читает базы через breakdown() первого онлайн-игрока каждого класса;
     * если онлайн-игрока класса нет — печатает 0/0 для этого класса.
     */
    public void logSummary() {
        StringBuilder sb = new StringBuilder("Базы резистов (маг/физ): ");
        for (PlayerClass pc : PlayerClass.values()) {
            double baseMagic = 0;
            double basePhys = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getClassProvider().getClassOf(online) == pc) {
                    ResistService.Breakdown rb = plugin.getResists().breakdown(online.getUniqueId());
                    baseMagic = rb.baseMagic();
                    basePhys = rb.basePhysical();
                    break;
                }
            }
            sb.append(pc.name()).append(' ')
                    .append((int) baseMagic).append('/')
                    .append((int) basePhys).append("; ");
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

    private void checkPositive(FileConfiguration cfg, String path, int min) {
        if (!cfg.isSet(path)) {
            return;
        }
        int v = cfg.getInt(path, min);
        if (v < min) {
            warn(path, String.valueOf(v), ">= " + min);
        }
    }

    private void warn(String path, String value, String expected) {
        problems++;
        plugin.getLogger().warning("RaskolClasses конфиг: некорректно " + path + " = " + value
                + " (ожидание: " + expected + ") — применено значение по умолчанию/зажим.");
    }
}
