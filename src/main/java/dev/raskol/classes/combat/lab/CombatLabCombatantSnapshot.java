// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.classsystem.PlayerClass;

/**
 * Неизменяемый снимок боевых характеристик для headless-сценариев.
 *
 * Снимок читает те же ключи config.yml и вызывает те же чистые формулы,
 * что используются игровыми сервисами. Bukkit-объекты в боевое ядро не передаются.
 */
public record CombatLabCombatantSnapshot(
        PlayerClass playerClass,
        int level,
        double strength,
        double agility,
        double intellect,
        double maxHealth,
        double weaponPower,
        double spellPower,
        double healPower,
        double physicalResistPct,
        double magicResistPct,
        double meleeCritChancePct,
        double meleeCritMultiplier,
        double dodgeChancePct,
        double parryChancePct,
        double healthRegenPerSecond) {

    public static CombatLabCombatantSnapshot from(
            YamlConfiguration config, PlayerClass playerClass, int level) {
        if (config == null || playerClass == null) {
            throw new IllegalArgumentException("config и playerClass обязательны");
        }
        if (level < 1 || level > config.getInt("attributes.level-cap", 60)) {
            throw new IllegalArgumentException("Недопустимый уровень персонажа: " + level);
        }

        String classPath = "attributes.classes." + playerClass.name() + ".";
        double strength = stat(config, classPath, "base-str", 0.0)
                + stat(config, classPath, "growth-str", 0.0) * level;
        double agility = stat(config, classPath, "base-agi", 0.0)
                + stat(config, classPath, "growth-agi", 0.0) * level;
        double intellect = stat(config, classPath, "base-int", 0.0)
                + stat(config, classPath, "growth-int", 0.0) * level;
        boolean strengthMain = "STR".equalsIgnoreCase(config.getString(classPath + "main", ""));

        double maxHealth = AttributeMath.maxHp(
                strength,
                level,
                strengthMain,
                config.getDouble("attributes.hp.base-hp", 100.0),
                config.getDouble("attributes.hp.per-str", 20.0),
                config.getDouble("attributes.hp.per-level", 5.0),
                config.getDouble("attributes.hp.main-str-bonus", 8.0));

        double weaponPower = PowerService.weaponPowerFormula(
                config.getDouble("attributes.power.base-wp." + playerClass.name(), defaultWeaponPower(playerClass)),
                strength,
                agility,
                config.getDouble("attributes.power.str-to-wp", 1.5),
                config.getDouble("attributes.power.agi-to-wp", 0.5));
        double spellPower = PowerService.spellPowerFormula(
                config.getDouble("attributes.power.base-sp." + playerClass.name(), defaultSpellPower(playerClass)),
                intellect,
                config.getDouble("attributes.power.int-to-sp", 1.5));
        double healPower = PowerService.healPowerFormula(
                config.getDouble("attributes.power.base-hpow." + playerClass.name(), defaultHealPower(playerClass)),
                intellect,
                config.getDouble("attributes.power.int-to-hpow", 1.4));

        double critChance = AttributeMath.critMelee(
                agility,
                config.getDouble("attributes.crit.melee-base", 5.0),
                config.getDouble("attributes.crit.melee-per-agi", 0.05),
                config.getDouble("attributes.crit.melee-cap", 40.0));

        double dodge = AttributeMath.dodgeRaw(agility, config.getDouble("avoidance.dodge-k", 100.0));
        double parryFull = AttributeMath.parryRaw(strength, config.getDouble("avoidance.parry-k", 300.0));
        boolean agilityMain = "AGI".equalsIgnoreCase(config.getString(classPath + "main", ""));
        if (agilityMain) {
            double micro = config.getDouble("avoidance.agi-main-parry-micro", 0.5);
            dodge += Math.max(0.0, parryFull - micro)
                    * config.getDouble("avoidance.agi-main-dodge-refund", 0.5);
        }
        dodge *= config.getDouble("avoidance.dodge-mult", 0.5);
        double parry = agilityMain ? config.getDouble("avoidance.agi-main-parry-micro", 0.5) : parryFull;
        double totalAvoidance = dodge + parry;
        double effectiveAvoidance = AttributeMath.applyDR(
                totalAvoidance,
                config.getDouble("avoidance.soft-cap", 60.0),
                config.getDouble("avoidance.dr-factor", 0.5),
                config.getDouble("avoidance.hard-cap", 75.0));
        double[] split = AttributeMath.splitEff(dodge, parry, effectiveAvoidance);

        String resistPath = "resist.classes." + playerClass.name() + ".";
        double physicalResist = finiteOr(
                config.getDouble(resistPath + "physical", defaultPhysicalResist(playerClass)),
                defaultPhysicalResist(playerClass));
        double magicResist = finiteOr(
                config.getDouble(resistPath + "magic", defaultMagicResist(playerClass)),
                defaultMagicResist(playerClass));

        double regen = AttributeMath.strRegenPerSecond(
                strength,
                config.getDouble("attributes.hp.regen-per-str", 0.025),
                maxHealth,
                true,
                config.getDouble("attributes.hp.regen-combat-factor", 0.35),
                config.getDouble("attributes.hp.regen-cap-pct", 1.5));

        return new CombatLabCombatantSnapshot(
                playerClass,
                level,
                strength,
                agility,
                intellect,
                maxHealth,
                weaponPower,
                spellPower,
                healPower,
                physicalResist,
                magicResist,
                critChance,
                config.getDouble("attributes.crit.melee-mult", 1.5),
                split[0],
                split[1],
                regen);
    }

    private static double stat(YamlConfiguration config, String path, String key, double fallback) {
        return finiteOr(config.getDouble(path + key, fallback), fallback);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double defaultWeaponPower(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> 30.0;
            case HUNTER -> 35.0;
            case ROGUE -> 30.0;
            case MAGE -> 5.0;
            case PRIEST, WARLOCK -> playerClass == PlayerClass.PRIEST ? 10.0 : 5.0;
        };
    }

    private static double defaultSpellPower(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR, HUNTER, ROGUE -> 5.0;
            case MAGE -> 30.0;
            case PRIEST -> 25.0;
            case WARLOCK -> 40.0;
        };
    }

    private static double defaultHealPower(PlayerClass playerClass) {
        return switch (playerClass) {
            case MAGE -> 15.0;
            case PRIEST -> 25.0;
            case WARRIOR, HUNTER, ROGUE, WARLOCK -> 0.0;
        };
    }

    private static double defaultPhysicalResist(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> 18.0;
            case ROGUE -> 14.0;
            case MAGE -> 12.0;
            case WARLOCK -> 10.0;
            case PRIEST, HUNTER -> 16.0;
        };
    }

    private static double defaultMagicResist(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR, ROGUE, HUNTER -> 12.0;
            case MAGE, WARLOCK -> 26.0;
            case PRIEST -> 30.0;
        };
    }
}
