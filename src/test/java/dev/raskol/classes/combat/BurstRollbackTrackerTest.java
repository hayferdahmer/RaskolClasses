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
        tracker.arm(outerEvent, playerId, token, 12.5, 1.0, 0L);

        assertNull(tracker.take(nestedEvent));

        BurstRollbackTracker.Handle rollback = tracker.take(outerEvent);
        assertEquals(playerId, rollback.playerId());
        assertEquals(12.5, rollback.reservedAmount());
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
        tracker.arm(firstEvent, firstPlayer, firstToken, 4.0, 1.0, 0L);
        tracker.arm(secondEvent, secondPlayer, secondToken, 9.0, 1.0, 0L);

        assertEquals(firstPlayer, tracker.take(firstEvent).playerId());
        assertEquals(secondPlayer, tracker.take(secondEvent).playerId());
    }

    @Test
    void rejectsInvalidRollbackData() {
        BurstRollbackTracker tracker = new BurstRollbackTracker();

        BurstReservationLedger.Token token =
                new BurstReservationLedger().reserve(0L, 10_000L, 100.0, 1.0).token();
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(null, UUID.randomUUID(), token, 1.0, 1.0, 0L));
        assertThrows(IllegalArgumentException.class, () -> tracker.arm(new Object(), null, token, 1.0, 1.0, 0L));
        assertThrows(
                IllegalArgumentException.class, () -> tracker.arm(new Object(), UUID.randomUUID(), null, 1.0, 1.0, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.arm(new Object(), UUID.randomUUID(), token, Double.NaN, 1.0, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.arm(new Object(), UUID.randomUUID(), token, 0.0, 1.0, 0L));
    }

    @Test
    void handleCanBeCompletedOnlyOnce() {
        BurstReservationLedger.Token token =
                new BurstReservationLedger().reserve(0L, 10_000L, 100.0, 5.0).token();
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object event = new Object();
        tracker.arm(event, UUID.randomUUID(), token, 5.0, 2.0, 100L);

        BurstRollbackTracker.Handle handle = tracker.take(event);
        assertEquals(BurstRollbackTracker.State.OPEN, handle.state());
        assertEquals(true, handle.finish(BurstRollbackTracker.State.SETTLED));
        assertEquals(false, handle.finish(BurstRollbackTracker.State.ROLLED_BACK));
        assertEquals(BurstRollbackTracker.State.SETTLED, handle.state());
        assertNull(tracker.take(event));
    }

    @Test
    void expiredHandlesAreRemovedAndClosedForRollback() {
        BurstReservationLedger.Token token =
                new BurstReservationLedger().reserve(0L, 10_000L, 100.0, 5.0).token();
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object event = new Object();
        tracker.arm(event, UUID.randomUUID(), token, 5.0, 1.0, 100L);

        var expired = tracker.expire(30_100L, 30_000L);

        assertEquals(1, expired.size());
        assertEquals(BurstRollbackTracker.State.ROLLED_BACK, expired.getFirst().state());
        assertEquals(0, tracker.size());
        assertEquals(0, tracker.expire(60_000L, 30_000L).size());
    }

    @Test
    void rejectsDuplicateEventHandleAndInvalidScale() {
        BurstReservationLedger.Token token =
                new BurstReservationLedger().reserve(0L, 10_000L, 100.0, 5.0).token();
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object event = new Object();

        tracker.arm(event, UUID.randomUUID(), token, 5.0, 1.0, 0L);
        assertThrows(IllegalStateException.class, () -> tracker.arm(event, UUID.randomUUID(), token, 5.0, 1.0, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.arm(new Object(), UUID.randomUUID(), token, 5.0, Double.NaN, 1L));
    }

    @Test
    void cancelledEventRollsBackOnlyItsOwnReservationToken() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation cancelled = ledger.reserve(100L, 3_000L, 100.0, 40.0);
        ledger.reserve(110L, 3_000L, 100.0, 20.0);
        BurstRollbackTracker tracker = new BurstRollbackTracker();
        Object event = new Object();
        tracker.arm(event, UUID.randomUUID(), cancelled.token(), 40.0, 1.0, 100L);

        BurstRollbackTracker.Handle rollback = tracker.take(event);
        ledger.rollback(rollback.token(), rollback.reservedAmount());

        assertEquals(80.0, ledger.reserve(120L, 3_000L, 100.0, 100.0).allowed());
    }
}
