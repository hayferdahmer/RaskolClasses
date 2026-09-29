// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.combat.CombatMath;

/**
 * 1.12.1: пробитие защиты урона-инстанции.
 *  - flat — плоское вычитание из резиста (в процентных пунктах);
 *  - pct  — процентный игнор ОТ ОСТАТКА после flat (порядок из ТЗ п.5);
 *  - clamped(pctCap) — кап процентного пробития (schools.pen-pct-cap, дефолт 0.40).
 * Immutable record; NONE — нулевое пробитие (legacy-поведение).
 */
public record Penetration(double flat, double pct) {

    public static final Penetration NONE = new Penetration(0.0, 0.0);

    public static Penetration of(double flat, double pct) {
        double f = Double.isFinite(flat) && flat >= 0.0 ? flat : 0.0;
        double p = Double.isFinite(pct) && pct >= 0.0 ? pct : 0.0;
        return new Penetration(f, p);
    }

    /** Кап процентного пробития (не может увести резист ниже нуля — гарантирует CombatMath). */
    public Penetration clamped(double pctCap) {
        double cap = Double.isFinite(pctCap) && pctCap >= 0.0 ? pctCap : 0.40;
        return new Penetration(flat, Math.min(pct, cap));
    }

    /** Эффективный резист цели после пробития: (resist − flat) × (1 − pct), пол 0. */
    public double effectiveResist(double resistPct, double pctPenCap) {
        return CombatMath.effectiveResist(resistPct, flat, Math.min(pct, pctPenCap), pctPenCap);
    }
}
