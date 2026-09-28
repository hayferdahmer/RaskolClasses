// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

/**
 * 1.11.4 (P4a): PURE-математика кита Чернокнижника без Bukkit-зависимостей.
 * Все формулы заперты selftest-чеками 41–43:
 *   recoil   — откат 6.66% от дошедшего, кап 30% maxHP, пол minHp;
 *   drain    — lifesteal способностей с капом lifesteal-cap;
 *   mult     — Скверна ≥75 (×1.2) × ад (×nether-mult) × спек (Чёрный Маг);
 *   ignore   — игнор маг-резиста при HP ≤ порога (любая сторона);
 *   missing  — бонус взрыва «Раскола Души» от недостающего HP;
 *   selfcost — плата «Чёрного Слова» 10% maxHP.
 * Единицы: formula-HP там, где сказано; recoil применяется к carrier через scale
 * на стороне вызывающего (WarlockAbilities / CombatService в P4c).
 */
public final class WarlockMath {

    private WarlockMath() {
    }

    /** Величина отката: taken × recoilPct%, но не больше formulaMaxHp × capPct%. */
    public static double recoilAmount(double taken, double recoilPct,
                                      double formulaMaxHp, double capPct) {
        if (!Double.isFinite(taken) || taken <= 0.0) {
            return 0.0;
        }
        double raw = taken * safe(recoilPct, 6.66) / 100.0;
        double cap = safe(formulaMaxHp, 0.0) * safe(capPct, 30.0) / 100.0;
        return Math.min(raw, Math.max(0.0, cap));
    }

    /** Новое значение HP после отката: не ниже minHp. */
    public static double applyRecoil(double currentHp, double recoil, double minHp) {
        double next = safe(currentHp, 0.0) - Math.max(0.0, recoil);
        return Math.max(safe(minHp, 1.0), next);
    }

    /** Lifesteal: dealt × drainPct, но не больше dealt × capPct (lifesteal-cap). */
    public static double drainHeal(double dealt, double drainPct, double capPct) {
        if (!Double.isFinite(dealt) || dealt <= 0.0) {
            return 0.0;
        }
        double drain = Math.max(0.0, safe(drainPct, 0.0));
        double cap = Math.max(0.0, safe(capPct, 1.0));
        return dealt * Math.min(drain, cap);
    }

    /**
     * Множитель урона чернокнижника:
     * (Скверна ≥ thresholdOpen ? 1.2 : 1) × (в аду ? netherMult : 1) × specMult.
     */
    public static double damageMult(double corruption, double thresholdOpen,
                                    double netherMult, double specMult, boolean inNether) {
        double mult = 1.0;
        if (safe(corruption, 0.0) >= safe(thresholdOpen, 75.0)) {
            mult *= 1.2;
        }
        if (inNether) {
            mult *= safe(netherMult, 6.0);
        }
        return mult * safe(specMult, 1.0);
    }

    /** Игнор маг-резиста: HP цели ИЛИ кастера ≤ порога (доли от max, 0..1). */
    public static boolean ignoreMagicResist(double targetFrac, double casterFrac, double threshold) {
        double thr = safe(threshold, 0.25);
        return safe(targetFrac, 1.0) <= thr || safe(casterFrac, 1.0) <= thr;
    }

    /** Бонус взрыва «Раскола Души»: недостающее HP цели × missingBonus. */
    public static double missingHpBonus(double currentHp, double maxHp, double missingBonus) {
        double missing = Math.max(0.0, safe(maxHp, 0.0) - safe(currentHp, 0.0));
        return missing * Math.max(0.0, safe(missingBonus, 0.0));
    }

    /** Амплификация «Печати Погибели»: base × (1 + amp). */
    public static double sealAmplify(double base, double amp) {
        return safe(base, 0.0) * (1.0 + Math.max(0.0, safe(amp, 0.0)));
    }

    /** Новое formula-HP после платы «Чёрного Слова»: pct% от maxHP, не ниже minHp. */
    public static double selfCostNewHp(double maxFormulaHp, double pct,
                                       double currentFormulaHp, double minHp) {
        double cost = safe(maxFormulaHp, 0.0) * Math.max(0.0, safe(pct, 10.0)) / 100.0;
        double next = safe(currentFormulaHp, 0.0) - cost;
        return Math.max(safe(minHp, 1.0), next);
    }

    private static double safe(double v, double def) {
        return Double.isFinite(v) ? v : def;
    }
}
