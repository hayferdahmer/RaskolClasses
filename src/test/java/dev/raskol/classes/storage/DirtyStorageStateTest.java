// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Проверки повторной записи после отказа хранилища. */
@DisplayName("DirtyStorageState: повторная запись после ошибки")
class DirtyStorageStateTest {

    @Test
    @DisplayName("ошибка записи сохраняет dirty-флаг и позволяет повторить flush")
    void failedWriteRetainsDirtyStateForRetry() {
        DirtyStorageState state = new DirtyStorageState();
        AtomicInteger attempts = new AtomicInteger();
        state.markDirty();

        assertFalse(state.flush(() -> {
            attempts.incrementAndGet();
            return false;
        }));
        assertTrue(state.isDirty());

        assertTrue(state.flush(() -> {
            attempts.incrementAndGet();
            return true;
        }));
        assertFalse(state.isDirty());
        assertEquals(2, attempts.get());
    }

    @Test
    @DisplayName("UUID dirty-игроков сохраняются до успешного flush")
    void failedWriteRetainsDirtyPlayersForRetry() {
        DirtyStorageState state = new DirtyStorageState();
        UUID playerId = UUID.randomUUID();
        state.markDirty(playerId);

        assertFalse(state.flush(() -> false));
        assertTrue(state.containsPlayer(playerId));
        assertEquals(1, state.dirtyPlayerCount());

        assertTrue(state.flush(() -> true));
        assertFalse(state.containsPlayer(playerId));
        assertEquals(0, state.dirtyPlayerCount());
    }

    @Test
    @DisplayName("чистое хранилище не вызывает writer")
    void cleanStateDoesNotWrite() {
        DirtyStorageState state = new DirtyStorageState();

        assertFalse(state.flush(() -> {
            throw new AssertionError("writer must not be called");
        }));
    }
}
