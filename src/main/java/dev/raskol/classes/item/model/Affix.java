// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.model;

/** Immutable weighted affix range. */
public record Affix(ItemStat stat, double min, double max, double weight) {
    public Affix {
        if (stat == null || !Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(weight)
                || min < stat.min() || max > stat.max() || min > max || weight <= 0) {
            throw new IllegalArgumentException("Invalid affix range");
        }
    }
}
