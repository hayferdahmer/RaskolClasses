// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Потокобезопасный TTL-кэш фракций игроков. */
final class FactionCache {

    private static final long TTL_MILLIS = 5_000L;

    record Entry(String faction, long expiresAt) {}

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    FactionCache() {
        this(System::currentTimeMillis);
    }

    FactionCache(LongSupplier clock) {
        this.clock = clock;
    }

    String get(UUID playerId) {
        Entry entry = entries.get(playerId);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() <= clock.getAsLong()) {
            entries.remove(playerId, entry);
            return null;
        }
        return entry.faction();
    }

    void put(UUID playerId, String faction) {
        entries.put(playerId, new Entry(faction, clock.getAsLong() + TTL_MILLIS));
    }

    void invalidate(UUID playerId) {
        entries.remove(playerId);
    }

    void clear() {
        entries.clear();
    }
}
