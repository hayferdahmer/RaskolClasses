// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.model;

import java.util.Map;
import java.util.Set;

/** Immutable precomputed equipment view; safe for combat hot paths. */
public record ItemStatSnapshot(
        Map<ItemStat, Double> stats,
        Set<String> unmetRequirements,
        String mainHandItemId,
        String autoAttackType,
        long equipmentHash) {

    public static final ItemStatSnapshot EMPTY = new ItemStatSnapshot(Map.of(), Set.of(), null, "PHYSICAL", 0L);

    public ItemStatSnapshot {
        stats = Map.copyOf(stats);
        unmetRequirements = Set.copyOf(unmetRequirements);
    }

    public double value(ItemStat stat) {
        return stats.getOrDefault(stat, 0.0);
    }
}
