// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.api;

import dev.raskol.classes.item.model.ItemStat;

import java.util.Map;

/** A future-proof, immutable contribution to an equipment stat aggregate. */
public interface StatSource {
    Map<ItemStat, Double> stats();
}
