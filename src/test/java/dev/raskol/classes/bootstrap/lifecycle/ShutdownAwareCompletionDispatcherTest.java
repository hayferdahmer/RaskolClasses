// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

class ShutdownAwareCompletionDispatcherTest {

    private static final Logger LOGGER = Logger.getLogger("ShutdownAwareCompletionDispatcherTest");

    @Test
    void closeSuppressesQueuedCallbacksAndRunsUndeliveredHandlerOnce() {
        List<Runnable> scheduled = new ArrayList<>();
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger undelivered = new AtomicInteger();
        ShutdownAwareCompletionDispatcher dispatcher =
                new ShutdownAwareCompletionDispatcher(LOGGER, scheduled::add);

        assertTrue(dispatcher.dispatch("purchase result", delivered::incrementAndGet, undelivered::incrementAndGet));
        assertEquals(1, dispatcher.pendingCount());

        dispatcher.close();
        dispatcher.close();
        scheduled.forEach(Runnable::run);

        assertEquals(0, delivered.get());
        assertEquals(1, undelivered.get());
        assertEquals(0, dispatcher.pendingCount());
    }

    @Test
    void dispatchAfterCloseIsRejectedAndUndeliveredHandlerRuns() {
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger undelivered = new AtomicInteger();
        ShutdownAwareCompletionDispatcher dispatcher =
                new ShutdownAwareCompletionDispatcher(LOGGER, Runnable::run);
        dispatcher.close();

        assertFalse(dispatcher.dispatch("late result", delivered::incrementAndGet, undelivered::incrementAndGet));

        assertEquals(0, delivered.get());
        assertEquals(1, undelivered.get());
    }

    @Test
    void schedulerRejectionIsLoggedAndRunsUndeliveredHandler() {
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger undelivered = new AtomicInteger();
        ShutdownAwareCompletionDispatcher dispatcher = new ShutdownAwareCompletionDispatcher(
                LOGGER,
                task -> {
                    throw new IllegalStateException("scheduler stopped");
                });

        assertFalse(dispatcher.dispatch("export result", delivered::incrementAndGet, undelivered::incrementAndGet));

        assertEquals(0, delivered.get());
        assertEquals(1, undelivered.get());
        assertEquals(0, dispatcher.pendingCount());
    }

    @Test
    void acceptedCallbackRunsOnceAndLeavesNoPendingEntry() {
        List<Runnable> scheduled = new ArrayList<>();
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger undelivered = new AtomicInteger();
        ShutdownAwareCompletionDispatcher dispatcher =
                new ShutdownAwareCompletionDispatcher(LOGGER, scheduled::add);

        assertTrue(dispatcher.dispatch("grant result", delivered::incrementAndGet, undelivered::incrementAndGet));
        scheduled.getFirst().run();
        scheduled.getFirst().run();
        dispatcher.close();

        assertEquals(1, delivered.get());
        assertEquals(0, undelivered.get());
        assertEquals(0, dispatcher.pendingCount());
    }
}
