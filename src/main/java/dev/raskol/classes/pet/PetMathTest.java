// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.10 (Sprint 7, волна 7.1, P0-11-01): unit-тесты pure-математики петов.
 * Якоря совпадают с selftest-чеками 109/111 — расхождение unit и in-game
 * проверки невозможно без падения обоих.
 */
@DisplayName("PetMath: pure-масштабирование петов")
class PetMathTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("hp = base + attr×2, далее ×(1+pct/100)")
    void hpScaling() {
        assertEquals(120.0, PetMath.hp(20.0, 50.0, 0.0), EPS);
        assertEquals(132.0, PetMath.hp(20.0, 50.0, 10.0), EPS);
    }

    @Test
    @DisplayName("hp: NaN-атрибут не ломает формулу (guard → base)")
    void hpNaNguard() {
        assertEquals(20.0, PetMath.hp(20.0, Double.NaN, 0.0), EPS);
    }

    @Test
    @DisplayName("dmg = base + power×0.3, далее ×(1+pct/100) × mult")
    void damageScaling() {
        assertEquals(34.0, PetMath.damage(4.0, 100.0, 0.0, 1.0), EPS);
        assertEquals(102.0, PetMath.damage(4.0, 100.0, 50.0, 2.0), EPS);
    }

    @Test
    @DisplayName("expired: ttl=0 вечен; expiry в прошлом → true; в будущем → false")
    void expiredSemantics() {
        long now = System.currentTimeMillis();
        assertFalse(PetMath.expired(0L, now), "ttl 0 = питомец постоянный");
        assertTrue(PetMath.expired(now - 1L, now), "истёкшая метка");
        assertFalse(PetMath.expired(now + 5_000L, now), "живая метка");
    }

    @Test
    @DisplayName("needsTeleport: distSq > radius² (13>12 телепорт, 11<12 нет)")
    void needsTeleportThreshold() {
        assertTrue(PetMath.needsTeleport(13.0 * 13.0, 12.0));
        assertFalse(PetMath.needsTeleport(11.0 * 11.0, 12.0));
    }
}
