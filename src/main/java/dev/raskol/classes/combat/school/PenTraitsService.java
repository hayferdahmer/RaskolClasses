// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;
import dev.raskol.classes.talent.TalentModel;
import dev.raskol.classes.talent.TalentsRegistry;

import java.util.List;
import java.util.UUID;

/**
 * 1.12.2 (Блок 3): агрегатор пробития из НЕ-гиревых источников:
 *  - таланты: купленные узлы с эффектом kind="pen", target="phys"|"magic"|<school id>,
 *    value = проценты (Sum по всем деревьям, где есть покупки);
 *  - спеки: passive-ключи specs.yml pen_phys / pen_magic / pen_<school> (проценты);
 *  - gear: делегирует GearHook (Блок 2), переводя доли обратно в проценты для суммы.
 *
 * Итог суммируется в процентах и один раз клампится в schools.pen-pct-cap
 * (clampSumPercent — pure, selftest-чек 59). Живая проводка в урон — Блок 4.
 *
 * Контент сейчас отсутствует (pen-узлов/ключей нет) → все суммы 0,
 * поведение идентично 1.12.1 (selftest-чек 60).
 */
public final class PenTraitsService {

    private final RaskolClasses plugin;

    public PenTraitsService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * Pure: сумма процентов пробития → доля с капом (capFraction, дефолт 0.40).
     * Отрицательное/NaN → 0. Selftest-чек 59.
     */
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

    /** Pen-проценты талантов: сумма value купленных узлов kind="pen" с target=key. */
    public double talentPenPercent(UUID uuid, String key) {
        if (uuid == null || key == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (Spec s : Spec.values()) {
            TalentModel.TalentTree tree = TalentsRegistry.treeOf(s.id());
            if (tree == null) {
                continue;
            }
            List<String> owned = plugin.getTalentsStorage().getPurchased(uuid, s.id());
            if (owned == null || owned.isEmpty()) {
                continue;
            }
            for (TalentModel.TalentNode node : tree.nodes()) {
                if (!owned.contains(node.id())) {
                    continue;
                }
                TalentModel.TalentEffect e = node.effect();
                if (e == null || !"pen".equals(e.kind()) || !key.equals(e.target())) {
                    continue;
                }
                sum += e.value();
            }
        }
        return sum;
    }

    /** Pen-проценты шмота (GearHook хранит доли → переводим в проценты для суммы). */
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
        Spec spec = plugin.getSpecService().getSpec(uuid);
        return gearPenPercent(uuid, key, gear)
                + talentPenPercent(uuid, key)
                + specPenPercent(spec, key);
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
