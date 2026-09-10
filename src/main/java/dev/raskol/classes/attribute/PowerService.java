// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.7.1: производные боевые статы (единственный источник силы для урона/хилов):
 *   WP   (Сила оружия)   = base-wp(class)  + STR×str-to-wp + AGI×agi-to-wp
 *   SP   (Сила заклинаний) = base-sp(class) + INT×int-to-sp
 *   HPow (Сила исцеления) = base-hpow(class) + INT×int-to-hpow
 *
 * Референс на 40 ур. (дефолты конфига):
 *   Воин WP 133 · Охотник WP 104 · Разбойник WP 104 · Маг SP 120 · Жрец SP 115 / HPow 109.
 *
 * Урон способности (киты 1.7.2+): dmg = base + Power × coeff (power-тег способности:
 * wp | sp | hpow). Базовый удар класса: ванильное оружие + WP × basic-coeff.
 * Все базы/коэффициенты — в конфиге attributes.power.*, тюнинг без пересборки.
 */
public final class PowerService {

    private final RaskolClasses plugin;

    public PowerService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double baseOf(String section, PlayerClass pc, double def) {
        double v = plugin.getConfig().getDouble("attributes.power." + section + "." + pc.name(), def);
        return Double.isFinite(v) ? v : def;
    }

    private static double defaultWp(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 30;
            case HUNTER -> 35;
            case ROGUE -> 30;
            case MAGE -> 5;
            case PRIEST -> 10;
        };
    }

    private static double defaultSp(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR, HUNTER, ROGUE -> 5;
            case MAGE -> 30;
            case PRIEST -> 25;
        };
    }

    private static double defaultHp(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR, HUNTER, ROGUE -> 0;
            case MAGE -> 15;
            case PRIEST -> 25;
        };
    }

    /** Сила оружия: база + STR×1.5 + AGI×0.5 (коэффициенты конфиг). */
    public double weaponPower(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return 0.0;
        }
        AttributeService attrs = plugin.getAttributes();
        double str = attrs.value(uuid, AttributeType.STR);
        double agi = attrs.value(uuid, AttributeType.AGI);
        return baseOf("base-wp", pc, defaultWp(pc))
                + str * cfgD("attributes.power.str-to-wp", 1.5)
                + agi * cfgD("attributes.power.agi-to-wp", 0.5);
    }

    /** Сила заклинаний: база + INT×1.5. */
    public double spellPower(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return 0.0;
        }
        AttributeService attrs = plugin.getAttributes();
        double intel = attrs.value(uuid, AttributeType.INT);
        return baseOf("base-sp", pc, defaultSp(pc))
                + intel * cfgD("attributes.power.int-to-sp", 1.5);
    }

    /** Сила исцеления: база + INT×1.4 (киты жреца 1.7.4). */
    public double healPower(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return 0.0;
        }
        AttributeService attrs = plugin.getAttributes();
        double intel = attrs.value(uuid, AttributeType.INT);
        return baseOf("base-hpow", pc, defaultHp(pc))
                + intel * cfgD("attributes.power.int-to-hpow", 1.4);
    }

    /** Power по тегу способности: wp | sp | hpow (неизвестный тег → 0). */
    public double powerFor(UUID uuid, String powerTag) {
        if (powerTag == null) {
            return 0.0;
        }
        return switch (powerTag.toLowerCase(java.util.Locale.ROOT)) {
            case "wp" -> weaponPower(uuid);
            case "sp" -> spellPower(uuid);
            case "hpow" -> healPower(uuid);
            default -> 0.0;
        };
    }

    /**
     * Урон способности (киты 1.7.2+): base + Power × coeff, кламп снизу 0.
     * Коэффициенты живут в конфиге кита; здесь только математика.
     */
    public double abilityDamage(UUID uuid, String powerTag, double base, double coeff) {
        double safeBase = Double.isFinite(base) && base >= 0 ? base : 0.0;
        double safeCoeff = Double.isFinite(coeff) ? coeff : 0.0;
        return safeBase + powerFor(uuid, powerTag) * safeCoeff;
    }

    /** Хил способности (киты 1.7.4): base + HPow × coeff, кламп снизу 0. */
    public double abilityHeal(UUID uuid, double base, double coeff) {
        double safeBase = Double.isFinite(base) && base >= 0 ? base : 0.0;
        double safeCoeff = Double.isFinite(coeff) ? coeff : 0.0;
        return safeBase + healPower(uuid) * safeCoeff;
    }
}
