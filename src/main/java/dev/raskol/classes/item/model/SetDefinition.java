// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.model;

import java.util.Map;

/** A named set with cumulative stat thresholds from two through six pieces. */
public record SetDefinition(String id, String displayName, Map<Integer, Bonus> bonuses) {

    public record Bonus(Map<ItemStat, Double> stats, String description) {
        public Bonus {
            stats = Map.copyOf(stats);
        }
    }

    public SetDefinition {
        if (id == null || !id.matches("[a-z0-9_]{3,48}") || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Invalid set definition");
        }
        bonuses = Map.copyOf(bonuses);
        for (int threshold : bonuses.keySet()) {
            if (threshold < 2 || threshold > 6) throw new IllegalArgumentException("Set threshold must be 2..6");
        }
    }
}
