// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.resource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Подготовка атомарного сохранения ресурсов с учётом отложенных сбросов.
 *
 * Маркер можно снять только для игрока, чьё актуальное состояние присутствует
 * в runtime-кэше и который всё ещё онлайн. Для офлайн-игроков маркер остаётся
 * до PlayerJoinEvent. Если запись не удалась, маркеры восстанавливаются.
 */
final class ResourceResetPersistence {

    static final String PENDING_RESETS_PATH = "pending-resets";

    private ResourceResetPersistence() {}

    static Set<UUID> prepareSave(
            YamlConfiguration store, Map<UUID, ResourceState> states, Set<UUID> resetOnJoin, Set<UUID> onlinePlayers) {
        Set<UUID> resolvedMarkers = new HashSet<>();
        for (Map.Entry<UUID, ResourceState> entry : states.entrySet()) {
            UUID uuid = entry.getKey();
            store.set(uuid.toString(), entry.getValue().getValue());
            if (onlinePlayers.contains(uuid)
                    && (resetOnJoin.contains(uuid) || store.getBoolean(PENDING_RESETS_PATH + "." + uuid, false))) {
                store.set(PENDING_RESETS_PATH + "." + uuid, null);
                resolvedMarkers.add(uuid);
            }
        }
        return resolvedMarkers;
    }

    static void completeSave(Set<UUID> resolvedMarkers, Set<UUID> resetOnJoin) {
        resetOnJoin.removeAll(resolvedMarkers);
    }

    static void restoreAfterFailedSave(YamlConfiguration store, Set<UUID> resolvedMarkers, Set<UUID> resetOnJoin) {
        for (UUID uuid : resolvedMarkers) {
            resetOnJoin.add(uuid);
            store.set(PENDING_RESETS_PATH + "." + uuid, true);
        }
    }
}
