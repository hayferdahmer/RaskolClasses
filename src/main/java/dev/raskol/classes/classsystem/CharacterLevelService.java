// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.character.profile.CharacterProfileStorage;

/** Читает единый уровень персонажа из канонического профиля. */
public final class CharacterLevelService {

    private final CharacterProfileStorage profiles;

    public CharacterLevelService(CharacterProfileStorage profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public int characterLevel(UUID uuid) {
        return profiles.ensureProfile(Objects.requireNonNull(uuid, "uuid")).level();
    }

    /** Оставлено для совместимости владельцев старых runtime-задач; кэш уровня больше не используется. */
    public void invalidate(UUID uuid) {}

    /** Оставлено для совместимости владельцев старых runtime-задач; кэш уровня больше не используется. */
    public void purgeStale() {}
}
