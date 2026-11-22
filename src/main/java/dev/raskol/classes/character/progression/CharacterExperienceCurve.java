// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

/** Настраиваемая кривая опыта персонажа с целочисленной арифметикой. */
public record CharacterExperienceCurve(long baseExperience, long linearCoefficient, int quadraticTenths) {

    public CharacterExperienceCurve {
        if (baseExperience < 0 || linearCoefficient < 0 || quadraticTenths < 0) {
            throw new IllegalArgumentException("Коэффициенты кривой опыта не могут быть отрицательными");
        }
        if (baseExperience == 0 && linearCoefficient == 0 && quadraticTenths == 0) {
            throw new IllegalArgumentException("Кривая опыта должна задавать положительный порог");
        }
    }

    public static CharacterExperienceCurve defaults() {
        return new CharacterExperienceCurve(120L, 18L, 9);
    }

    /** Возвращает стоимость перехода с указанного уровня на следующий. */
    public long requiredExperience(int level) {
        if (level < CharacterProgressionEngine.MIN_LEVEL || level >= CharacterProgressionEngine.MAX_LEVEL) {
            throw new IllegalArgumentException("Для расчёта порога нужен уровень от 1 до 59");
        }
        long linear = Math.multiplyExact(linearCoefficient, level);
        long square = Math.multiplyExact((long) level, level);
        long quadraticNumerator = Math.multiplyExact((long) quadraticTenths, square);
        long quadratic = Math.addExact(quadraticNumerator, 5L) / 10L;
        long required = Math.addExact(Math.addExact(baseExperience, linear), quadratic);
        if (required <= 0) {
            throw new IllegalArgumentException("Порог опыта должен быть положительным");
        }
        return required;
    }

    /** Суммарный опыт, необходимый для достижения целевого уровня с первого. */
    public long totalExperienceToLevel(int targetLevel) {
        if (targetLevel < CharacterProgressionEngine.MIN_LEVEL || targetLevel > CharacterProgressionEngine.MAX_LEVEL) {
            throw new IllegalArgumentException("Целевой уровень должен быть в диапазоне 1–60");
        }
        long total = 0L;
        for (int level = CharacterProgressionEngine.MIN_LEVEL; level < targetLevel; level++) {
            total = Math.addExact(total, requiredExperience(level));
        }
        return total;
    }
}
