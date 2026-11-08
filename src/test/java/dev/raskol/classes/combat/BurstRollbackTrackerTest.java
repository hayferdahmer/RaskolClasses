// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BurstRollbackTrackerTest {

    @Test
    void nestedEventDoesNotClearOuterRollback() {
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object outerEvent = new Object();
        Object nestedEvent = new Object();
        UUID playerId = UUID.randomUUID();

        tracker.arm(outerEvent, playerId, 12.5);

        assertNull(tracker.take(nestedEvent));

        BurstRollbackTracker.Entry rollback = tracker.take(outerEvent);
        assertEquals(playerId, rollback.playerId());
        assertEquals(12.5, rollback.amount());
        assertNull(tracker.take(outerEvent));
    }

    @Test
    void distinctEventsKeepIndependentRollbackEntries() {
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object firstEvent = new Object();
        Object secondEvent = new Object();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        tracker.arm(firstEvent, firstPlayer, 4.0);
        tracker.arm(secondEvent, secondPlayer, 9.0);

        assertEquals(firstPlayer, tracker.take(firstEvent).playerId());
        assertEquals(secondPlayer, tracker.take(secondEvent).playerId());
    }

    @Test
    void rejectsInvalidRollbackData() {
        BurstRollbackTracker tracker = new BurstRollbackTracker();

        assertThrows(IllegalArgumentException.class, () -> tracker.arm(null, UUID.randomUUID(), 1.0));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), UUID.randomUUID(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), UUID.randomUUID(), 0.0));
    }
}
