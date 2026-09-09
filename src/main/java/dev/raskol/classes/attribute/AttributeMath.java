// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

/**
 * 1.7.0: ВСЯ боевая математика атрибутов — чистые статические функции без Bukkit.
 * Никакого состояния, никаких_side-эффектов: это позволяет гонять формулы
 * в /rc selftest headless-проверками и переиспользовать их в бою, HUD и PAPI
 * без расхождений (единый источник правды).
 *
 * Соглашения: шансы и проценты — в процентах (0..100), углы — в градусах.
 */
public final class AttributeMath {

    private AttributeMath() {
    }

    /* ------------------------------- здоровье ------------------------------- */

    /**
     * Максимум HP: 20 + STR×perStr + level×perLevel; если STR основной —
     * сверху +STR×mainBonus (итог воина: 20 + STR×3 + level при perStr=2, mainBonus=1).
     */
    public static double maxHp(double str, double level, boolean strMain,
                               double perStr, double perLevel, double mainBonus) {
        double hp = 20.0 + str * perStr + level * perLevel;
        if (strMain) {
            hp += str * mainBonus;
        }
        return Math.max(1.0, hp);
    }

    /* -------------------------------- урон ----------------------------------- */

    /** Плоская добавка к физ-компоненте исходящего урона: STR + level. */
    public static double physicalBonus(double str, double level) {
        return Math.max(0.0, str + level);
    }

    /** Плоская добавка к маг-компоненте исходящего урона: INT + level. */
    public static double spellBonus(double intel, double level) {
        return Math.max(0.0, intel + level);
    }

    /* -------------------------------- крит ----------------------------------- */

    /** Крит мили: base + AGI×perAgi, кламп в [0, cap]. */
    public static double critMelee(double agi, double base, double perAgi, double cap) {
        return clampPct(base + Math.max(0.0, agi) * perAgi, cap);
    }

    /** Крит магии: (base + INT×perInt) × (intMain ? mainMult : 1), кламп в [0, cap]. */
    public static double critSpell(double intel, boolean intMain,
                                   double base, double perInt, double mainMult, double cap) {
        double v = base + Math.max(0.0, intel) * perInt;
        if (intMain) {
            v *= mainMult;
        }
        return clampPct(v, cap);
    }

    /* ------------------------------ защита ----------------------------------- */

    /** Уклонение (raw, %): гипербола 100×AGI/(AGI+k) — убывающая отдача из коробки. */
    public static double dodgeRaw(double agi, double k) {
        if (agi <= 0.0 || k <= 0.0) {
            return 0.0;
        }
        return 100.0 * agi / (agi + k);
    }

    /** Парирование (raw, %): гипербола 100×STR/(STR+k), только фронт + мили/щит. */
    public static double parryRaw(double str, double k) {
        if (str <= 0.0 || k <= 0.0) {
            return 0.0;
        }
        return 100.0 * str / (str + k);
    }

    /**
     * Закон убывающей отдачи (DR): до softCap эффективность полная,
     * свыше — каждый процент за drFactor; жёсткий предел hardCap.
     * Пример: softCap 60, dr 0.5, raw 80 → eff 70.
     */
    public static double applyDR(double total, double softCap, double drFactor, double hardCap) {
        if (total <= 0.0) {
            return 0.0;
        }
        double eff = total <= softCap
                ? total
                : softCap + (total - softCap) * drFactor;
        return Math.min(eff, hardCap);
    }

    /**
     * Пропорциональное распределение эффективного шанса между уклоном и парированием
     * (возврат [dodgeEff, parryEff]): условия парирования (фронт/оружие) не ломают математику.
     */
    public static double[] splitEff(double dodgeRaw, double parryRaw, double effTotal) {
        double raw = dodgeRaw + parryRaw;
        if (raw <= 0.0 || effTotal <= 0.0) {
            return new double[]{0.0, 0.0};
        }
        double f = Math.min(1.0, effTotal / raw);
        return new double[]{dodgeRaw * f, parryRaw * f};
    }

    /* ------------------------------- углы ------------------------------------ */

    /**
     * Угол (градусы, 0..180) между направлением взгляда защищающегося
     * и вектором на атакующего (горизонтальная плоскость). 0 = точно спереди.
     */
    public static double angleToAttacker(double defDirX, double defDirZ,
                                         double toAttX, double toAttZ) {
        double lenA = Math.hypot(defDirX, defDirZ);
        double lenB = Math.hypot(toAttX, toAttZ);
        if (lenA < 1e-9 || lenB < 1e-9) {
            return 0.0;
        }
        double cos = (defDirX * toAttX + defDirZ * toAttZ) / (lenA * lenB);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    /** Фронт: угол ≤ frontAngle (дефолт 90°). */
    public static boolean isFront(double angleDeg, double frontAngle) {
        return angleDeg <= frontAngle;
    }

    /** Спина: угол ≥ backAngle (дефолт 135°) — микро-парирование здесь не работает. */
    public static boolean isBack(double angleDeg, double backAngle) {
        return angleDeg >= backAngle;
    }

    /* ------------------------------- прочее ---------------------------------- */

    /** Цвет HP-бара по доле: >0.6 зелёный, >0.3 жёлтый, иначе красный (код цвета для Adventure). */
    public static String hpFractionColor(double hp, double maxHp) {
        if (maxHp <= 0.0) {
            return "#D64545";
        }
        double f = hp / maxHp;
        if (f > 0.6) {
            return "#3FA33F";
        }
        if (f > 0.3) {
            return "#D9A521";
        }
        return "#D64545";
    }

    private static double clampPct(double v, double cap) {
        return Math.max(0.0, Math.min(cap, v));
    }
}
