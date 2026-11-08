// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит откат burst-лимита по идентичности события, чтобы вложенный урон
 * не мог сбросить или подменить состояние внешнего события.
 *
 * Вызывается только из основного потока Bukkit.
 */
final class BurstRollbackTracker {

    private final Map<Object, Entry> entries = new IdentityHashMap<>();

    void arm(Object event, UUID playerId, double amount) {
        if (event == null || playerId == null || !Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Некорректные данные отката burst-лимита");
        }
        entries.put(event, new Entry(playerId, amount));
    }

    Entry take(Object event) {
        if (event == null) {
            return null;
        }
        return entries.remove(event);
    }

    record Entry(UUID playerId, double amount) {}
}
