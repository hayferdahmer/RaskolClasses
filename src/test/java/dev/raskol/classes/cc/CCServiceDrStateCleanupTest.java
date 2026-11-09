// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CCService: очистка устаревшего DR-состояния")
class CCServiceDrStateCleanupTest {

    @Test
    @DisplayName("Удаляет категории после истечения окна DR")
    void removesCategoriesAfterDrWindowExpires() {
        UUID entityId = UUID.randomUUID();
        EnumMap<DRCategory, DRState> categories = new EnumMap<>(DRCategory.class);
        categories.put(DRCategory.STUN, new DRState(3, 1_000L, 500L));
        Map<UUID, EnumMap<DRCategory, DRState>> states = new java.util.HashMap<>();
        states.put(entityId, categories);

        CCService.pruneExpiredDrStates(states, 16_001L, 15_000L);

        assertTrue(states.isEmpty());
    }

    @Test
    @DisplayName("Сохраняет состояние, пока окно DR ещё действует")
    void preservesCategoriesInsideDrWindow() {
        UUID entityId = UUID.randomUUID();
        EnumMap<DRCategory, DRState> categories = new EnumMap<>(DRCategory.class);
        DRState state = new DRState(2, 1_000L, 500L);
        categories.put(DRCategory.ROOT, state);
        Map<UUID, EnumMap<DRCategory, DRState>> states = new java.util.HashMap<>();
        states.put(entityId, categories);

        CCService.pruneExpiredDrStates(states, 16_000L, 15_000L);

        assertEquals(state, states.get(entityId).get(DRCategory.ROOT));
    }

    @Test
    @DisplayName("Удаляет только истёкшие категории и сохраняет остальные")
    void removesOnlyExpiredCategories() {
        UUID entityId = UUID.randomUUID();
        EnumMap<DRCategory, DRState> categories = new EnumMap<>(DRCategory.class);
        categories.put(DRCategory.STUN, new DRState(3, 1_000L, 500L));
        DRState recent = new DRState(1, 10_000L, 10_000L);
        categories.put(DRCategory.ROOT, recent);
        Map<UUID, EnumMap<DRCategory, DRState>> states = new java.util.HashMap<>();
        states.put(entityId, categories);

        CCService.pruneExpiredDrStates(states, 16_001L, 15_000L);

        assertFalse(categories.containsKey(DRCategory.STUN));
        assertEquals(recent, categories.get(DRCategory.ROOT));
        assertEquals(1, states.size());
    }

    @Test
    @DisplayName("Удаляет пустые записи сущностей")
    void removesEmptyEntityEntries() {
        UUID entityId = UUID.randomUUID();
        Map<UUID, EnumMap<DRCategory, DRState>> states = new java.util.HashMap<>();
        states.put(entityId, new EnumMap<>(DRCategory.class));

        CCService.pruneExpiredDrStates(states, 20_000L, 15_000L);

        assertTrue(states.isEmpty());
    }

    @Test
    @DisplayName("Не изменяет карту при некорректном интервале")
    void ignoresNegativeWindow() {
        UUID entityId = UUID.randomUUID();
        EnumMap<DRCategory, DRState> categories = new EnumMap<>(DRCategory.class);
        DRState state = new DRState(1, 1_000L, 1_000L);
        categories.put(DRCategory.STUN, state);
        Map<UUID, EnumMap<DRCategory, DRState>> states = new java.util.HashMap<>();
        states.put(entityId, categories);

        CCService.pruneExpiredDrStates(states, 100_000L, -1L);

        assertEquals(state, categories.get(DRCategory.STUN));
    }
}
