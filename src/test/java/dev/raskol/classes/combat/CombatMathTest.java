// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.10 (Sprint 7, волна 7.1b, P0-11-01): полное покрытие CombatMath.
 * Якорные значения из selftest-чеков 16/62.
 */
@DisplayName("CombatMath: формулы боя")
class CombatMathTest {

    private static final double EPS = 1e-9;

    @Nested
    @DisplayName("cappedDamage: анти-ваншот (чек 16)")
    class CappedDamage {

        @Test
        @DisplayName("урон выше капа → обрезается до limit = maxHp × pct/100")
        void trimsOverLimit() {
            // чек 16a: 9999 против maxHp=1300 и cap=35% → limit=455
            assertEquals(455.0, CombatMath.cappedDamage(9999.0, 1300.0, 35.0), EPS);
        }

        @Test
        @DisplayName("урон ниже капа → не трогается")
        void passesUnderLimit() {
            // чек 16b: 100 против 1300/35% → 100
            assertEquals(100.0, CombatMath.cappedDamage(100.0, 1300.0, 35.0), EPS);
        }

        @Test
        @DisplayName("pct=0 → кап выключен, урон не трогается")
        void pctZeroDisablesCap() {
            // чек 16c: 500 против 1000 с pct=0 → 500
            assertEquals(500.0, CombatMath.cappedDamage(500.0, 1000.0, 0.0), EPS);
        }

        @Test
        @DisplayName("pct отрицательный → кап выключен")
        void negativePctDisablesCap() {
            assertEquals(500.0, CombatMath.cappedDamage(500.0, 1000.0, -5.0), EPS);
        }

        @Test
        @DisplayName("NaN/<=0 damage → 0.0 (некорректный вход)")
        void invalidDamage() {
            assertEquals(0.0, CombatMath.cappedDamage(Double.NaN, 1000.0, 35.0), EPS);
            assertEquals(0.0, CombatMath.cappedDamage(0.0, 1000.0, 35.0), EPS);
            assertEquals(0.0, CombatMath.cappedDamage(-10.0, 1000.0, 35.0), EPS);
        }

        @Test
        @DisplayName("NaN/<=0 maxHp → урон без изменений (невалидный контекст)")
        void invalidMaxHp() {
            assertEquals(500.0, CombatMath.cappedDamage(500.0, Double.NaN, 35.0), EPS);
            assertEquals(500.0, CombatMath.cappedDamage(500.0, 0.0, 35.0), EPS);
            assertEquals(500.0, CombatMath.cappedDamage(500.0, -100.0, 35.0), EPS);
        }
    }

    @Nested
    @DisplayName("sanitize: чистка DamageProfile")
    class Sanitize {

        @Test
        @DisplayName("чистый профиль возвращается тем же объектом (без аллокации)")
        void returnsSameInstanceWhenClean() {
            DamageProfile clean = new DamageProfile(10.0, 20.0, 5.0);
            assertSame(clean, CombatMath.sanitize(clean));
        }

        @Test
        @DisplayName("NaN в компоненте → 0.0")
        void nanToZero() {
            DamageProfile out = CombatMath.sanitize(new DamageProfile(Double.NaN, 20.0, 5.0));
            assertEquals(0.0, out.physical(), EPS);
            assertEquals(20.0, out.magic(), EPS);
            assertEquals(5.0, out.trueDamage(), EPS);
        }

        @Test
        @DisplayName("отрицательные значения → 0.0")
        void negativeToZero() {
            DamageProfile out = CombatMath.sanitize(new DamageProfile(10.0, -5.0, -1.0));
            assertEquals(10.0, out.physical(), EPS);
            assertEquals(0.0, out.magic(), EPS);
            assertEquals(0.0, out.trueDamage(), EPS);
        }

        @Test
        @DisplayName("всё NaN/отрицательное → нулевой профиль")
        void allInvalid() {
            DamageProfile out = CombatMath.sanitize(new DamageProfile(Double.NaN, -1.0, Double.NEGATIVE_INFINITY));
            assertEquals(0.0, out.physical(), EPS);
            assertEquals(0.0, out.magic(), EPS);
            assertEquals(0.0, out.trueDamage(), EPS);
        }
    }

    @Nested
    @DisplayName("effectiveResist: пробитие в порядке flat → pct (чек 62)")
    class EffectiveResist {

        @Test
        @DisplayName("40% резист − 25% pct-pen = 30 (якорь чека 62)")
        void anchorCheck62() {
            assertEquals(30.0, CombatMath.effectiveResist(40.0, 0.0, 0.25, 0.40), EPS);
        }

        @Test
        @DisplayName("flat-pen вычитается ПЕРВЫМ, затем pct от остатка")
        void flatBeforePct() {
            // 50% резист, flat=20, pct=0.5 → (50-20) * 0.5 = 15
            assertEquals(15.0, CombatMath.effectiveResist(50.0, 20.0, 0.5, 1.0), EPS);
            // если бы порядок был обратный: 50*0.5 - 20 = 5 (неверно)
            assertTrue(Math.abs(CombatMath.effectiveResist(50.0, 20.0, 0.5, 1.0) - 15.0) < 1e-9,
                    "flat вычитается первым, не после pct");
        }

        @Test
        @DisplayName("pct-pen ограничен капом")
        void pctPenCapped() {
            // pct=0.9 при cap=0.4 → применяется только 0.4
            // 100% резист, flat=0 → 100 * (1-0.4) = 60
            assertEquals(60.0, CombatMath.effectiveResist(100.0, 0.0, 0.9, 0.40), EPS);
        }

        @Test
        @DisplayName("flat-pen не может увести резист в минус")
        void flatCannotGoNegative() {
            // 10% резист, flat=50 → 0 (а не -40)
            assertEquals(0.0, CombatMath.effectiveResist(10.0, 50.0, 0.0, 1.0), EPS);
        }

        @Test
        @DisplayName("NaN-guards: NaN-параметры = 0")
        void nanGuards() {
            assertEquals(40.0, CombatMath.effectiveResist(40.0, Double.NaN, Double.NaN, 0.40), EPS);
            assertEquals(40.0, CombatMath.effectiveResist(40.0, 0.0, 0.0, Double.NaN), EPS);
        }
    }

    @Nested
    @DisplayName("mitigation: % резиста → множитель поглощения")
    class Mitigation {

        @Test
        @DisplayName("50% резист с cap=90% → 0.5")
        void normal() {
            assertEquals(0.5, CombatMath.mitigation(50.0, 90.0), EPS);
        }

        @Test
        @DisplayName("резист выше cap → обрезается до cap/100")
        void overCap() {
            assertEquals(0.9, CombatMath.mitigation(150.0, 90.0), EPS);
        }

        @Test
        @DisplayName("отрицательный резист → 0")
        void negative() {
            assertEquals(0.0, CombatMath.mitigation(-10.0, 90.0), EPS);
        }

        @Test
        @DisplayName("NaN-резист → 0, NaN-cap → дефолт 90")
        void nanGuards() {
            assertEquals(0.0, CombatMath.mitigation(Double.NaN, 90.0), EPS);
            assertEquals(0.5, CombatMath.mitigation(50.0, Double.NaN), EPS);
        }
    }
}
