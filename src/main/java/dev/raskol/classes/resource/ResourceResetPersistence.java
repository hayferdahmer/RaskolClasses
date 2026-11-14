// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Подготовка атомарного сохранения ресурсов с учётом отложенных сбросов.
 *
 * Маркер можно снять только для игрока, чьё актуальное состояние присутствует
 * в runtime-кэше и который всё ещё онлайн. Для офлайн-игроков маркер остаётся
 * до PlayerJoinEvent. Подтверждение снимка сверяется с поколением reset intent.
 */
final class ResourceResetPersistence {

    static final String PENDING_RESETS_PATH = "pending-resets";

    private ResourceResetPersistence() {}

    static Map<UUID, Long> prepareSave(
            YamlConfiguration store,
            Map<UUID, ResourceState> states,
            Set<UUID> resetOnJoin,
            Set<UUID> onlinePlayers,
            Map<UUID, Long> resetGenerations) {
        Map<UUID, Long> resolvedMarkers = new HashMap<>();
        for (Map.Entry<UUID, ResourceState> entry : states.entrySet()) {
            UUID uuid = entry.getKey();
            store.set(uuid.toString(), entry.getValue().getValue());
            if (onlinePlayers.contains(uuid)
                    && (resetOnJoin.contains(uuid) || store.getBoolean(PENDING_RESETS_PATH + "." + uuid, false))) {
                store.set(PENDING_RESETS_PATH + "." + uuid, null);
                resolvedMarkers.put(uuid, resetGenerations.getOrDefault(uuid, 0L));
            }
        }
        return resolvedMarkers;
    }

    static void completeSave(
            Map<UUID, Long> resolvedMarkers, Set<UUID> resetOnJoin, Map<UUID, Long> resetGenerations) {
        resolvedMarkers.forEach((uuid, snapshotGeneration) -> {
            if (resetGenerations.getOrDefault(uuid, 0L).equals(snapshotGeneration)) {
                resetOnJoin.remove(uuid);
            }
        });
    }

    static void restoreAfterFailedSave(
            Map<UUID, Long> resolvedMarkers, YamlConfiguration store, Set<UUID> resetOnJoin) {
        for (UUID uuid : resolvedMarkers.keySet()) {
            resetOnJoin.add(uuid);
            store.set(PENDING_RESETS_PATH + "." + uuid, true);
        }
    }
}
