// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class HpAttributeSyncRuntimeOwnerTest {

    @Test
    void startRegistersListenerAndSchedulesSweepOnce() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        HpAttributeSyncRuntimeOwner owner = owner(calls, scheduler);

        owner.start();
        owner.start();

        assertEquals(List.of("register", "schedule:100"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void stopCancelsSweepBeforeUnregisteringListener() {
        List<String> calls = new ArrayList<>();
        HpAttributeSyncRuntimeOwner owner = owner(calls, new FakeScheduler(calls));

        owner.start();
        owner.stop();

        assertEquals(List.of("register", "schedule:100", "cancel", "unregister"), calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void repeatedStopAndCloseAreSafe() {
        List<String> calls = new ArrayList<>();
        HpAttributeSyncRuntimeOwner owner = owner(calls, new FakeScheduler(calls));

        owner.start();
        owner.close();
        owner.stop();
        owner.close();

        assertEquals(1, count(calls, "cancel"));
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void schedulingFailureRollsBackListenerRegistration() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.failScheduling = true;
        HpAttributeSyncRuntimeOwner owner = owner(calls, scheduler);

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "schedule:100", "unregister"), calls);
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void registrationFailureStillAttemptsUnregistration() {
        List<String> calls = new ArrayList<>();
        HpAttributeSyncRegistration registration = new HpAttributeSyncRegistration() {
            @Override
            public void register() {
                calls.add("register");
                throw new IllegalStateException("partial registration");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
            }
        };
        HpAttributeSyncRuntimeOwner owner =
                new HpAttributeSyncRuntimeOwner(registration, new FakeScheduler(calls), 100L);

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "unregister"), calls);
    }

    @Test
    void cancellationFailureDoesNotSkipUnregistrationAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.task.failFirstCancellation = true;
        HpAttributeSyncRuntimeOwner owner = owner(calls, scheduler);

        owner.start();
        assertThrows(IllegalStateException.class, owner::stop);

        assertTrue(calls.indexOf("unregister") > calls.indexOf("cancel"));
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.stop();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void closeBeforeStartPreventsLaterStart() {
        List<String> calls = new ArrayList<>();
        HpAttributeSyncRuntimeOwner owner = owner(calls, new FakeScheduler(calls));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of(), calls);
    }

    @Test
    void rejectsNonPositivePeriod() {
        List<String> calls = new ArrayList<>();
        assertThrows(
                IllegalArgumentException.class,
                () -> new HpAttributeSyncRuntimeOwner(registration(calls), new FakeScheduler(calls), 0L));
    }

    private static HpAttributeSyncRuntimeOwner owner(List<String> calls, FakeScheduler scheduler) {
        return new HpAttributeSyncRuntimeOwner(registration(calls), scheduler, 100L);
    }

    private static HpAttributeSyncRegistration registration(List<String> calls) {
        return new HpAttributeSyncRegistration() {
            @Override
            public void register() {
                calls.add("register");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
            }
        };
    }

    private static int count(List<String> values, String expected) {
        return (int) values.stream().filter(expected::equals).count();
    }

    private static final class FakeScheduler implements HpAttributeSyncScheduler {

        private final List<String> calls;
        private final FakeTask task = new FakeTask();
        private boolean failScheduling;

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public TaskHandle scheduleSweep(long periodTicks) {
            calls.add("schedule:" + periodTicks);
            if (failScheduling) {
                throw new IllegalStateException("scheduling failed");
            }
            return task;
        }
    }

    private static final class FakeTask implements TaskHandle {

        private boolean cancelled;
        private boolean failFirstCancellation;

        @Override
        public void cancel() {
            callsPlaceholder();
            if (failFirstCancellation) {
                failFirstCancellation = false;
                throw new IllegalStateException("cancellation failed");
            }
            cancelled = true;
        }

        private void callsPlaceholder() {}

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
