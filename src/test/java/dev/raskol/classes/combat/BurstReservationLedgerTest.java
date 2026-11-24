// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BurstReservationLedgerTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void rollbackTargetsItsOwnReservationInsteadOfTheLatestEntry() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation first = ledger.reserve(100L, 3_000L, 100.0, 40.0);
        BurstReservationLedger.Allocation second = ledger.reserve(110L, 3_000L, 100.0, 30.0);

        ledger.rollback(first.token(), 40.0);

        BurstReservationLedger.Allocation next = ledger.reserve(120L, 3_000L, 100.0, 70.0);
        assertEquals(70.0, next.allowed(), EPSILON);
        assertNotNull(second.token());
    }

    @Test
    void partialRollbackCanSettlePhysicalAndMagicChannelsIndependently() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 60.0);

        ledger.rollback(allocation.token(), 20.0);
        BurstReservationLedger.Allocation afterPhysical = ledger.reserve(110L, 3_000L, 100.0, 60.0);
        assertEquals(60.0, afterPhysical.allowed(), EPSILON);

        ledger.rollback(allocation.token(), 40.0);
        ledger.rollback(allocation.token(), 40.0);
        BurstReservationLedger.Allocation afterMagic = ledger.reserve(120L, 3_000L, 100.0, 100.0);
        assertEquals(100.0, afterMagic.allowed(), EPSILON);
    }

    @Test
    void reservationIsLimitedByRemainingWindowBudget() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        assertEquals(70.0, ledger.reserve(100L, 3_000L, 100.0, 70.0).allowed(), EPSILON);
        assertEquals(30.0, ledger.reserve(110L, 3_000L, 100.0, 50.0).allowed(), EPSILON);
        assertEquals(0.0, ledger.reserve(120L, 3_000L, 100.0, 10.0).allowed(), EPSILON);
    }

    @Test
    void expiredReservationsDoNotConsumeNewWindowBudget() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation expired = ledger.reserve(100L, 1_000L, 100.0, 80.0);

        BurstReservationLedger.Allocation current = ledger.reserve(1_101L, 1_000L, 100.0, 100.0);

        assertEquals(100.0, current.allowed(), EPSILON);
        assertNull(expired.token() == current.token() ? current.token() : null);
    }

    @Test
    void invalidRequestedAmountDoesNotCreateReservation() {
        BurstReservationLedger ledger = new BurstReservationLedger();

        assertEquals(0.0, ledger.reserve(100L, 3_000L, 100.0, Double.NaN).allowed(), EPSILON);
        assertNull(ledger.reserve(110L, 3_000L, 100.0, 0.0).token());
    }
}
