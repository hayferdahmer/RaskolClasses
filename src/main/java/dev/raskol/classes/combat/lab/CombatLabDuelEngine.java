// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.util.Random;

import dev.raskol.classes.combat.CombatMath;

/**
 * Детерминированный движок дуэлей Combat Lab.
 * Профиль без экипировки остаётся контрольным; экипированные снимки используют
 * runtime-формулы VanillaDamageListener, PowerService и WeaponMastery.
 */
public final class CombatLabDuelEngine {

    public static final String PROFILE_ID = "pvp-unarmed-melee-probe-v1";
    public static final String EQUIPMENT_PROFILE_ID = "pvp-equipment-auto-attack-v1";
    private static final double DEFAULT_ATTACK_INTERVAL_SECONDS = 0.8;
    private static final double DEFAULT_MAX_DURATION_SECONDS = 60.0;

    public record Rules(
            double unarmedBaseDamage,
            double weaponPowerCoefficient,
            double magicPowerCoefficient,
            boolean strengthToPhysical,
            boolean intellectToMagic,
            double attackIntervalSeconds,
            double singleHitCapPct,
            double resistCapPct,
            double maxDurationSeconds) {

        public Rules {
            if (!finiteNonNegative(unarmedBaseDamage)
                    || !finiteNonNegative(weaponPowerCoefficient)
                    || !finiteNonNegative(magicPowerCoefficient)
                    || !Double.isFinite(attackIntervalSeconds)
                    || attackIntervalSeconds <= 0.0
                    || !finiteNonNegative(singleHitCapPct)
                    || !Double.isFinite(resistCapPct)
                    || resistCapPct < 0.0
                    || resistCapPct > 100.0
                    || !Double.isFinite(maxDurationSeconds)
                    || maxDurationSeconds <= 0.0) {
                throw new IllegalArgumentException("Некорректные правила Combat Lab");
            }
        }

        public static Rules from(org.bukkit.configuration.file.YamlConfiguration config) {
            return new Rules(
                    1.0,
                    config.getDouble("attributes.offense.basic-coeff", 0.35),
                    config.getDouble("attributes.offense.basic-coeff-magic", 0.35),
                    config.getBoolean("attributes.offense.str-to-physical", false),
                    config.getBoolean("attributes.offense.int-to-magic", false),
                    DEFAULT_ATTACK_INTERVAL_SECONDS,
                    config.getDouble("combat.max-single-hit-pct", 35.0),
                    config.getDouble("resist.pvp-cap", 90.0),
                    DEFAULT_MAX_DURATION_SECONDS);
        }
    }

    private record HitResult(double damage, boolean critical, boolean avoided) {}

    public record DuelResult(
            double durationSeconds,
            boolean timeout,
            boolean draw,
            boolean firstPlayerWon,
            double firstDamageDealt,
            double secondDamageDealt,
            double firstHealing,
            double secondHealing,
            int firstHits,
            int secondHits,
            int firstCrits,
            int secondCrits,
            int firstAvoids,
            int secondAvoids) {}

    private CombatLabDuelEngine() {}

    public static DuelResult duel(
            CombatLabCombatantSnapshot first, CombatLabCombatantSnapshot second, Rules rules, long seed) {
        return duelInternal(first, second, rules, seed, false);
    }

    /** Дуэль с включёнными экипировкой, типом автоатаки и WeaponMastery. */
    public static DuelResult duelEquipped(
            CombatLabCombatantSnapshot first, CombatLabCombatantSnapshot second, Rules rules, long seed) {
        return duelInternal(first, second, rules, seed, true);
    }

    private static DuelResult duelInternal(
            CombatLabCombatantSnapshot first,
            CombatLabCombatantSnapshot second,
            Rules rules,
            long seed,
            boolean equipmentAware) {
        if (first == null || second == null || rules == null) {
            throw new IllegalArgumentException("Участники и правила обязательны");
        }
        Random random = new Random(seed);
        double firstHealth = first.maxHealth();
        double secondHealth = second.maxHealth();
        double firstDamage = 0.0;
        double secondDamage = 0.0;
        double firstHealing = 0.0;
        double secondHealing = 0.0;
        int firstHits = 0;
        int secondHits = 0;
        int firstCrits = 0;
        int secondCrits = 0;
        int firstAvoids = 0;
        int secondAvoids = 0;
        double firstInterval = attackInterval(first, rules, equipmentAware);
        double secondInterval = attackInterval(second, rules, equipmentAware);
        double nextFirstAttack = firstInterval;
        double nextSecondAttack = secondInterval;
        double elapsed = 0.0;

        while (elapsed < rules.maxDurationSeconds()) {
            double nextEvent = Math.min(nextFirstAttack, nextSecondAttack);
            if (nextEvent > rules.maxDurationSeconds()) break;
            double delta = nextEvent - elapsed;
            double firstRegen = Math.min(
                    first.maxHealth() - firstHealth, first.healthRegenPerSecond() * delta);
            double secondRegen = Math.min(
                    second.maxHealth() - secondHealth, second.healthRegenPerSecond() * delta);
            firstHealth += firstRegen;
            secondHealth += secondRegen;
            firstHealing += firstRegen;
            secondHealing += secondRegen;
            elapsed = nextEvent;

            boolean firstAttacks = Math.abs(nextFirstAttack - nextEvent) < 1.0e-9;
            boolean secondAttacks = Math.abs(nextSecondAttack - nextEvent) < 1.0e-9;
            HitResult firstHit = firstAttacks
                    ? resolveHit(first, second, rules, random, equipmentAware)
                    : new HitResult(0.0, false, false);
            HitResult secondHit = secondAttacks
                    ? resolveHit(second, first, rules, random, equipmentAware)
                    : new HitResult(0.0, false, false);

            if (firstAttacks) {
                firstHits++;
                firstDamage += firstHit.damage();
                secondHealth -= firstHit.damage();
                if (firstHit.critical()) firstCrits++;
                if (firstHit.avoided()) secondAvoids++;
                nextFirstAttack += firstInterval;
            }
            if (secondAttacks) {
                secondHits++;
                secondDamage += secondHit.damage();
                firstHealth -= secondHit.damage();
                if (secondHit.critical()) secondCrits++;
                if (secondHit.avoided()) firstAvoids++;
                nextSecondAttack += secondInterval;
            }

            if (firstHealth <= 0.0 || secondHealth <= 0.0) {
                boolean draw = firstHealth <= 0.0 && secondHealth <= 0.0;
                return result(
                        elapsed, false, draw, !draw && secondHealth <= 0.0,
                        firstDamage, secondDamage, firstHealing, secondHealing,
                        firstHits, secondHits, firstCrits, secondCrits, firstAvoids, secondAvoids);
            }
        }

        return result(
                rules.maxDurationSeconds(),
                true,
                Math.abs(firstHealth - secondHealth) < 1e-9,
                firstHealth > secondHealth,
                firstDamage,
                secondDamage,
                firstHealing,
                secondHealing,
                firstHits,
                secondHits,
                firstCrits,
                secondCrits,
                firstAvoids,
                secondAvoids);
    }

    private static DuelResult result(
            double duration,
            boolean timeout,
            boolean draw,
            boolean firstWon,
            double firstDamage,
            double secondDamage,
            double firstHealing,
            double secondHealing,
            int firstHits,
            int secondHits,
            int firstCrits,
            int secondCrits,
            int firstAvoids,
            int secondAvoids) {
        return new DuelResult(
                duration, timeout, draw, firstWon, firstDamage, secondDamage,
                firstHealing, secondHealing, firstHits, secondHits,
                firstCrits, secondCrits, firstAvoids, secondAvoids);
    }

    private static double attackInterval(
            CombatLabCombatantSnapshot combatant, Rules rules, boolean equipmentAware) {
        if (!equipmentAware || combatant.attackSpeedPct() <= 0.0) {
            return rules.attackIntervalSeconds();
        }
        return rules.attackIntervalSeconds() / (1.0 + combatant.attackSpeedPct() / 100.0);
    }

    private static HitResult resolveHit(
            CombatLabCombatantSnapshot attacker,
            CombatLabCombatantSnapshot defender,
            Rules rules,
            Random random,
            boolean equipmentAware) {
        boolean magic = equipmentAware && "MAGIC".equals(attacker.autoAttackType());
        if (!magic) {
            double avoidChance = Math.max(0.0, defender.dodgeChancePct() + defender.parryChancePct());
            if (random.nextDouble() * 100.0 < avoidChance) {
                return new HitResult(0.0, false, true);
            }
        }

        double damage = equipmentAware && attacker.attackDamage() > 0.0
                ? attacker.attackDamage()
                : rules.unarmedBaseDamage();
        if (magic) {
            damage += attacker.spellPower() * rules.magicPowerCoefficient();
            if (rules.intellectToMagic()) damage += attacker.intellect() + attacker.level();
        } else {
            damage += attacker.weaponPower() * rules.weaponPowerCoefficient();
            if (rules.strengthToPhysical()) damage += attacker.strength() + attacker.level();
        }

        double critChance = magic ? attacker.spellCritChancePct() : attacker.meleeCritChancePct();
        boolean critical = random.nextDouble() * 100.0 < critChance;
        if (critical) {
            damage *= magic ? attacker.spellCritMultiplier() : attacker.meleeCritMultiplier();
        }
        if (equipmentAware && !magic) {
            damage *= attacker.weaponMasteryMultiplier();
        }

        double resistance = magic ? defender.magicResistPct() : defender.physicalResistPct();
        double mitigation = CombatMath.mitigation(resistance, rules.resistCapPct());
        damage *= 1.0 - mitigation;
        return new HitResult(
                CombatMath.cappedDamage(damage, defender.maxHealth(), rules.singleHitCapPct()), critical, false);
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
