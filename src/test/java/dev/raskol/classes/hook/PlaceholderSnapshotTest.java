// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Проверки неизменяемости опубликованных снимков PlaceholderAPI. */
@DisplayName("PlaceholderSnapshot: immutable published values")
class PlaceholderSnapshotTest {

    @Test
    @DisplayName("снимок не меняется при последующем изменении исходной карты")
    void snapshotCopiesSourceMaps() {
        Map<String, String> raskol = new HashMap<>();
        raskol.put("hp", "125");
        Map<String, String> crown = new HashMap<>();
        crown.put("faction", "rassvet");

        PlaceholderSnapshot snapshot = new PlaceholderSnapshot(raskol, crown);
        raskol.put("hp", "1");
        crown.put("faction", "valradis");

        assertEquals("125", snapshot.raskolValue("hp"));
        assertEquals("rassvet", snapshot.crownValue("faction"));
    }

    @Test
    @DisplayName("опубликованные карты нельзя изменить через снимок")
    void snapshotMapsAreUnmodifiable() {
        PlaceholderSnapshot snapshot = new PlaceholderSnapshot(
                Map.of("level", "10"),
                Map.of("crown", "Рассвет"));

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.raskolValues().put("level", "99"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.crownValues().clear());
    }

    @Test
    @DisplayName("неизвестный параметр не подменяется значением")
    void unknownParameterReturnsNull() {
        PlaceholderSnapshot snapshot = new PlaceholderSnapshot(
                Map.of("level", "10"),
                Map.of("crown", "Рассвет"));

        assertNull(snapshot.raskolValue("unknown"));
        assertNull(snapshot.crownValue("unknown"));
    }
}
