// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Потокобезопасный TTL-кэш классов игроков. */
final class ClassCache {

    private static final long TTL_MILLIS = 5_000L;

    record Entry(PlayerClass playerClass, long expiresAt) {}

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    ClassCache() {
        this(System::currentTimeMillis);
    }

    ClassCache(LongSupplier clock) {
        this.clock = clock;
    }

    Entry get(UUID playerId) {
        Entry entry = entries.get(playerId);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() <= clock.getAsLong()) {
            entries.remove(playerId, entry);
            return null;
        }
        return entry;
    }

    void put(UUID playerId, PlayerClass playerClass) {
        entries.put(playerId, new Entry(playerClass, clock.getAsLong() + TTL_MILLIS));
    }

    void invalidate(UUID playerId) {
        entries.remove(playerId);
    }

    void clear() {
        entries.clear();
    }
}
