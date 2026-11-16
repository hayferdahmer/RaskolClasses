// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.resource.ResourceService.ResourceSnapshot;

class ResourceSnapshotTest {

    @Test
    void acceptsAValidReadOnlyProjection() {
        ResourceSnapshot snapshot = new ResourceSnapshot(42.5, 125.0, true);

        assertEquals(42.5, snapshot.current());
        assertEquals(125.0, snapshot.effectiveMax());
        assertTrue(snapshot.inCombat());
    }

    @Test
    void rejectsNonFiniteOrNegativeCurrentValue() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceSnapshot(-1.0, 100.0, false));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSnapshot(Double.NaN, 100.0, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResourceSnapshot(Double.POSITIVE_INFINITY, 100.0, false));
    }

    @Test
    void rejectsInvalidMaximumAndCurrentAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceSnapshot(0.0, 0.0, false));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSnapshot(101.0, 100.0, false));
        assertThrows(IllegalArgumentException.class, () -> new ResourceSnapshot(1.0, Double.NaN, false));
    }

    @Test
    void allowsZeroResourceAndClampedUpperBoundary() {
        ResourceSnapshot empty = new ResourceSnapshot(0.0, 100.0, false);
        ResourceSnapshot full = new ResourceSnapshot(100.0, 100.0, false);

        assertFalse(empty.inCombat());
        assertEquals(1.0, full.current() / full.effectiveMax());
    }
}
