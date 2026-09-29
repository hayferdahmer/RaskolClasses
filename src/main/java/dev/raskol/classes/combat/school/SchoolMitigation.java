// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.combat.CombatMath;

/**
 * 1.12.1: формула прохождения урона школы через защиту (ТЗ п.3–5).
 *  mitigation = 1 − (1 − mitChannel) × (1 − mitElemental), сверху кап mitigation-cap;
 *  mitChannel считается от резиста канала ПОСЛЕ пробития (flat → pct);
 *  стихийный слой (mitElemental) выключен до 1.12.2 (schools.elemental.enabled=false).
 * Pure-статика: тестируется selftest-чеком 52 без Bukkit-контекста.
 */
public final class SchoolMitigation {

    private SchoolMitigation() {
    }

    /**
     * Итоговое поглощение [0, mitigationCap].
     *
     * @param channelResistPct  резист канала цели (физ/маг), уже с капами ResistService
     * @param pen               пробитие атаки
     * @param pctPenCap         кап процентного пробития (schools.pen-pct-cap)
     * @param elementalResistPct стихийный резист школы (0, если слой выключен)
     * @param elementalEnabled   рубильник стихийного слоя (schools.elemental.enabled)
     * @param mitigationCap      кап суммарного поглощения (schools.mitigation-cap)
     */
    public static double mitigationFor(double channelResistPct, Penetration pen, double pctPenCap,
                                       double elementalResistPct, boolean elementalEnabled,
                                       double mitigationCap) {
        Penetration safe = pen == null ? Penetration.NONE : pen;
        double effResist = safe.effectiveResist(channelResistPct, pctPenCap);
        double mitChannel = CombatMath.mitigation(effResist, 100.0);
        double mitElemental = elementalEnabled
                ? CombatMath.mitigation(elementalResistPct, 100.0)
                : 0.0;
        double combined = 1.0 - (1.0 - mitChannel) * (1.0 - mitElemental);
        double cap = Double.isFinite(mitigationCap) ? mitigationCap : 0.80;
        return Math.max(0.0, Math.min(combined, cap));
    }

    /** final = base × (1 − mitigation) × schoolMult (ТЗ п.8, порядок множителей). */
    public static double taken(double base, double mitigation, double schoolMult) {
        if (!Double.isFinite(base) || base <= 0.0) {
            return 0.0;
        }
        double mit = Math.max(0.0, Math.min(1.0, Double.isFinite(mitigation) ? mitigation : 0.0));
        double mult = Math.max(0.0, Double.isFinite(schoolMult) ? schoolMult : 1.0);
        return Math.max(0.0, base * (1.0 - mit) * mult);
    }
}
