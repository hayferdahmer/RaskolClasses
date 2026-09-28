// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

/**
 * 1.11.4 (P4e): pure-математика спек-слоя.
 *  - asFraction: допуск единиц «проценты ИЛИ доли» (15 → 0.15; 0.15 → 0.15).
 *    Закрывает эксплойты F1/F2 (целочисленные проценты читались как доли).
 *  - respecCost: формула цены отречения (selftest-чек 47).
 */
public final class SpecMath {

    private SpecMath() {
    }

    /** Значение >1 считаем процентами и делим на 100; иначе — готовая доля. */
    public static double asFraction(double raw) {
        if (!Double.isFinite(raw) || raw < 0.0) {
            return 0.0;
        }
        return raw > 1.0 ? raw / 100.0 : raw;
    }

    /** Цена отречения: base + level × per. */
    public static int respecCost(int level, int base, int per) {
        return base + Math.max(0, level) * per;
    }
}
