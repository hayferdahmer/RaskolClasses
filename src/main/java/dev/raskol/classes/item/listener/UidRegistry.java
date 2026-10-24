// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.15.1-D: реестр владельцев item_uid (instance, а не static-карта).
 * Защита от дубликатов UID в экипировке двух игроков (копирование предметов
 * вне ItemsApi) и утечки записей после выхода.
 *
 * Жизненный цикл: создаётся ItemHook'ом; claim при reconcile слота,
 * retain после прохода экипировки, release на PlayerQuit.
 */
public final class UidRegistry {

    private final Map<String, UUID> owner = new ConcurrentHashMap<>();

    /** Первый владелец uid; null если uid ещё не занят. */
    public UUID claim(String uid, UUID player) {
        return owner.putIfAbsent(uid, player);
    }

    /** Удерживает только uid'ы текущей экипировки игрока; остальные записи игрока чистит. */
    public void retain(UUID player, Set<String> current) {
        owner.entrySet().removeIf(e -> e.getValue().equals(player) && !current.contains(e.getKey()));
    }

    /** Полный сброс записей игрока (выход с сервера). */
    public void release(UUID player) {
        owner.entrySet().removeIf(e -> e.getValue().equals(player));
    }

    /** Текущий владелец uid или null (для диагностики /rc debug). */
    public UUID ownerOf(String uid) {
        return owner.get(uid);
    }
}
