// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.api;

import dev.raskol.classes.item.model.ItemStat;

import java.util.Map;

/** A future-proof, immutable contribution to an equipment stat aggregate. */
public interface StatSource {
    Map<ItemStat, Double> stats();
}
