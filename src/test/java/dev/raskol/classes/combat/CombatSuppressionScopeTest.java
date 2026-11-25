// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CombatSuppressionScopeTest {

    @AfterEach
    @SuppressWarnings("deprecation")
    void clearSuppressionContext() {
        while (CombatService.isSuppressed()) {
            CombatService.endSuppress();
        }
        CombatService.setSuppress(false);
    }

    @Test
    void suppressionRemainsVisibleToMultipleListeners() {
        CombatService.beginSuppress();

        assertTrue(CombatService.isSuppressed());
        assertTrue(CombatService.isSuppressed());
        assertTrue(CombatService.isSuppressed());
    }

    @Test
    void nestedDamageDoesNotClearOuterSuppression() {
        CombatService.beginSuppress();
        CombatService.beginSuppress();

        assertTrue(CombatService.isSuppressed());

        CombatService.endSuppress();

        assertTrue(CombatService.isSuppressed());

        CombatService.endSuppress();

        assertFalse(CombatService.isSuppressed());
    }

    @Test
    void suppressionIsRemovedAfterFinalScope() {
        CombatService.beginSuppress();

        CombatService.endSuppress();

        assertFalse(CombatService.isSuppressed());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacySuppressMarkerRetainsConsumptiveSemantics() {
        CombatService.setSuppress(true);

        assertTrue(CombatService.consumeSuppress());
        assertFalse(CombatService.consumeSuppress());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacySetFalseClearsPendingMarker() {
        CombatService.setSuppress(true);
        CombatService.setSuppress(false);

        assertFalse(CombatService.consumeSuppress());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyMarkerDoesNotMutateDepthBasedScope() {
        CombatService.beginSuppress();
        CombatService.setSuppress(true);

        assertTrue(CombatService.isSuppressed());
        assertTrue(CombatService.consumeSuppress());
        assertTrue(CombatService.isSuppressed());

        CombatService.endSuppress();

        assertFalse(CombatService.isSuppressed());
        assertFalse(CombatService.consumeSuppress());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacySuppressMarkerIsThreadLocal() throws InterruptedException {
        CombatService.setSuppress(true);
        boolean[] workerObserved = {true};

        Thread worker = new Thread(() -> workerObserved[0] = CombatService.consumeSuppress());
        worker.start();
        worker.join();

        assertFalse(workerObserved[0]);
        assertTrue(CombatService.consumeSuppress());
        assertFalse(CombatService.consumeSuppress());
    }

    @Test
    void suppressionIsThreadLocal() throws InterruptedException {
        CombatService.beginSuppress();
        boolean[] workerObserved = {true};

        Thread worker = new Thread(() -> workerObserved[0] = CombatService.isSuppressed());
        worker.start();
        worker.join();

        assertFalse(workerObserved[0]);
        assertTrue(CombatService.isSuppressed());
    }
}
