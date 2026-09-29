// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.dot;

/**
 * 1.12.4: pure-математика DoT-капов и множителей (selftest-чеки 66–67).
 *  - capFactor: масштабирование суммарного DPS под combat.dot-dps-cap-pct;
 *  - withMults: dps × множитель школы × иммунитет × (1 + seal-амплификация).
 */
public final class DotMath {

    private DotMath() {
    }

    /**
     * Масштабный фактор для суммарного DPS по цели:
     * sum ≤ limit → 1.0; sum > limit → limit/sum; sum ≤ 0 → 1.0 (ничего не применяем).
     */
    public static double capFactor(double[] dpsPerDot, double limit) {
        if (dpsPerDot == null || dpsPerDot.length == 0) {
            return 1.0;
        }
        double sum = 0.0;
        for (double v : dpsPerDot) {
            if (Double.isFinite(v) && v > 0.0) {
                sum += v;
            }
        }
        if (sum <= 0.0) {
            return 1.0;
        }
        if (!Double.isFinite(limit) || limit <= 0.0) {
            return 1.0;
        }
        return sum <= limit ? 1.0 : limit / sum;
    }

    /** dps × schoolMult × immunityMult × (1 + sealAmp). Любое небезопасное значение → 0. */
    public static double withMults(double dps, double schoolMult, double immunityMult, double sealAmp) {
        if (!Double.isFinite(dps) || dps <= 0.0) {
            return 0.0;
        }
        double sm = Double.isFinite(schoolMult) ? schoolMult : 1.0;
        double im = Double.isFinite(immunityMult) ? immunityMult : 1.0;
        double sa = Double.isFinite(sealAmp) && sealAmp >= 0.0 ? sealAmp : 0.0;
        double out = dps * sm * im * (1.0 + sa);
        return Double.isFinite(out) && out >= 0.0 ? out : 0.0;
    }

    /** Процентный кап → абсолютный лимит DPS от maxHp цели. */
    public static double dpsLimit(double maxHpFormula, double capPct) {
        if (!Double.isFinite(maxHpFormula) || maxHpFormula <= 0.0) {
            return 0.0;
        }
        double pct = Double.isFinite(capPct) && capPct >= 0.0 ? capPct : 6.0;
        return maxHpFormula * pct / 100.0;
    }
}
