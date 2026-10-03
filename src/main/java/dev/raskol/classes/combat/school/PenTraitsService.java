// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;

import java.util.UUID;

/**
 * 1.12.2 → 1.14.0: агрегатор пробития из НЕ-гиревых источников.
 * 1.14.0 (Б3): талант-источник переехал на Spec2Service.penPercent (агрегат
 *         узлов pen_phys_pct/pen_magic_pct дерева путей); старые TalentStorage
 *         и TalentModel не читаются. Спека и gear — без изменений.
 */
public final class PenTraitsService {

    private final RaskolClasses plugin;

    public PenTraitsService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public static double clampSumPercent(double pctSum, double capFraction) {
        if (!Double.isFinite(pctSum) || pctSum <= 0.0) {
            return 0.0;
        }
        double cap = Double.isFinite(capFraction) && capFraction > 0.0 ? capFraction : 0.40;
        return Math.min(pctSum / 100.0, cap);
    }

    /** Pen-проценты спеки из specs.yml passive-ключей (pen_<key>). */
    public double specPenPercent(Spec spec, String key) {
        if (spec == null || key == null) {
            return 0.0;
        }
        SpecRegistry.SpecDef def = plugin.getSpecRegistry().get(spec);
        return def == null ? 0.0 : def.passiveDouble("pen_" + key, 0.0);
    }

    /**
     * 1.14.0 (Б3): pen-проценты дерева путей.
     * Агрегируются в Spec2Service.agg через kind pen_phys_pct/pen_magic_pct/pen_<school>;
     * Spec2Service.penPercent(uuid, key) возвращает готовые проценты.
     */
    public double talentPenPercent(UUID uuid, String key) {
        if (uuid == null || key == null) {
            return 0.0;
        }
        if (plugin.getSpec2Service() == null) {
            return 0.0;
        }
        return plugin.getSpec2Service().penPercent(uuid, key);
    }

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

    public double totalPenPercent(UUID uuid, String key, GearHook gear) {
        Spec spec = plugin.getSpecService().getSpec(uuid);
        return gearPenPercent(uuid, key, gear)
                + talentPenPercent(uuid, key)
                + specPenPercent(spec, key);
    }

    public Penetration channelPen(UUID uuid, boolean physical, GearHook gear, double cap) {
        double pct = totalPenPercent(uuid, physical ? "phys" : "magic", gear);
        return Penetration.of(0.0, clampSumPercent(pct, cap));
    }

    public double schoolPenFraction(UUID uuid, School school, GearHook gear, double cap) {
        if (school == null) {
            return 0.0;
        }
        return clampSumPercent(totalPenPercent(uuid, school.id(), gear), cap);
    }
}
