// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

/**
 * 1.7.0: ВСЯ боевая математика атрибутов — чистые статические функции без Bukkit.
 * Никакого состояния, никаких side-эффектов: это позволяет гонять формулы
 * в /rc selftest headless-проверками и переиспользовать их в бою, HUD и PAPI
 * без расхождений (единый источник правды).
 *
 * 1.7.0.5: HP-ФОРМУЛА ПЕРЕСЧИТАНА:
 *   HP = baseHp + STR × perStr
 *   (дефолты: baseHp=100, perStr=20 → воин STR 60 = 1300 HP, маг STR 16 = 420 HP).
 *   Параметры level/strMain/perLevel/mainBonus сохранены в сигнатуре для
 *   совместимости вызовов, но не используются (legacy).
 *
 * 1.7.0.3: strRegenPerSecond — Dota-подобный реген HP от СИЛЫ.
 */
public final class AttributeMath {

    private AttributeMath() {
    }

    /* ------------------------------- здоровье ------------------------------- */

    /**
     * Максимум HP: baseHp + STR × perStr.
     * Параметры level/strMain/perLevel/mainBonus — legacy, не используются
     * (сохранены для совместимости вызовов AttributeService).
     * 1.7.0.5: baseHp=100, perStr=20 (было: 20 + STR×perStr + level×perLevel + mainBonus).
     */
    @SuppressWarnings("unused")
    public static double maxHp(double str, double level, boolean strMain,
                               double perStr, double perLevel, double mainBonus) {
        return maxHp(str, 100.0, perStr);
    }

    /**
     * Каноническая формула HP (1.7.0.5): HP = baseHp + STR × perStr.
     * Кламп снизу на 1.0 (живой игрок всегда имеет хотя бы 1 HP в модели).
     * Все входы защищены от NaN/отрицательных.
     */
    public static double maxHp(double str, double baseHp, double perStr) {
        double safeStr = Double.isFinite(str) && str >= 0 ? str : 0.0;
        double safePerStr = Double.isFinite(perStr) && perStr >= 0 ? perStr : 0.0;
        double safeBase = Double.isFinite(baseHp) && baseHp >= 0 ? baseHp : 100.0;
        double hp = safeBase + safeStr * safePerStr;
        return Math.max(1.0, hp);
    }

    /**
     * 1.7.0.3: реген HP от СИЛЫ, HP/сек.
     *   rate = STR × perStr;
     *   в бою rate ×= combatFactor (толпа всё равно убивает);
     *   кап: rate ≤ maxHp × capPct / 100 (реген не скейлится в абсурд с пулом).
     * Все входы защищены от NaN/отрицательных.
     */
    public static double strRegenPerSecond(double str, double perStr, double maxHp,
                                           boolean inCombat, double combatFactor, double capPct) {
        double rate = Math.max(0.0, str) * Math.max(0.0, perStr);
        if (!Double.isFinite(rate)) {
            return 0.0;
        }
        if (inCombat) {
            rate *= Math.max(0.0, combatFactor);
        }
        double cap = Math.max(0.0, maxHp) * Math.max(0.0, capPct) / 100.0;
        if (!Double.isFinite(cap)) {
            return 0.0;
        }
        return Math.min(rate, cap);
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
     * Пропорциональное распределение эффективного шанса между уклоном и парированием.
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

    public static boolean isFront(double angleDeg, double frontAngle) {
        return angleDeg <= frontAngle;
    }

    public static boolean isBack(double angleDeg, double backAngle) {
        return angleDeg >= backAngle;
    }

    /* ------------------------------- прочее ---------------------------------- */

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
