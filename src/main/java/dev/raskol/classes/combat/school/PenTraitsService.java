// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemStat;

import java.util.UUID;

/**
 * 1.12.2 (Блок 3) → 1.14.0 (Б8): агрегатор пробития из НЕ-гиревых источников.
 * Источники: ItemStatsProvider + spec2-деревья (узлы pen_phys_pct/pen_magic_pct/
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

    /** Pen-проценты предметного снапшота, хранятся в процентных пунктах. */
    public double itemPenPercent(UUID uuid, String key) {
        if (plugin.getItems() == null || uuid == null || key == null) {
            return 0.0;
        }
        ItemStat stat = "phys".equals(key) ? ItemStat.PEN_PHYS_PCT
                : "magic".equals(key) ? ItemStat.PEN_MAGIC_PCT : ItemStat.fromId("pen-" + key + "-pct");
        return stat == null ? 0.0 : plugin.getItems().value(uuid, stat);
    }

    /** Сумма всех источников pen по ключу ("phys"/"magic"/<school id>), в процентах. */
    public double totalPenPercent(UUID uuid, String key) {
        return itemPenPercent(uuid, key) + talentPenPercent(uuid, key);
    }

    /** Канальное пробитие (phys/magic) как Penetration с капом pen-pct-cap. */
    public Penetration channelPen(UUID uuid, boolean physical, double cap) {
        double pct = totalPenPercent(uuid, physical ? "phys" : "magic");
        ItemStat flat = physical ? ItemStat.PEN_PHYS_FLAT : ItemStat.PEN_MAGIC_FLAT;
        double amount = plugin.getItems() == null ? 0.0 : plugin.getItems().value(uuid, flat);
        return Penetration.of(amount, clampSumPercent(pct, cap));
    }

    /** Стихийное пробитие школы (доля, с капом) — режет elemental-резист цели. */
    public double schoolPenFraction(UUID uuid, School school, double cap) {
        if (school == null) {
            return 0.0;
        }
        return clampSumPercent(totalPenPercent(uuid, school.id()), cap);
    }
}
