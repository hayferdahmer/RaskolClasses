// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.13.0 (Б4): sanity-валидация секции cc.* конфига (аналог KitSanity для CC).
 * Вызываем из selftest-чека 89 и из ConfigValidator-потока при reload.
 *
 * 1.14.0-fix (чек 89): мгновенные CC (defaultTicks() <= 0, напр. KNOCKBACK)
 *    исключены из проверки duration-ticks — у них вместо длительности power/
 *    vertical-boost, и требовать [1,1200] тиков от мгновенного отброса — ошибка
 *    валидатора, а не конфига.
 */
public final class CcSanity {

    private CcSanity() {
    }

    /** Pure-хелпер диапазонов (selftest-чек 89). */
    public static boolean inRange(double v, double lo, double hi) {
        return Double.isFinite(v) && v >= lo && v <= hi;
    }

    /** Список проблем секции cc.*; пустой = конфиг здоров. */
    public static List<String> validateCc(RaskolClasses plugin) {
        List<String> problems = new ArrayList<>();
        FileConfiguration cfg = plugin.getConfig();

        double window = cfg.getDouble("cc.window-seconds", 15.0);
        if (!inRange(window, 0.5, 120.0)) {
            problems.add("cc.window-seconds вне [0.5,120]: " + window);
        }
        double resistCap = cfg.getDouble("cc.resist-cap", 0.60);
        if (!inRange(resistCap, 0.0, 1.0)) {
            problems.add("cc.resist-cap вне [0,1]: " + resistCap);
        }
        double redCap = cfg.getDouble("cc.duration-reduction-cap", 0.50);
        if (!inRange(redCap, 0.0, 1.0)) {
            problems.add("cc.duration-reduction-cap вне [0,1]: " + redCap);
        }
        double powerCap = cfg.getDouble("cc.power-cap", 0.50);
        if (!inRange(powerCap, 0.0, 1.0)) {
            problems.add("cc.power-cap вне [0,1]: " + powerCap);
        }
        double breakThr = cfg.getDouble("cc.breaks-on-damage-threshold-pct", 0.05);
        if (!inRange(breakThr, 0.0, 1.0)) {
            problems.add("cc.breaks-on-damage-threshold-pct вне [0,1]: " + breakThr);
        }

        List<Double> mults = cfg.getDoubleList("cc.dr-multipliers");
        if (mults == null || mults.isEmpty()) {
            problems.add("cc.dr-multipliers пуст или отсутствует");
        } else {
            for (double m : mults) {
                if (!inRange(m, 0.0, 1.0)) {
                    problems.add("cc.dr-multipliers значение вне [0,1]: " + m);
                }
            }
        }

        for (CCType t : CCType.values()) {
            // 1.14.0-fix (чек 89): мгновенные CC не имеют duration-ticks
            if (t.defaultTicks() <= 0) {
                continue;
            }
            int dur = cfg.getInt("cc.types." + t.id() + ".duration-ticks", t.defaultTicks());
            if (dur <= 0 || dur > 1200) {
                problems.add("cc.types." + t.id() + ".duration-ticks вне [1,1200]: " + dur);
            }
        }

        ConfigurationSection immSec = cfg.getConfigurationSection("cc.entity-immunity");
        if (immSec != null) {
            for (String key : immSec.getKeys(false)) {
                for (String s : immSec.getStringList(key)) {
                    if (CCType.fromId(s) == null) {
                        problems.add("cc.entity-immunity." + key + ": неизвестный CC-тип " + s);
                    }
                }
            }
        }

        for (PlayerClass pc : PlayerClass.values()) {
            double r = cfg.getDouble("cc.class-resist." + pc.name(), 0.0);
            if (!inRange(r, 0.0, 1.0)) {
                problems.add("cc.class-resist." + pc.name() + " вне [0,1]: " + r);
            }
        }
        return problems;
    }
}
