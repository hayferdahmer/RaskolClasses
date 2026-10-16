// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.dot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1.14.10 (Sprint 7, волна 7.1b, P0-11-01): полное покрытие DotMath.
 * Якорные значения из selftest-чеков 66/67/68.
 */
@DisplayName("DotMath: DoT-капы и множители")
class DotMathTest {

    private static final double EPS = 1e-9;

    @Nested
    @DisplayName("capFactor: масштабирование суммарного DPS (чек 66)")
    class CapFactor {

        @Test
        @DisplayName("[40,30] против limit=60 → 60/70 ≈ 0.857 (якорь чека 66)")
        void scalesDownWhenOverLimit() {
            assertEquals(60.0 / 70.0, DotMath.capFactor(new double[]{40.0, 30.0}, 60.0), EPS);
        }

        @Test
        @DisplayName("[10] против limit=60 → 1.0 (sum ≤ limit)")
        void noScalingUnderLimit() {
            assertEquals(1.0, DotMath.capFactor(new double[]{10.0}, 60.0), EPS);
        }

        @Test
        @DisplayName("[0] против limit=60 → 1.0 (sum ≤ 0 = ничего не применяем)")
        void zeroSumReturnsOne() {
            assertEquals(1.0, DotMath.capFactor(new double[]{0.0}, 60.0), EPS);
        }

        @Test
        @DisplayName("null/empty массив → 1.0 (ничего не применяем)")
        void nullOrEmpty() {
            assertEquals(1.0, DotMath.capFactor(null, 60.0), EPS);
            assertEquals(1.0, DotMath.capFactor(new double[0], 60.0), EPS);
        }

        @Test
        @DisplayName("limit ≤ 0 → 1.0 (кап выключен)")
        void invalidLimit() {
            assertEquals(1.0, DotMath.capFactor(new double[]{40.0, 30.0}, 0.0), EPS);
            assertEquals(1.0, DotMath.capFactor(new double[]{40.0, 30.0}, -5.0), EPS);
            assertEquals(1.0, DotMath.capFactor(new double[]{40.0, 30.0}, Double.NaN), EPS);
        }

        @Test
        @DisplayName("NaN/отрицательные элементы dps → не учитываются в sum")
        void invalidDpsElementsIgnored() {
            // sum = 30 (40 неFinite, -5 отсечён), limit=60 → 1.0
            assertEquals(1.0, DotMath.capFactor(new double[]{Double.NaN, -5.0, 30.0}, 60.0), EPS);
            // sum = 70, limit=60 → 60/70
            assertEquals(60.0 / 70.0,
                    DotMath.capFactor(new double[]{Double.NaN, 40.0, 30.0}, 60.0), EPS);
        }
    }

    @Nested
    @DisplayName("withMults: dps × school × immunity × (1 + seal) (чек 67)")
    class WithMults {

        @Test
        @DisplayName("10 × 1.5 × 0.5 × 1.26 = 9.45 (якорь чека 67)")
        void anchorCheck67() {
            assertEquals(9.45, DotMath.withMults(10.0, 1.5, 0.5, 0.26), EPS);
        }

        @Test
        @DisplayName("schoolMult=1, immunity=0 → 0 (иммунитет)")
        void immunityZero() {
            assertEquals(0.0, DotMath.withMults(10.0, 1.0, 0.0, 0.0), EPS);
        }

        @Test
        @DisplayName("dps ≤ 0 → 0 (некорректный вход)")
        void invalidDps() {
            assertEquals(0.0, DotMath.withMults(-5.0, 2.0, 1.0, 0.0), EPS);
            assertEquals(0.0, DotMath.withMults(0.0, 2.0, 1.0, 0.0), EPS);
            assertEquals(0.0, DotMath.withMults(Double.NaN, 2.0, 1.0, 0.0), EPS);
        }

        @Test
        @DisplayName("NaN-множители → 1.0 (безопасный дефолт), seal < 0 → 0")
        void nanMultDefaults() {
            // 10 × 1 × 1 × 1 = 10 (все NaN заменены на 1.0)
            assertEquals(10.0, DotMath.withMults(10.0, Double.NaN, Double.NaN, Double.NaN), EPS);
            // seal < 0 → 0 → множитель = 1.0
            assertEquals(10.0, DotMath.withMults(10.0, 1.0, 1.0, -0.5), EPS);
        }
    }

    @Nested
    @DisplayName("dpsLimit: процентный кап → абсолютный лимит DPS (чек 68)")
    class DpsLimit {

        @Test
        @DisplayName("maxHp=1000, cap=6% → 60 (якорь чека 68)")
        void anchorCheck68() {
            assertEquals(60.0, DotMath.dpsLimit(1000.0, 6.0), EPS);
        }

        @Test
        @DisplayName("maxHp ≤ 0 → 0 (невалидный контекст)")
        void invalidMaxHp() {
            assertEquals(0.0, DotMath.dpsLimit(0.0, 6.0), EPS);
            assertEquals(0.0, DotMath.dpsLimit(-100.0, 6.0), EPS);
            assertEquals(0.0, DotMath.dpsLimit(Double.NaN, 6.0), EPS);
        }

        @Test
        @DisplayName("cap ≤ 0 → дефолт 6%")
        void invalidCapUsesDefault() {
            assertEquals(60.0, DotMath.dpsLimit(1000.0, 0.0), EPS);
            assertEquals(60.0, DotMath.dpsLimit(1000.0, -5.0), EPS);
            assertEquals(60.0, DotMath.dpsLimit(1000.0, Double.NaN), EPS);
        }
    }
}
