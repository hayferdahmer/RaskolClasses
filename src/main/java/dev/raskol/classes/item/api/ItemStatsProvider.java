// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.api;

import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.ItemStatSnapshot;

import java.util.UUID;

/** Read-only view over immutable per-player item snapshots. */
public interface ItemStatsProvider {
    ItemStatSnapshot snapshot(UUID playerId);

    default double value(UUID playerId, ItemStat stat) {
        return snapshot(playerId).value(stat);
    }

    default String autoAttackType(UUID playerId) {
        return snapshot(playerId).autoAttackType();
    }
}
