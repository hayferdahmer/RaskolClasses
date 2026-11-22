// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResourceResetPersistence: сохранение маркеров сброса")
class ResourceResetPersistenceTest {

    @Test
    @DisplayName("онлайн-состояние фиксирует новый ресурс и снимает отложенный маркер")
    void onlineStateResolvesPendingReset() {
        UUID playerId = UUID.randomUUID();
        ResourceState state = new ResourceState();
        state.setValue(35.0);
        YamlConfiguration store = new YamlConfiguration();
        store.set(ResourceResetPersistence.PENDING_RESETS_PATH + "." + playerId, true);
        Set<UUID> pending = new java.util.HashSet<>(Set.of(playerId));

        Map<UUID, Long> generations = new HashMap<>(Map.of(playerId, 1L));
        Map<UUID, Long> resolved = ResourceResetPersistence.prepareSave(
                store, Map.of(playerId, state), pending, Set.of(playerId), generations);

        assertEquals(35.0, store.getDouble(playerId.toString()), 0.0001);
        assertFalse(store.getBoolean(ResourceResetPersistence.PENDING_RESETS_PATH + "." + playerId));
        assertTrue(resolved.containsKey(playerId));
        assertTrue(pending.contains(playerId), "in-memory marker is retained until disk save succeeds");

        ResourceResetPersistence.completeSave(resolved, pending, generations);
        assertFalse(pending.contains(playerId));
    }

    @Test
    @DisplayName("офлайн-маркер не снимается при сохранении других состояний")
    void offlinePendingResetRemains() {
        UUID offlineId = UUID.randomUUID();
        UUID onlineId = UUID.randomUUID();
        ResourceState onlineState = new ResourceState();
        onlineState.setValue(12.0);
        YamlConfiguration store = new YamlConfiguration();
        store.set(ResourceResetPersistence.PENDING_RESETS_PATH + "." + offlineId, true);
        Set<UUID> pending = new java.util.HashSet<>(Set.of(offlineId));

        Map<UUID, Long> generations = new HashMap<>();
        Map<UUID, Long> resolved = ResourceResetPersistence.prepareSave(
                store, Map.of(onlineId, onlineState), pending, Set.of(onlineId), generations);

        assertTrue(store.getBoolean(ResourceResetPersistence.PENDING_RESETS_PATH + "." + offlineId));
        assertTrue(pending.contains(offlineId));
        assertTrue(resolved.isEmpty());
    }

    @Test
    @DisplayName("старое успешное сохранение не подтверждает новое намерение сброса")
    void staleSuccessDoesNotClearNewerResetIntent() {
        UUID playerId = UUID.randomUUID();
        ResourceState state = new ResourceState();
        state.setValue(35.0);
        YamlConfiguration store = new YamlConfiguration();
        store.set(ResourceResetPersistence.PENDING_RESETS_PATH + "." + playerId, true);
        Set<UUID> pending = new java.util.HashSet<>(Set.of(playerId));
        Map<UUID, Long> generations = new HashMap<>(Map.of(playerId, 4L));

        Map<UUID, Long> snapshot = ResourceResetPersistence.prepareSave(
                store, Map.of(playerId, state), pending, Set.of(playerId), generations);
        generations.put(playerId, 5L);

        ResourceResetPersistence.completeSave(snapshot, pending, generations);

        assertTrue(pending.contains(playerId), "new reset intent must remain active");
    }

    @Test
    @DisplayName("ошибка атомарной записи восстанавливает маркер для следующей попытки")
    void failedSaveRestoresResolvedMarker() {
        UUID playerId = UUID.randomUUID();
        ResourceState state = new ResourceState();
        state.setValue(35.0);
        YamlConfiguration store = new YamlConfiguration();
        store.set(ResourceResetPersistence.PENDING_RESETS_PATH + "." + playerId, true);
        Set<UUID> pending = new java.util.HashSet<>(Set.of(playerId));

        Map<UUID, Long> generations = new HashMap<>(Map.of(playerId, 1L));
        Map<UUID, Long> resolved = ResourceResetPersistence.prepareSave(
                store, Map.of(playerId, state), pending, Set.of(playerId), generations);
        ResourceResetPersistence.restoreAfterFailedSave(resolved, store, pending);

        assertTrue(pending.contains(playerId));
        assertTrue(store.getBoolean(ResourceResetPersistence.PENDING_RESETS_PATH + "." + playerId));
        assertEquals(35.0, store.getDouble(playerId.toString()), 0.0001);
    }
}
