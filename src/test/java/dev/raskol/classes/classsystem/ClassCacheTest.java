// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Регрессионные проверки TTL и немедленной инвалидации кэша класса. */
@DisplayName("ClassCache: TTL и инвалидация")
class ClassCacheTest {

    @Test
    @DisplayName("после смены класса инвалидирование удаляет старое значение немедленно")
    void invalidationAllowsTheNewClassToBeCached() {
        AtomicLong now = new AtomicLong(1_000L);
        ClassCache cache = new ClassCache(now::get);
        UUID playerId = UUID.randomUUID();

        cache.put(playerId, PlayerClass.WARRIOR);
        assertEquals(PlayerClass.WARRIOR, cache.get(playerId).playerClass());

        cache.invalidate(playerId);
        assertNull(cache.get(playerId), "старый класс не должен оставаться доступен после инвалидации");

        cache.put(playerId, PlayerClass.MAGE);
        assertEquals(PlayerClass.MAGE, cache.get(playerId).playerClass());
    }

    @Test
    @DisplayName("просроченная запись не возвращается после истечения TTL")
    void expiredEntryIsNotReturned() {
        AtomicLong now = new AtomicLong(1_000L);
        ClassCache cache = new ClassCache(now::get);
        UUID playerId = UUID.randomUUID();

        cache.put(playerId, PlayerClass.PRIEST);
        now.addAndGet(5_000L);

        assertNull(cache.get(playerId));
    }
}
