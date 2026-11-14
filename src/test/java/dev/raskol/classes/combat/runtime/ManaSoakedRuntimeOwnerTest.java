// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class ManaSoakedRuntimeOwnerTest {

    @Test
    void startSchedulesTickOnce() {
        List<String> calls = new ArrayList<>();
        ManaSoakedRuntimeOwner owner = new ManaSoakedRuntimeOwner(new FakeScheduler(calls), () -> {});

        owner.start();
        owner.start();

        assertEquals(List.of("schedule"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void closeCancelsProducerBeforeClearingTracking() {
        List<String> calls = new ArrayList<>();
        ManaSoakedRuntimeOwner owner = new ManaSoakedRuntimeOwner(new FakeScheduler(calls), () -> calls.add("clear"));

        owner.start();
        owner.close();
        owner.close();

        assertEquals(List.of("schedule", "cancel", "clear"), calls);
        assertTrue(owner.isClosed());
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void schedulingFailurePreventsRestart() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.failScheduling = true;
        ManaSoakedRuntimeOwner owner = new ManaSoakedRuntimeOwner(scheduler, () -> {});

        assertThrows(IllegalStateException.class, owner::start);

        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
        assertThrows(IllegalStateException.class, owner::start);
    }

    @Test
    void cancellationFailureCanBeRetriedWithoutRepeatingTrackingCleanup() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.task.failFirstCancellation = true;
        ManaSoakedRuntimeOwner owner = new ManaSoakedRuntimeOwner(scheduler, () -> calls.add("clear"));
        owner.start();

        assertThrows(IllegalStateException.class, owner::close);

        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());
        assertEquals(1, calls.stream().filter("clear"::equals).count());

        owner.close();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(1, calls.stream().filter("clear"::equals).count());
        assertEquals(List.of("schedule", "cancel", "clear", "cancel"), calls);
    }

    @Test
    void trackingCleanupFailureCanBeRetried() {
        List<String> calls = new ArrayList<>();
        boolean[] fail = {true};
        ManaSoakedRuntimeOwner owner = new ManaSoakedRuntimeOwner(
                new FakeScheduler(calls),
                () -> {
                    calls.add("clear");
                    if (fail[0]) {
                        fail[0] = false;
                        throw new IllegalStateException("cleanup failed");
                    }
                });
        owner.start();

        assertThrows(IllegalStateException.class, owner::close);
        assertFalse(owner.isClosed());

        owner.close();

        assertTrue(owner.isClosed());
        assertEquals(List.of("schedule", "cancel", "clear", "clear"), calls);
    }

    @Test
    void closeBeforeStartClearsTrackingAndPreventsStart() {
        List<String> calls = new ArrayList<>();
        ManaSoakedRuntimeOwner owner = new ManaSoakedRuntimeOwner(new FakeScheduler(calls), () -> calls.add("clear"));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of("clear"), calls);
    }

    private static final class FakeScheduler implements ManaSoakedTaskScheduler {

        private final List<String> calls;
        private final FakeTask task;
        private boolean failScheduling;

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
            this.task = new FakeTask(calls);
        }

        @Override
        public TaskHandle scheduleTick() {
            calls.add("schedule");
            if (failScheduling) {
                throw new IllegalStateException("scheduling failed");
            }
            return task;
        }
    }

    private static final class FakeTask implements TaskHandle {

        private final List<String> calls;
        private boolean cancelled;
        private boolean failFirstCancellation;

        private FakeTask(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void cancel() {
            calls.add("cancel");
            if (failFirstCancellation) {
                failFirstCancellation = false;
                throw new IllegalStateException("cancellation failed");
            }
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
