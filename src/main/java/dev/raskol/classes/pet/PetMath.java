// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.pet;

/**
 * 1.14.6: pure-математика петов (headless, selftest 109/111).
 * Формулы дизайна A2 + процентные узлы spec2 (pet_hp_pct / pet_dmg_pct):
 *   hp  = (baseHp  + attr  × 2.0) × (1 + petHpPct  / 100)
 *   dmg = (baseDmg + power × 0.3) × (1 + petDmgPct / 100) × dmgMult
 * Все входы защищёны от NaN/отрицательных: мусор → базовое значение.
 */
public final class PetMath {

    private PetMath() {
    }

    private static double safe(double v) {
        return Double.isFinite(v) && v >= 0.0 ? v : 0.0;
    }

    private static double pctFactor(double pct) {
        return Double.isFinite(pct) && pct > 0.0 ? 1.0 + pct / 100.0 : 1.0;
    }

    /** HP пета: база + атрибут×2, умноженное на узлы pet_hp_pct. */
    public static double hp(double baseHp, double attr, double petHpPct) {
        return (safe(baseHp) + safe(attr) * 2.0) * pctFactor(petHpPct);
    }

    /** Урон пета: база + сила×0.3, умноженное на узлы pet_dmg_pct и бафф-множитель. */
    public static double damage(double baseDmg, double power, double petDmgPct, double dmgMult) {
        double mult = Double.isFinite(dmgMult) && dmgMult > 0.0 ? dmgMult : 1.0;
        return (safe(baseDmg) + safe(power) * 0.3) * pctFactor(petDmgPct) * mult;
    }

    /** ttl-пет истёк? expiresAt==0 → постоянный пет, никогда не истекает. */
    public static boolean expired(long expiresAt, long nowMillis) {
        return expiresAt > 0L && nowMillis > expiresAt;
    }

    /** Дистанция слежения превышена (квадраты блоков, без sqrt). */
    public static boolean needsTeleport(double distanceSquared, double followBlocks) {
        return distanceSquared > followBlocks * followBlocks;
    }
}
