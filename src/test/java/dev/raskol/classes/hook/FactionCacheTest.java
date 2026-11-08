// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Регрессионные проверки TTL и адресной инвалидации кэша фракций. */
@DisplayName("FactionCache: TTL и инвалидация")
class FactionCacheTest {

    @Test
    @DisplayName("после смены фракции старое значение удаляется немедленно")
    void invalidationRemovesStaleFactionImmediately() {
        AtomicLong now = new AtomicLong(1_000L);
        FactionCache cache = new FactionCache(now::get);
        UUID playerId = UUID.randomUUID();

        cache.put(playerId, "rassvet");
        assertEquals("rassvet", cache.get(playerId));

        cache.invalidate(playerId);
        assertNull(cache.get(playerId), "старая фракция не должна оставаться в кэше");

        cache.put(playerId, "valradis");
        assertEquals("valradis", cache.get(playerId));
    }

    @Test
    @DisplayName("инвалидация одного игрока не затрагивает кэш остальных")
    void invalidationDoesNotAffectOtherPlayers() {
        AtomicLong now = new AtomicLong(1_000L);
        FactionCache cache = new FactionCache(now::get);
        UUID changedPlayer = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();

        cache.put(changedPlayer, "rassvet");
        cache.put(otherPlayer, "valradis");

        cache.invalidate(changedPlayer);

        assertNull(cache.get(changedPlayer));
        assertEquals("valradis", cache.get(otherPlayer));
    }

    @Test
    @DisplayName("просроченная запись не возвращается после истечения TTL")
    void expiredEntryIsNotReturned() {
        AtomicLong now = new AtomicLong(1_000L);
        FactionCache cache = new FactionCache(now::get);
        UUID playerId = UUID.randomUUID();

        cache.put(playerId, "rassvet");
        now.addAndGet(5_000L);

        assertNull(cache.get(playerId));
    }
}
