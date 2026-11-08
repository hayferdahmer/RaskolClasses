// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.api.combat.CombatProfile;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.school.School;

@DisplayName("DotCombatProfileMath: защита внешних мобов")
class DotCombatProfileMathTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("Отсутствующий профиль не меняет урон")
    void missingProfileDoesNotChangeDamage() {
        assertEquals(1.0, DotCombatProfileMath.multiplier(null, School.FIRE, 0.0, 0.0), EPS);
    }

    @Test
    @DisplayName("Иммунитет профиля полностью блокирует соответствующую школу")
    void immunityBlocksSchool() {
        CombatProfile profile = profile(
                0.0,
                0.0,
                Map.of(),
                Set.of(School.FIRE),
                Map.of());

        assertEquals(0.0, DotCombatProfileMath.multiplier(profile, School.FIRE, 0.0, 0.0), EPS);
    }

    @Test
    @DisplayName("Физическое сопротивление учитывает сопротивление школы и оба вида пробития")
    void physicalResistanceUsesChannelAndSchoolPenetration() {
        CombatProfile profile = profile(
                50.0,
                0.0,
                Map.of(School.PHYSICAL, 10.0),
                Set.of(),
                Map.of());

        // 50 физ. резиста + 10 резиста школы - 20 канального пробития
        // - 5 пробития школы = 35% итогового сопротивления.
        assertEquals(
                0.65,
                DotCombatProfileMath.multiplier(profile, School.PHYSICAL, 20.0, 5.0),
                EPS);
    }

    @Test
    @DisplayName("Магическое сопротивление и уязвимость применяются в правильном порядке")
    void magicResistanceAndVulnerabilityAreCombined() {
        CombatProfile profile = profile(
                0.0,
                40.0,
                Map.of(School.FIRE, 10.0),
                Set.of(),
                Map.of(School.FIRE, 50.0));

        // (40 + 10)% сопротивления → 0.5 урона;
        // затем уязвимость +50% → итоговый множитель 0.75.
        assertEquals(
                0.75,
                DotCombatProfileMath.multiplier(profile, School.FIRE, 0.0, 0.0),
                EPS);
    }

    @Test
    @DisplayName("TRUE-школа игнорирует сопротивления, но учитывает уязвимость")
    void trueSchoolIgnoresResistanceButUsesVulnerability() {
        CombatProfile profile = profile(
                60.0,
                60.0,
                Map.of(School.TRUE, 60.0),
                Set.of(),
                Map.of(School.TRUE, 50.0));

        assertEquals(
                1.5,
                DotCombatProfileMath.multiplier(profile, School.TRUE, 40.0, 40.0),
                EPS);
    }

    @Test
    @DisplayName("Уязвимость минус 100 процентов полностью обнуляет урон")
    void fullResistanceVulnerabilityBlocksDamage() {
        CombatProfile profile = profile(
                0.0,
                0.0,
                Map.of(),
                Set.of(),
                Map.of(School.FROST, -100.0));

        assertEquals(0.0, DotCombatProfileMath.multiplier(profile, School.FROST, 0.0, 0.0), EPS);
    }

    @Test
    @DisplayName("Итоговое сопротивление ограничивается сверху 90 процентами")
    void resistanceIsCappedAtNinetyPercent() {
        CombatProfile profile = profile(
                60.0,
                0.0,
                Map.of(School.PHYSICAL, 60.0),
                Set.of(),
                Map.of());

        assertEquals(0.1, DotCombatProfileMath.multiplier(profile, School.PHYSICAL, 0.0, 0.0), EPS);
    }

    @Test
    @DisplayName("Некорректное пробитие не создаёт дополнительный урон")
    void invalidPenetrationIsIgnored() {
        CombatProfile profile = profile(
                30.0,
                0.0,
                Map.of(),
                Set.of(),
                Map.of());

        assertEquals(
                0.7,
                DotCombatProfileMath.multiplier(
                        profile, School.PHYSICAL, Double.NaN, Double.POSITIVE_INFINITY),
                EPS);
    }

    private static CombatProfile profile(
            double physResist,
            double magicResist,
            Map<School, Double> schoolResists,
            Set<School> immunities,
            Map<School, Double> vulnerabilities) {
        return new CombatProfile(
                10,
                10.0,
                DamageProfile.physical(10.0),
                0.0,
                0.0,
                Map.of(),
                physResist,
                magicResist,
                schoolResists,
                immunities,
                vulnerabilities,
                0.0,
                1.5,
                0.0,
                Set.of(),
                0.0,
                Map.of());
    }
}
