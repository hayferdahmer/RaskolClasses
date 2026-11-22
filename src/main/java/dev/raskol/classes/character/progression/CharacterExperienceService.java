// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

import dev.raskol.classes.character.profile.CharacterProfileStorage;

/** Единственная точка начисления опыта и изменения уровня персонажа. */
public final class CharacterExperienceService {

    private final CharacterProfileStorage profiles;
    private final CharacterExperienceCurve curve;

    public CharacterExperienceService(CharacterProfileStorage profiles, CharacterExperienceCurve curve) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.curve = Objects.requireNonNull(curve, "curve");
    }

    /** Начисляет опыт, сохраняет новый снимок профиля и возвращает результат перехода. */
    public CharacterProgressionEngine.AwardResult award(UUID playerId, long experience) {
        Objects.requireNonNull(playerId, "playerId");
        if (experience < 0) {
            throw new IllegalArgumentException("Начисляемый опыт не может быть отрицательным");
        }

        CharacterProfile current = profiles.ensureProfile(playerId);
        CharacterProgressionEngine.State state =
                new CharacterProgressionEngine.State(current.level(), current.experienceIntoLevel());
        CharacterProgressionEngine.AwardResult result =
                CharacterProgressionEngine.award(state, experience, curve::requiredExperience);

        if (!result.state().equals(state)) {
            long updatedAt = Math.max(current.updatedAtMillis(), System.currentTimeMillis());
            profiles.upsert(new CharacterProfile(
                    playerId,
                    current.schemaVersion(),
                    result.state().level(),
                    result.state().experienceIntoLevel(),
                    current.createdAtMillis(),
                    updatedAt));
        }
        return result;
    }

    /**
     * Возвращает снимок прогрессии; при изменении XP-кривой применяет уже накопленный опыт
     * к активным порогам и сохраняет согласованное состояние профиля.
     */
    public ProgressSnapshot snapshot(UUID playerId) {
        CharacterProfile profile = profiles.ensureProfile(Objects.requireNonNull(playerId, "playerId"));
        CharacterProgressionEngine.State state =
                new CharacterProgressionEngine.State(profile.level(), profile.experienceIntoLevel());
        CharacterProgressionEngine.AwardResult reconciled =
                CharacterProgressionEngine.award(state, 0L, curve::requiredExperience);
        if (!reconciled.state().equals(state)) {
            long updatedAt = Math.max(profile.updatedAtMillis(), System.currentTimeMillis());
            profile = new CharacterProfile(
                    profile.playerId(),
                    profile.schemaVersion(),
                    reconciled.state().level(),
                    reconciled.state().experienceIntoLevel(),
                    profile.createdAtMillis(),
                    updatedAt);
            profiles.upsert(profile);
        }

        OptionalLong experienceToNextLevel = profile.level() >= CharacterProgressionEngine.MAX_LEVEL
                ? OptionalLong.empty()
                : OptionalLong.of(curve.requiredExperience(profile.level()) - profile.experienceIntoLevel());
        return new ProgressSnapshot(profile.level(), profile.experienceIntoLevel(), experienceToNextLevel);
    }

    public record ProgressSnapshot(int level, long currentExperience, OptionalLong experienceToNextLevel) {
        public ProgressSnapshot {
            if (level < CharacterProgressionEngine.MIN_LEVEL || level > CharacterProgressionEngine.MAX_LEVEL) {
                throw new IllegalArgumentException("Уровень персонажа вне диапазона 1–60");
            }
            if (currentExperience < 0L || experienceToNextLevel == null) {
                throw new IllegalArgumentException("Некорректное состояние XP-снимка");
            }
            if ((level == CharacterProgressionEngine.MAX_LEVEL) != experienceToNextLevel.isEmpty()) {
                throw new IllegalArgumentException("Наличие следующего уровня не совпадает с уровнем персонажа");
            }
            if (experienceToNextLevel.isPresent() && experienceToNextLevel.getAsLong() < 0L) {
                throw new IllegalArgumentException("Остаток опыта до следующего уровня не может быть отрицательным");
            }
        }
    }
}
