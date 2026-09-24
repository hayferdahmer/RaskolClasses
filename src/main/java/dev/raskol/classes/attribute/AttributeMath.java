// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

/**
 * 1.9.3: ПОЛНАЯ формула HP возвращена и сделана конфигурируемой:
 *   HP = baseHp + STR×perStr + level×perLevel + (strMain ? level×mainBonus : 0)
 * Все слагаемые читаются из конфига (attributes.hp.*), мёртвых ключей больше нет.
 * Legacy 6-arg maxHp сохранён для совместимости, но делегирует в полную формулу
 * (baseHp=100) — больше никакого молчаливого хардкода в боевых вызовах.
 * Чистые статические функции без Bukkit: единый источник правды для боя, HUD, PAPI, selftest.
 */
public final class AttributeMath {

    private AttributeMath() {
    }

    private static double fin(double v, double def) {
        return Double.isFinite(v) ? v : def;
    }

    /* ------------------------------- здоровье ------------------------------- */

    /**
     * Каноническая ПОЛНАЯ формула HP (1.9.3).
     * @param str        значение СИЛЫ
     * @param level      уровень для формул (сводный/профильный/ванильный)
     * @param strMain    true если главный атрибут класса — STR (танк-бонус)
     * @param baseHp     базовое HP
     * @param perStr     HP за единицу STR
     * @param perLevel   HP за уровень
     * @param mainBonus  доп. HP за уровень для STR-main классов
     */
    public static double maxHp(double str, double level, boolean strMain,
                               double baseHp, double perStr, double perLevel, double mainBonus) {
        double sStr = Math.max(0.0, fin(str, 0.0));
        double sLvl = Math.max(0.0, fin(level, 0.0));
        double sBase = Math.max(0.0, fin(baseHp, 100.0));
        double sPerStr = Math.max(0.0, fin(perStr, 0.0));
        double sPerLvl = Math.max(0.0, fin(perLevel, 0.0));
        double sMain = Math.max(0.0, fin(mainBonus, 0.0));
        double hp = sBase + sStr * sPerStr + sLvl * sPerLvl + (strMain ? sLvl * sMain : 0.0);
        return Math.max(1.0, hp);
    }

    /** 3-arg совместимость: HP только от STR (level=0, без main-бонуса). */
    public static double maxHp(double str, double baseHp, double perStr) {
        return maxHp(str, 0.0, false, baseHp, perStr, 0.0, 0.0);
    }

    /** Legacy 6-arg совместимость: делегирует в полную формулу с baseHp=100. */
    @SuppressWarnings("unused")
    public static double maxHp(double str, double level, boolean strMain,
                               double perStr, double perLevel, double mainBonus) {
        return maxHp(str, level, strMain, 100.0, perStr, perLevel, mainBonus);
    }

    /**
     * Реген HP от СИЛЫ, HP/сек (1.7.0.3, без изменений).
     */
    public static double strRegenPerSecond(double str, double perStr, double maxHp,
                                           boolean inCombat, double combatFactor, double capPct) {
        double rate = Math.max(0.0, fin(str, 0.0)) * Math.max(0.0, fin(perStr, 0.0));
        if (!Double.isFinite(rate)) {
            return 0.0;
        }
        if (inCombat) {
            rate *= Math.max(0.0, fin(combatFactor, 0.0));
        }
        double cap = Math.max(0.0, fin(maxHp, 0.0)) * Math.max(0.0, fin(capPct, 0.0)) / 100.0;
        if (!Double.isFinite(cap)) {
            return 0.0;
        }
        return Math.min(rate, cap);
    }

    /* -------------------------------- урон ----------------------------------- */

    public static double physicalBonus(double str, double level) {
        return Math.max(0.0, fin(str, 0.0) + fin(level, 0.0));
    }

    public static double spellBonus(double intel, double level) {
        return Math.max(0.0, fin(intel, 0.0) + fin(level, 0.0));
    }

    /* -------------------------------- крит ----------------------------------- */

    public static double critMelee(double agi, double base, double perAgi, double cap) {
        return clampPct(base + Math.max(0.0, fin(agi, 0.0)) * fin(perAgi, 0.0), cap);
    }

    public static double critSpell(double intel, boolean intMain,
                                   double base, double perInt, double mainMult, double cap) {
        double v = base + Math.max(0.0, fin(intel, 0.0)) * fin(perInt, 0.0);
        if (intMain) {
            v *= fin(mainMult, 1.0);
        }
        return clampPct(v, cap);
    }

    /* ------------------------------ защита ----------------------------------- */

    public static double dodgeRaw(double agi, double k) {
        if (agi <= 0.0 || k <= 0.0) {
            return 0.0;
        }
        return 100.0 * agi / (agi + k);
    }

    public static double parryRaw(double str, double k) {
        if (str <= 0.0 || k <= 0.0) {
            return 0.0;
        }
        return 100.0 * str / (str + k);
    }

    public static double applyDR(double total, double softCap, double drFactor, double hardCap) {
        if (total <= 0.0) {
            return 0.0;
        }
        double eff = total <= softCap ? total : softCap + (total - softCap) * drFactor;
        return Math.min(eff, hardCap);
    }

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
        return Math.max(0.0, Math.min(fin(cap, 100.0), v));
    }
}
