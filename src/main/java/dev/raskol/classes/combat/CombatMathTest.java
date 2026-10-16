// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1.14.10 (Sprint 7, волна 7.1, P0-11-01): регресс-якорь формулы эффективного
 * резиста. Значение совпадает с selftest-чеком 62 (40% резиста − 25% пробития = 30).
 * Расширяется в волне 7.1b после инвентаризации остальных методов CombatMath.
 */
@DisplayName("CombatMath: эффективный резист")
class CombatMathTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("effectiveResist(40, 0, 0.25, 0.40) = 30 (якорь чека 62)")
    void effectiveResistAnchor() {
        assertEquals(30.0, CombatMath.effectiveResist(40.0, 0.0, 0.25, 0.40), EPS);
    }
}
