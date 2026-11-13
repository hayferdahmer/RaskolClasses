// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class PlaceholderRuntimeOwnerTest {

    @Test
    void startPublishesInitialSnapshotBeforeSchedulingAndRegistration() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        PlaceholderRuntimeOwner owner = owner(calls, scheduler);

        owner.start();
        owner.start();

        assertEquals(List.of("refresh", "schedule", "register"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void stopCancelsProducerBeforeUnregisteringAndClearingSnapshots() {
        List<String> calls = new ArrayList<>();
        PlaceholderRuntimeOwner owner = owner(calls, new FakeScheduler(calls));

        owner.start();
        owner.stop();

        assertEquals(
                List.of("refresh", "schedule", "register", "cancel", "unregister", "clear"),
                calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void schedulingFailureClearsInitialSnapshotWithoutRegisteringExpansions() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.failSchedule = true;
        PlaceholderRuntimeOwner owner = owner(calls, scheduler);

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("refresh", "schedule", "clear"), calls);
        assertFalse(owner.isRunning());
    }

    @Test
    void registrationFailureCancelsProducerAndClearsSnapshots() {
        List<String> calls = new ArrayList<>();
        PlaceholderRuntimeOwner owner = new PlaceholderRuntimeOwner(
                () -> calls.add("refresh"),
                () -> calls.add("clear"),
                new PlaceholderRuntimeRegistration() {
                    @Override
                    public void register() {
                        calls.add("register");
                        throw new IllegalStateException("registration failed");
                    }

                    @Override
                    public void unregister() {
                        calls.add("unregister");
                    }
                },
                new FakeScheduler(calls));

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("refresh", "schedule", "register", "cancel", "unregister", "clear"), calls);
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void cancellationFailureDoesNotSkipUnregisterOrClearAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.task.failFirstCancellation = true;
        PlaceholderRuntimeOwner owner = owner(calls, scheduler);

        owner.start();
        assertThrows(IllegalStateException.class, owner::stop);

        assertTrue(calls.indexOf("unregister") > calls.indexOf("cancel"));
        assertTrue(calls.contains("clear"));
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.stop();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void unregisterFailureDoesNotSkipSnapshotClearAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeRegistration registration = new FakeRegistration(calls);
        registration.failFirstUnregister = true;
        PlaceholderRuntimeOwner owner = new PlaceholderRuntimeOwner(
                () -> calls.add("refresh"), () -> calls.add("clear"), registration, new FakeScheduler(calls));

        owner.start();
        assertThrows(IllegalStateException.class, owner::stop);

        assertTrue(calls.contains("clear"));
        assertFalse(owner.isClosed());

        owner.stop();

        assertTrue(owner.isClosed());
        assertEquals(2, count(calls, "unregister"));
    }

    @Test
    void closeBeforeStartIsSafeAndPreventsLaterStart() {
        List<String> calls = new ArrayList<>();
        PlaceholderRuntimeOwner owner = owner(calls, new FakeScheduler(calls));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of("clear"), calls);
    }

    @Test
    void refreshFailureDoesNotScheduleOrRegisterAndClearsSnapshots() {
        List<String> calls = new ArrayList<>();
        PlaceholderRuntimeOwner owner = new PlaceholderRuntimeOwner(
                () -> {
                    calls.add("refresh");
                    throw new IllegalStateException("refresh failed");
                },
                () -> calls.add("clear"),
                new FakeRegistration(calls),
                new FakeScheduler(calls));

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("refresh", "clear"), calls);
    }

    private static PlaceholderRuntimeOwner owner(List<String> calls, FakeScheduler scheduler) {
        return new PlaceholderRuntimeOwner(
                () -> calls.add("refresh"),
                () -> calls.add("clear"),
                new FakeRegistration(calls),
                scheduler);
    }

    private static int count(List<String> values, String expected) {
        return (int) values.stream().filter(expected::equals).count();
    }

    private static final class FakeRegistration implements PlaceholderRuntimeRegistration {

        private final List<String> calls;
        private boolean failFirstUnregister;

        private FakeRegistration(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void register() {
            calls.add("register");
        }

        @Override
        public void unregister() {
            calls.add("unregister");
            if (failFirstUnregister) {
                failFirstUnregister = false;
                throw new IllegalStateException("unregister failed");
            }
        }
    }

    private static final class FakeScheduler implements PlaceholderRuntimeScheduler {

        private final List<String> calls;
        private final FakeTask task;
        private boolean failSchedule;

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
            this.task = new FakeTask(calls);
        }

        @Override
        public TaskHandle scheduleRefresh() {
            calls.add("schedule");
            if (failSchedule) {
                throw new IllegalStateException("schedule failed");
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
                throw new IllegalStateException("cancel failed");
            }
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
