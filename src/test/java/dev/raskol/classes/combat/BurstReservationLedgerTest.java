// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void partialRollbackRemainsAvailableForAddressedReservations() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 60.0);

        ledger.rollback(allocation.token(), 20.0);
        BurstReservationLedger.Allocation afterPhysical = ledger.reserve(110L, 3_000L, 100.0, 60.0);
        assertEquals(60.0, afterPhysical.allowed(), EPSILON);
        ledger.rollback(afterPhysical.token(), 60.0);

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
        assertNotSame(expired.token(), current.token());
    }

    @Test
    void invalidRequestedAmountDoesNotCreateReservation() {
        BurstReservationLedger ledger = new BurstReservationLedger();

        assertEquals(0.0, ledger.reserve(100L, 3_000L, 100.0, Double.NaN).allowed(), EPSILON);
        assertNull(ledger.reserve(110L, 3_000L, 100.0, 0.0).token());
    }

    @Test
    void reconcileRaisesReservationWhenActualDamageExceedsInitialReserve() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 40.0);

        BurstReservationLedger.Settlement settlement = ledger.settle(allocation.token(), 65.0);

        assertEquals(40.0, settlement.previousAmount(), EPSILON);
        assertEquals(65.0, settlement.finalAmount(), EPSILON);
        assertEquals(25.0, settlement.overage(), EPSILON);
        assertEquals(35.0, ledger.reserve(110L, 3_000L, 100.0, 70.0).allowed(), EPSILON);
    }

    @Test
    void reconcileReleasesUnusedReservationWhenActualDamageIsLower() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 60.0);

        ledger.settle(allocation.token(), 25.0);

        assertEquals(75.0, ledger.reserve(110L, 3_000L, 100.0, 100.0).allowed(), EPSILON);
    }

    @Test
    void reconcileRemovesReservationWhenNoDamageWasApplied() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 60.0);

        ledger.settle(allocation.token(), 0.0);

        assertEquals(100.0, ledger.reserve(110L, 3_000L, 100.0, 100.0).allowed(), EPSILON);
    }

    @Test
    void exceptionAfterPartialChannelDamageSettlesHealthDeltaCapturedInFinally() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 60.0);
        Object target = new Object();
        double[] health = {100.0};
        double[] actualDamage = {0.0};

        assertThrows(IllegalStateException.class, () -> {
            DamageAttribution.Scope attribution = DamageAttribution.begin(target, health[0]);
            try {
                // Paper успел применить часть урона, после чего listener выбросил исключение.
                health[0] -= 22.0;
                throw new IllegalStateException("Synthetic damage listener failure");
            } finally {
                actualDamage[0] = attribution.finish(health[0]);
            }
        });

        BurstReservationLedger.Settlement settlement = ledger.settle(allocation.token(), actualDamage[0]);

        assertEquals(22.0, actualDamage[0], EPSILON);
        assertEquals(60.0, settlement.previousAmount(), EPSILON);
        assertEquals(22.0, settlement.finalAmount(), EPSILON);
        assertEquals(0.0, settlement.overage(), EPSILON);
        assertEquals(78.0, ledger.reserve(110L, 3_000L, 100.0, 100.0).allowed(), EPSILON);
    }

    @Test
    void exceptionBeforeHealthChangesReleasesTheWholeReservation() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 60.0);
        Object target = new Object();
        double healthBefore = 100.0;
        double[] actualDamage = {0.0};

        assertThrows(IllegalStateException.class, () -> {
            DamageAttribution.Scope attribution = DamageAttribution.begin(target, healthBefore);
            try {
                throw new IllegalStateException("Synthetic damage failure before health change");
            } finally {
                actualDamage[0] = attribution.finish(healthBefore);
            }
        });

        ledger.settle(allocation.token(), actualDamage[0]);
        assertEquals(100.0, ledger.reserve(110L, 3_000L, 100.0, 100.0).allowed(), EPSILON);
    }

    @Test
    void reconcileIgnoresInvalidAmountsAndTokensOwnedByAnotherLedger() {
        BurstReservationLedger ledger = new BurstReservationLedger();
        BurstReservationLedger otherLedger = new BurstReservationLedger();
        BurstReservationLedger.Allocation allocation = ledger.reserve(100L, 3_000L, 100.0, 40.0);
        BurstReservationLedger.Allocation foreign = otherLedger.reserve(100L, 3_000L, 100.0, 20.0);

        BurstReservationLedger.Settlement invalid = ledger.settle(allocation.token(), Double.NaN);
        ledger.settle(allocation.token(), Double.POSITIVE_INFINITY);
        ledger.settle(allocation.token(), -1.0);
        BurstReservationLedger.Settlement foreignResult = otherLedger.settle(allocation.token(), 10.0);

        assertTrue(invalid.invalid());
        assertFalse(foreignResult.applied());

        assertEquals(60.0, ledger.reserve(110L, 3_000L, 100.0, 100.0).allowed(), EPSILON);
        assertEquals(80.0, otherLedger.reserve(110L, 3_000L, 100.0, 100.0).allowed(), EPSILON);
    }
}
