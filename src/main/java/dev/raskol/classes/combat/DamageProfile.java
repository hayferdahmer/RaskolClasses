// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

/**
 * 1.6.0: профиль урона — три компоненты (физ / маг / чистый).
 * Гибрид = профиль с ненулевыми physical и magic одновременно.
 * Резисты применяются покомпонентно: физ → физрезист (+ ванильная броня после),
 * маг → магрезист (броню не трогает), чистый → проходит целиком.
 */
public record DamageProfile(double physical, double magic, double trueDamage) {

    public static DamageProfile physical(double value) {
        return new DamageProfile(value, 0.0, 0.0);
    }

    public static DamageProfile magic(double value) {
        return new DamageProfile(0.0, value, 0.0);
    }

    public static DamageProfile trueDmg(double value) {
        return new DamageProfile(0.0, 0.0, value);
    }

    public static DamageProfile hybrid(double physical, double magic) {
        return new DamageProfile(physical, magic, 0.0);
    }

    public double total() {
        return physical + magic + trueDamage;
    }

    public boolean isEmpty() {
        return total() <= 0.0;
    }

    public DamageProfile scaled(double k) {
        return new DamageProfile(physical * k, magic * k, trueDamage * k);
    }
}
