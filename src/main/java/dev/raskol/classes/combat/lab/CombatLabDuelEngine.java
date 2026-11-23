// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.lab;

import java.util.Random;

import dev.raskol.classes.combat.CombatMath;

/**
 * Первый детерминированный headless-профиль: стандартизированный бой без экипировки.
 *
 * Это калибровочный probe для проверки общих формул характеристик, крита,
 * уклонения, резиста и анти-ваншот-капа. Он намеренно НЕ моделирует способности,
 * таланты, предметы, проки, питомцев, CC, школы и Paper events.
 */
public final class CombatLabDuelEngine {

    public static final String PROFILE_ID = "pvp-unarmed-melee-probe-v1";
    private static final double DEFAULT_ATTACK_INTERVAL_SECONDS = 0.8;
    private static final double DEFAULT_MAX_DURATION_SECONDS = 60.0;

    public record Rules(
            double unarmedBaseDamage,
            double weaponPowerCoefficient,
            double attackIntervalSeconds,
            double singleHitCapPct,
            double resistCapPct,
            double maxDurationSeconds) {

        public Rules {
            if (!finiteNonNegative(unarmedBaseDamage)
                    || !finiteNonNegative(weaponPowerCoefficient)
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
            CombatLabCombatantSnapshot first,
            CombatLabCombatantSnapshot second,
            Rules rules,
            long seed) {
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
        double elapsed = 0.0;

        while (elapsed < rules.maxDurationSeconds()) {
            HitResult firstHit = resolvePhysicalHit(first, second, rules, random);
            HitResult secondHit = resolvePhysicalHit(second, first, rules, random);
            double dealtToSecond = firstHit.damage();
            double dealtToFirst = secondHit.damage();
            if (firstHit.critical()) firstCrits++;
            if (secondHit.critical()) secondCrits++;
            if (firstHit.avoided()) secondAvoids++;
            if (secondHit.avoided()) firstAvoids++;
            firstHits++;
            secondHits++;
            firstDamage += dealtToSecond;
            secondDamage += dealtToFirst;

            // Реген в бою использует ту же pure-формулу AttributeMath, что и сервер.
            double firstRegen = Math.min(
                    first.maxHealth() - firstHealth,
                    first.healthRegenPerSecond() * rules.attackIntervalSeconds());
            double secondRegen = Math.min(
                    second.maxHealth() - secondHealth,
                    second.healthRegenPerSecond() * rules.attackIntervalSeconds());
            firstHealth += firstRegen;
            secondHealth += secondRegen;
            firstHealing += firstRegen;
            secondHealing += secondRegen;
            firstHealth -= dealtToFirst;
            secondHealth -= dealtToSecond;

            elapsed += rules.attackIntervalSeconds();
            if (firstHealth <= 0.0 || secondHealth <= 0.0) {
                boolean draw = firstHealth <= 0.0 && secondHealth <= 0.0;
                return new DuelResult(
                        elapsed,
                        false,
                        draw,
                        !draw && secondHealth <= 0.0,
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
        }
        return new DuelResult(
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

    private static HitResult resolvePhysicalHit(
            CombatLabCombatantSnapshot attacker,
            CombatLabCombatantSnapshot defender,
            Rules rules,
            Random random) {
        double avoidChance = Math.max(0.0, defender.dodgeChancePct() + defender.parryChancePct());
        if (random.nextDouble() * 100.0 < avoidChance) {
            return new HitResult(0.0, false, true);
        }
        double damage = rules.unarmedBaseDamage() + attacker.weaponPower() * rules.weaponPowerCoefficient();
        boolean critical = random.nextDouble() * 100.0 < attacker.meleeCritChancePct();
        if (critical) {
            damage *= attacker.meleeCritMultiplier();
        }
        double mitigation = CombatMath.mitigation(defender.physicalResistPct(), rules.resistCapPct());
        damage *= 1.0 - mitigation;
        return new HitResult(
                CombatMath.cappedDamage(damage, defender.maxHealth(), rules.singleHitCapPct()),
                critical,
                false);
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
