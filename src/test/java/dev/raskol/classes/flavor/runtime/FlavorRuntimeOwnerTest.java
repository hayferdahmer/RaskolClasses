// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.flavor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class FlavorRuntimeOwnerTest {

    @Test
    void registrationAndStartAreIdempotent() {
        List<String> calls = new ArrayList<>();
        FakeRegistration registration = new FakeRegistration(calls);
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(registration, new FakeScheduler(calls));

        owner.register();
        owner.register();
        owner.start();
        owner.start();

        assertEquals(List.of("register", "schedule"), calls);
        assertTrue(owner.isRegistered());
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void startRequiresListenerRegistration() {
        List<String> calls = new ArrayList<>();
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(
                new FakeRegistration(calls), new FakeScheduler(calls));

        assertThrows(IllegalStateException.class, owner::start);
        assertTrue(calls.isEmpty());
    }

    @Test
    void closeCancelsAuraBeforeUnregisteringListener() {
        List<String> calls = new ArrayList<>();
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(
                new FakeRegistration(calls), new FakeScheduler(calls));

        owner.register();
        owner.start();
        owner.close();

        assertEquals(List.of("register", "schedule", "cancel", "unregister"), calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void schedulingFailureRollsBackListenerRegistration() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.failSchedule = true;
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(new FakeRegistration(calls), scheduler);

        owner.register();
        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "schedule", "unregister"), calls);
        assertFalse(owner.isRegistered());
        assertFalse(owner.isRunning());
    }

    @Test
    void registrationFailureAttemptsUnregister() {
        List<String> calls = new ArrayList<>();
        FakeRegistration registration = new FakeRegistration(calls);
        registration.failRegister = true;
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(registration, new FakeScheduler(calls));

        assertThrows(IllegalStateException.class, owner::register);

        assertEquals(List.of("register", "unregister"), calls);
        assertFalse(owner.isRegistered());
    }

    @Test
    void cancellationFailureDoesNotSkipUnregisterAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(new FakeRegistration(calls), scheduler);

        owner.register();
        owner.start();
        scheduler.task.failCancelOnce = true;

        assertThrows(IllegalStateException.class, owner::close);

        assertTrue(calls.indexOf("unregister") > calls.indexOf("cancel"));
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.close();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void unregisterFailureCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeRegistration registration = new FakeRegistration(calls);
        registration.failUnregisterOnce = true;
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(registration, new FakeScheduler(calls));

        owner.register();
        owner.start();
        assertThrows(IllegalStateException.class, owner::close);

        assertFalse(owner.isClosed());
        assertTrue(owner.isRegistered());

        owner.close();

        assertTrue(owner.isClosed());
        assertFalse(owner.isRegistered());
        assertEquals(2, count(calls, "unregister"));
    }

    @Test
    void closeBeforeStartIsSafeAndPreventsLaterRegistration() {
        List<String> calls = new ArrayList<>();
        FlavorRuntimeOwner owner = new FlavorRuntimeOwner(
                new FakeRegistration(calls), new FakeScheduler(calls));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::register);
        assertEquals(List.of(), calls);
    }

    private static int count(List<String> calls, String value) {
        return (int) calls.stream().filter(value::equals).count();
    }

    private static final class FakeRegistration implements FlavorRuntimeRegistration {

        private final List<String> calls;
        private boolean failRegister;
        private boolean failUnregisterOnce;

        private FakeRegistration(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void register() {
            calls.add("register");
            if (failRegister) {
                failRegister = false;
                throw new IllegalStateException("register failed");
            }
        }

        @Override
        public void unregister() {
            calls.add("unregister");
            if (failUnregisterOnce) {
                failUnregisterOnce = false;
                throw new IllegalStateException("unregister failed");
            }
        }
    }

    private static final class FakeScheduler implements FlavorTaskScheduler {

        private final List<String> calls;
        private final FakeTask task;
        private boolean failSchedule;

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
            task = new FakeTask(calls);
        }

        @Override
        public TaskHandle scheduleAura() {
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
        private boolean failCancelOnce;

        private FakeTask(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void cancel() {
            calls.add("cancel");
            if (failCancelOnce) {
                failCancelOnce = false;
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
