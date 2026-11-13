// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.targeting.infrastructure.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.targeting.domain.TargetIdentity;
import dev.raskol.classes.targeting.domain.TargetSelection;
import dev.raskol.classes.targeting.domain.TargetSelectionSource;

class InMemoryTargetSelectionStoreTest {

    @Test
    void savesAndFindsSelectionByOwner() {
        InMemoryTargetSelectionStore store = new InMemoryTargetSelectionStore();
        TargetSelection selection = selection(UUID.randomUUID(), UUID.randomUUID(), 100L);

        store.save(selection);

        assertEquals(selection, store.findByOwner(selection.ownerId()).orElseThrow());
    }

    @Test
    void replacesOnlyTheOwnersCurrentSelection() {
        InMemoryTargetSelectionStore store = new InMemoryTargetSelectionStore();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        TargetSelection first = selection(firstOwner, UUID.randomUUID(), 100L);
        TargetSelection second = selection(secondOwner, UUID.randomUUID(), 100L);
        TargetSelection replacement = selection(firstOwner, UUID.randomUUID(), 200L);

        store.save(first);
        store.save(second);
        store.save(replacement);

        assertEquals(replacement, store.findByOwner(firstOwner).orElseThrow());
        assertEquals(second, store.findByOwner(secondOwner).orElseThrow());
    }

    @Test
    void removesOnlyRequestedOwnerAndReturnsRemovedSelection() {
        InMemoryTargetSelectionStore store = new InMemoryTargetSelectionStore();
        TargetSelection first = selection(UUID.randomUUID(), UUID.randomUUID(), 100L);
        TargetSelection second = selection(UUID.randomUUID(), UUID.randomUUID(), 100L);
        store.save(first);
        store.save(second);

        assertEquals(first, store.remove(first.ownerId()).orElseThrow());
        assertTrue(store.findByOwner(first.ownerId()).isEmpty());
        assertEquals(second, store.findByOwner(second.ownerId()).orElseThrow());
    }

    @Test
    void clearRemovesAllSelections() {
        InMemoryTargetSelectionStore store = new InMemoryTargetSelectionStore();
        TargetSelection selection = selection(UUID.randomUUID(), UUID.randomUUID(), 100L);
        store.save(selection);

        store.clear();

        assertTrue(store.findByOwner(selection.ownerId()).isEmpty());
    }

    @Test
    void rejectsNullArguments() {
        InMemoryTargetSelectionStore store = new InMemoryTargetSelectionStore();

        assertThrows(NullPointerException.class, () -> store.findByOwner(null));
        assertThrows(NullPointerException.class, () -> store.save(null));
        assertThrows(NullPointerException.class, () -> store.remove(null));
    }

    @Test
    void removingMissingSelectionIsSafe() {
        InMemoryTargetSelectionStore store = new InMemoryTargetSelectionStore();

        assertFalse(store.remove(UUID.randomUUID()).isPresent());
    }

    private static TargetSelection selection(UUID ownerId, UUID targetId, long selectedAtMillis) {
        return new TargetSelection(
                ownerId,
                new TargetIdentity(targetId),
                selectedAtMillis,
                TargetSelectionSource.ENTITY_RIGHT_CLICK);
    }
}
