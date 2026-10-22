package dev.raskol.classes.item;

import java.util.UUID;

/** Read-only view over immutable per-player item snapshots. */
public interface ItemStatsProvider {
    ItemStatSnapshot snapshot(UUID playerId);
    default double value(UUID playerId, ItemStat stat) { return snapshot(playerId).value(stat); }
    default String autoAttackType(UUID playerId) { return snapshot(playerId).autoAttackType(); }
}
