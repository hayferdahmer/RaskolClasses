package dev.raskol.classes.item;

import java.util.Map;

/** A future-proof, immutable contribution to an equipment stat aggregate. */
public interface StatSource {
    Map<ItemStat, Double> stats();
}
