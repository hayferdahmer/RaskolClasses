// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.GearHook;

import java.util.UUID;

/**
 * 1.12.2 (Блок 3) → 1.14.0 (Б8): агрегатор пробития из НЕ-гиревых источников.
 * Источники: gear (GearHook) + spec2-деревья (узлы pen_phys_pct/pen_magic_pct/
 * pen_<school> через Spec2Service.penPercent). Legacy specs.yml/talents удалены.
 * Сумма в процентах, один кламп в schools.pen-pct-cap (clampSumPercent, чек 59).
 */
public final class PenTraitsService {

    private final RaskolClasses plugin;

    public PenTraitsService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Pure: сумма процентов → доля с капом; отрицательное/NaN → 0. */
    public static double clampSumPercent(double pctSum, double capFraction) {
        if (!Double.isFinite(pctSum) || pctSum <= 0.0) {
            return 0.0;
        }
        double cap = Double.isFinite(capFraction) && capFraction > 0.0 ? capFraction : 0.40;
        return Math.min(pctSum / 100.0, cap);
    }

    /** Pen-проценты деревьев путей (spec2-агрегат). */
    public double talentPenPercent(UUID uuid, String key) {
        if (uuid == null || key == null || plugin.getSpec2Service() == null) {
            return 0.0;
        }
        return plugin.getSpec2Service().penPercent(uuid, key);
    }

    /** Pen-проценты шмота (GearHook хранит доли → переводим в проценты). */
    public double gearPenPercent(UUID uuid, String key, GearHook gear) {
        if (gear == null || uuid == null || key == null) {
            return 0.0;
        }
        double fraction;
        if ("phys".equals(key)) {
            fraction = gear.penPhys(uuid);
        } else if ("magic".equals(key)) {
            fraction = gear.penMagic(uuid);
        } else {
            School school = School.fromId(key);
            fraction = school == null ? 0.0 : gear.penSchool(uuid, school);
        }
        return fraction * 100.0;
    }

    /** Сумма всех источников pen по ключу ("phys"/"magic"/<school id>), в процентах. */
    public double totalPenPercent(UUID uuid, String key, GearHook gear) {
        return gearPenPercent(uuid, key, gear) + talentPenPercent(uuid, key);
    }

    /** Канальное пробитие (phys/magic) как Penetration с капом pen-pct-cap. */
    public Penetration channelPen(UUID uuid, boolean physical, GearHook gear, double cap) {
        double pct = totalPenPercent(uuid, physical ? "phys" : "magic", gear);
        return Penetration.of(0.0, clampSumPercent(pct, cap));
    }

    /** Стихийное пробитие школы (доля, с капом) — режет elemental-резист цели. */
    public double schoolPenFraction(UUID uuid, School school, GearHook gear, double cap) {
        if (school == null) {
            return 0.0;
        }
        return clampSumPercent(totalPenPercent(uuid, school.id(), gear), cap);
    }
}
