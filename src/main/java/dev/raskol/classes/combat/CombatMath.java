// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

/**
 * 1.11.4 (P3): pure-математика боя без Bukkit-зависимостей.
 * Всё тестируется headless (selftest-чеки 16, 41+).
 * Задел 1.12.0: effectiveResist/mitigation — порядок пробития (flat → pct).
 */
public final class CombatMath {

    private CombatMath() {
    }

    /** Анти-ваншот: урон за один удар ≤ pct% от maxHp. pct ≤ 0 = выключено. */
    public static double cappedDamage(double damage, double maxHp, double pct) {
        if (!Double.isFinite(damage) || damage <= 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(maxHp) || maxHp <= 0.0) {
            return damage;
        }
        if (!Double.isFinite(pct) || pct <= 0.0) {
            return damage;
        }
        double limit = maxHp * pct / 100.0;
        return damage > limit ? limit : damage;
    }

    /** NaN/отрицательные защиты для профиля урона. */
    public static DamageProfile sanitize(DamageProfile p) {
        double phys = Double.isFinite(p.physical()) && p.physical() >= 0 ? p.physical() : 0.0;
        double magic = Double.isFinite(p.magic()) && p.magic() >= 0 ? p.magic() : 0.0;
        double truth = Double.isFinite(p.trueDamage()) && p.trueDamage() >= 0 ? p.trueDamage() : 0.0;
        if (phys == p.physical() && magic == p.magic() && truth == p.trueDamage()) {
            return p;
        }
        return new DamageProfile(phys, magic, truth);
    }

    /**
     * 1.12-prep: эффективный резист после пробития.
     * Порядок: flat вычитается ПЕРВЫМ (не съедается процентом), затем pct от остатка.
     * pctPen ограничен капом (рекомендация 40%), результат не ниже 0.
     */
    public static double effectiveResist(double resistPct, double flatPen,
                                         double pctPen, double pctPenCap) {
        double safeFlat = Math.max(0.0, Double.isFinite(flatPen) ? flatPen : 0.0);
        double safePct = Math.max(0.0, Math.min(
                Double.isFinite(pctPenCap) ? pctPenCap : 0.40,
                Double.isFinite(pctPen) ? pctPen : 0.0));
        double afterFlat = Math.max(0.0, resistPct - safeFlat);
        return Math.max(0.0, afterFlat * (1.0 - safePct));
    }

    /** Процент резиста → множитель поглощения [0, cap/100]. */
    public static double mitigation(double resistPct, double capPct) {
        double safe = Double.isFinite(resistPct) ? resistPct : 0.0;
        double cap = Double.isFinite(capPct) ? capPct : 90.0;
        return Math.max(0.0, Math.min(safe, cap)) / 100.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
