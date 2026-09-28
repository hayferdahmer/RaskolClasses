// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 1.12.0: профиль урона по школам: школа → абсолютная величина (formula-единицы).
 * Legacy-адаптер: fromLegacy(DamageProfile) кладёт phys→PHYSICAL, magic→ARCANE,
 * true→TRUE; toLegacy(SchoolConfig) сворачивает школы в каналы с учётом
 * глобальных множителей школ (schools.multiplier.*).
 * Immutable: builder собирает, дальше только чтение.
 */
public final class SchoolProfile {

    private final Map<School, Double> amounts;

    private SchoolProfile(Map<School, Double> amounts) {
        this.amounts = amounts;
    }

    public static SchoolProfile empty() {
        return new SchoolProfile(Collections.unmodifiableMap(new EnumMap<>(School.class)));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SchoolProfile of(School school, double value) {
        return builder().add(school, value).build();
    }

    /** Legacy-мост (1.12.0): DamageProfile → школы (magic → ARCANE). */
    public static SchoolProfile fromLegacy(DamageProfile p) {
        Builder b = builder();
        if (p.physical() > 0.0) {
            b.add(School.PHYSICAL, p.physical());
        }
        if (p.magic() > 0.0) {
            b.add(School.ARCANE, p.magic());
        }
        if (p.trueDamage() > 0.0) {
            b.add(School.TRUE, p.trueDamage());
        }
        return b.build();
    }

    public double amount(School school) {
        return amounts.getOrDefault(school, 0.0);
    }

    public double total() {
        double sum = 0.0;
        for (double v : amounts.values()) {
            sum += v;
        }
        return sum;
    }

    public boolean isEmpty() {
        return total() <= 0.0;
    }

    public Set<School> schools() {
        return amounts.keySet();
    }

    /** Доминирующая школа (максимальная величина) — для триггеров DoT (1.12.5). */
    public School dominant() {
        School best = null;
        double bestV = 0.0;
        for (Map.Entry<School, Double> e : amounts.entrySet()) {
            if (best == null || e.getValue() > bestV) {
                best = e.getKey();
                bestV = e.getValue();
            }
        }
        return best;
    }

    /**
     * Сворачивание в legacy-каналы: каждая школа умножается на глобальный
     * множитель школы (schools.multiplier.*) и падает в свой канал.
     * PHYSICAL→physical, MAGIC-школы→magic, TRUE→trueDamage.
     */
    public DamageProfile toLegacy(SchoolConfig cfg) {
        double phys = 0.0;
        double magic = 0.0;
        double truth = 0.0;
        for (Map.Entry<School, Double> e : amounts.entrySet()) {
            double v = e.getValue() * cfg.multiplier(e.getKey());
            switch (e.getKey().channel()) {
                case PHYSICAL -> phys += v;
                case MAGIC -> magic += v;
                case TRUE -> truth += v;
            }
        }
        return new DamageProfile(phys, magic, truth);
    }

    public static final class Builder {
        private final EnumMap<School, Double> map = new EnumMap<>(School.class);

        public Builder add(School school, double value) {
            if (school == null || !Double.isFinite(value) || value <= 0.0) {
                return this;
            }
            map.merge(school, value, Double::sum);
            return this;
        }

        public SchoolProfile build() {
            EnumMap<School, Double> copy = new EnumMap<>(School.class);
            copy.putAll(map);
            return new SchoolProfile(Collections.unmodifiableMap(copy));
        }
    }
}
