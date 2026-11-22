// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.character.progression.CharacterExperienceService;

/** Читает единый уровень персонажа из канонического профиля. */
public final class CharacterLevelService {

    private final CharacterExperienceService experience;

    public CharacterLevelService(CharacterExperienceService experience) {
        this.experience = Objects.requireNonNull(experience, "experience");
    }

    public int characterLevel(UUID uuid) {
        return experience.snapshot(Objects.requireNonNull(uuid, "uuid")).level();
    }
}
