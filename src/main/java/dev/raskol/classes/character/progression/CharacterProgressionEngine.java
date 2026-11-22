// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import java.util.Objects;
import java.util.function.IntToLongFunction;

/** Чистая модель перехода уровня персонажа без привязки к Bukkit и хранилищу. */
public final class CharacterProgressionEngine {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 60;

    private CharacterProgressionEngine() {}

    /** Состояние прогресса внутри текущего уровня. */
    public record State(int level, long experienceIntoLevel) {

        public State {
            if (level < MIN_LEVEL || level > MAX_LEVEL) {
                throw new IllegalArgumentException("Уровень персонажа должен быть в диапазоне 1–60");
            }
            if (experienceIntoLevel < 0) {
                throw new IllegalArgumentException("Опыт текущего уровня не может быть отрицательным");
            }
            if (level == MAX_LEVEL && experienceIntoLevel != 0) {
                throw new IllegalArgumentException("На максимальном уровне опыт до следующего уровня отсутствует");
            }
        }
    }

    /**
     * Результат начисления опыта. Остаток после достижения 60 уровня возвращается
     * отдельно, чтобы вызывающий слой явно определял его дальнейшую политику.
     */
    public record AwardResult(State state, int levelsGained, long unspentExperience) {

        public AwardResult {
            Objects.requireNonNull(state, "state");
            if (levelsGained < 0 || unspentExperience < 0) {
                throw new IllegalArgumentException("Результат начисления опыта содержит отрицательное значение");
            }
        }
    }

    /**
     * Начисляет опыт и переносит остаток через любое количество уровней.
     *
     * @param current текущее состояние персонажа
     * @param awardedExperience начисляемый опыт
     * @param requiredExperience функция порога для перехода с указанного уровня на следующий
     * @return новое состояние, число полученных уровней и остаток после достижения cap
     */
    public static AwardResult award(State current, long awardedExperience, IntToLongFunction requiredExperience) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requiredExperience, "requiredExperience");
        if (awardedExperience < 0) {
            throw new IllegalArgumentException("Начисляемый опыт не может быть отрицательным");
        }
        if (current.level() == MAX_LEVEL) {
            return new AwardResult(current, 0, awardedExperience);
        }

        long accumulated = Math.addExact(current.experienceIntoLevel(), awardedExperience);
        int level = current.level();
        int levelsGained = 0;

        while (level < MAX_LEVEL) {
            long threshold = requiredExperience.applyAsLong(level);
            if (threshold <= 0) {
                throw new IllegalArgumentException("Порог опыта для следующего уровня должен быть положительным");
            }
            if (accumulated < threshold) {
                break;
            }
            accumulated -= threshold;
            level++;
            levelsGained++;
        }

        if (level == MAX_LEVEL) {
            return new AwardResult(new State(MAX_LEVEL, 0), levelsGained, accumulated);
        }
        return new AwardResult(new State(level, accumulated), levelsGained, 0);
    }
}
