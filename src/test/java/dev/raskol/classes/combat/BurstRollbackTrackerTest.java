// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        BurstReservationLedger.Token token =
                new BurstReservationLedger().reserve(0L, 10_000L, 100.0, 12.5).token();
        tracker.arm(outerEvent, playerId, token, 12.5);

        assertNull(tracker.take(nestedEvent));

        BurstRollbackTracker.Entry rollback = tracker.take(outerEvent);
        assertEquals(playerId, rollback.playerId());
        assertEquals(12.5, rollback.amount());
        assertSame(token, rollback.token());
        assertNull(tracker.take(outerEvent));
    }

    @Test
    void distinctEventsKeepIndependentRollbackEntries() {
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object firstEvent = new Object();
        Object secondEvent = new Object();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Token firstToken =
                ledger.reserve(0L, 10_000L, 100.0, 4.0).token();
        BurstReservationLedger.Token secondToken =
                ledger.reserve(0L, 10_000L, 100.0, 9.0).token();
        tracker.arm(firstEvent, firstPlayer, firstToken, 4.0);
        tracker.arm(secondEvent, secondPlayer, secondToken, 9.0);

        assertEquals(firstPlayer, tracker.take(firstEvent).playerId());
        assertEquals(secondPlayer, tracker.take(secondEvent).playerId());
    }

    @Test
    void rejectsInvalidRollbackData() {
        BurstRollbackTracker tracker = new BurstRollbackTracker();

        BurstReservationLedger.Token token =
                new BurstReservationLedger().reserve(0L, 10_000L, 100.0, 1.0).token();
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(null, UUID.randomUUID(), token, 1.0));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), null, token, 1.0));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), UUID.randomUUID(), null, 1.0));
        assertThrows(
                IllegalArgumentException.class, () -> tracker.arm(new Object(), UUID.randomUUID(), token, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), UUID.randomUUID(), token, 0.0));
    }

    @Test
    void cancelledEventRollsBackOnlyItsOwnReservationToken() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation cancelled = ledger.reserve(100L, 3_000L, 100.0, 40.0);
        ledger.reserve(110L, 3_000L, 100.0, 20.0);
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object event = new Object();
        tracker.arm(event, UUID.randomUUID(), cancelled.token(), 40.0);

        BurstRollbackTracker.Entry rollback = tracker.take(event);
        ledger.rollback(rollback.token(), rollback.amount());

        assertEquals(80.0, ledger.reserve(120L, 3_000L, 100.0, 100.0).allowed());
    }
}
