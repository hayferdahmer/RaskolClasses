package dev.raskol.classes.item;

import java.util.Map;
import java.util.Set;

/** Immutable precomputed equipment view; safe for combat hot paths. */
public record ItemStatSnapshot(Map<ItemStat, Double> stats, Set<String> unmetRequirements,
                               String mainHandItemId, String autoAttackType, long equipmentHash) {
    public static final ItemStatSnapshot EMPTY = new ItemStatSnapshot(Map.of(), Set.of(), null, "PHYSICAL", 0L);
    public ItemStatSnapshot { stats = Map.copyOf(stats); unmetRequirements = Set.copyOf(unmetRequirements); }
    public double value(ItemStat stat) { return stats.getOrDefault(stat, 0.0); }
}
