// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.profile;

import java.util.Objects;
import java.util.UUID;

/** Неизменяемый профиль прогрессии персонажа. */
public record CharacterProfile(
        UUID playerId,
        int schemaVersion,
        int level,
        long experienceIntoLevel,
        long createdAtMillis,
        long updatedAtMillis) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 60;

    public CharacterProfile {
        Objects.requireNonNull(playerId, "playerId");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая версия профиля: " + schemaVersion);
        }
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Уровень персонажа должен быть в диапазоне 1–60");
        }
        if (experienceIntoLevel < 0) {
            throw new IllegalArgumentException("Опыт текущего уровня не может быть отрицательным");
        }
        if (level == MAX_LEVEL && experienceIntoLevel != 0) {
            throw new IllegalArgumentException("На максимальном уровне опыт до следующего уровня отсутствует");
        }
        if (createdAtMillis < 0 || updatedAtMillis < createdAtMillis) {
            throw new IllegalArgumentException("Временные метки профиля некорректны");
        }
    }

    public static CharacterProfile newCharacter(UUID playerId, long nowMillis) {
        return new CharacterProfile(playerId, CURRENT_SCHEMA_VERSION, MIN_LEVEL, 0, nowMillis, nowMillis);
    }
}
