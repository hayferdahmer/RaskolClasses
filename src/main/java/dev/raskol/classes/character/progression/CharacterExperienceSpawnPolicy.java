// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.character.progression;

import java.util.Set;

/** Чистая политика допустимого PvE-опыта и причин появления сущностей. */
public final class CharacterExperienceSpawnPolicy {

    private static final Set<String> NATURAL_SPAWN_REASONS = Set.of(
            "NATURAL", "CHUNK_GEN", "PATROL", "RAID", "REINFORCEMENTS", "VILLAGE_DEFENSE", "VILLAGE_INVASION");

    private CharacterExperienceSpawnPolicy() {}

    public static boolean isEligibleSpawn(String entityType, String spawnReason) {
        if (entityType == null || spawnReason == null) {
            return false;
        }
        if (NATURAL_SPAWN_REASONS.contains(spawnReason)) {
            return true;
        }
        return entityType.equals("WITHER") && spawnReason.equals("BUILD_WITHER")
                || entityType.equals("ENDER_DRAGON") && spawnReason.equals("DEFAULT");
    }

    public static boolean shouldAwardExperience(
            boolean playerEntity, boolean hasPlayerKiller, boolean spawnEligible, long configuredReward) {
        return !playerEntity && hasPlayerKiller && spawnEligible && configuredReward > 0L;
    }
}
