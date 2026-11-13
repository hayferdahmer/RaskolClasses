// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class PetRuntimeOwnerTest {

    @Test
    void startRegistersListenerAndSchedulesBothTasksOnce() {
        List<String> calls = new ArrayList<>();
        FakeTaskScheduler scheduler = new FakeTaskScheduler(calls);
        PetRuntimeOwner owner = owner(calls, scheduler);

        owner.start();
        owner.start();

        assertEquals(List.of("register", "tick", "orphan"), calls);
        assertTrue(owner.isRunning());
        assertEquals(2, owner.ownedTaskCount());
    }

    @Test
    void secondTaskFailureCancelsFirstTaskAndUnregistersListener() {
        List<String> calls = new ArrayList<>();
        FakeTaskScheduler scheduler = new FakeTaskScheduler(calls);
        scheduler.failOrphan = true;
        PetRuntimeOwner owner = owner(calls, scheduler);

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "tick", "orphan", "cancel-tick", "unregister"), calls);
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void stopDespawnsBeforeCancellingTasksAndUnregisteringListener() {
        List<String> calls = new ArrayList<>();
        PetRuntimeOwner owner = owner(calls, new FakeTaskScheduler(calls));

        owner.start();
        owner.stop();

        assertEquals(
                List.of(
                        "register",
                        "tick",
                        "orphan",
                        "despawn",
                        "cancel-tick",
                        "cancel-orphan",
                        "unregister"),
                calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void repeatedStopAndCloseAreSafe() {
        List<String> calls = new ArrayList<>();
        PetRuntimeOwner owner = owner(calls, new FakeTaskScheduler(calls));

        owner.start();
        owner.stop();
        owner.stop();
        owner.close();

        assertEquals(1, count(calls, "despawn"));
        assertEquals(1, count(calls, "unregister"));
        assertEquals(1, count(calls, "cancel-tick"));
        assertEquals(1, count(calls, "cancel-orphan"));
    }

    @Test
    void cancellationFailureDoesNotSkipListenerUnregistrationAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeTaskScheduler scheduler = new FakeTaskScheduler(calls);
        scheduler.tickTask.failFirstCancellation = true;
        PetRuntimeOwner owner = owner(calls, scheduler);

        owner.start();
        assertThrows(IllegalStateException.class, owner::stop);

        assertTrue(calls.indexOf("unregister") > calls.indexOf("cancel-orphan"));
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.stop();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void failedDespawnDoesNotPreventTaskCancellationOrListenerUnregistration() {
        List<String> calls = new ArrayList<>();
        PetRuntimeOwner owner = new PetRuntimeOwner(
                registration(calls),
                new FakeTaskScheduler(calls),
                () -> {
                    calls.add("despawn");
                    throw new IllegalStateException("despawn failed");
                });

        owner.start();

        assertThrows(IllegalStateException.class, owner::stop);

        assertTrue(calls.contains("cancel-tick"));
        assertTrue(calls.contains("cancel-orphan"));
        assertTrue(calls.contains("unregister"));
        assertFalse(owner.isClosed());
    }

    @Test
    void closeBeforeStartIsSafeAndPreventsLaterStart() {
        List<String> calls = new ArrayList<>();
        PetRuntimeOwner owner = owner(calls, new FakeTaskScheduler(calls));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of(), calls);
    }

    @Test
    void registrationFailureAttemptsUnregistration() {
        List<String> calls = new ArrayList<>();
        PetRuntimeRegistration registration = new PetRuntimeRegistration() {
            @Override
            public void register() {
                calls.add("register");
                throw new IllegalStateException("registration failed");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
            }
        };
        PetRuntimeOwner owner = new PetRuntimeOwner(
                registration, new FakeTaskScheduler(calls), () -> calls.add("despawn"));

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "unregister"), calls);
    }

    private static PetRuntimeOwner owner(List<String> calls, FakeTaskScheduler scheduler) {
        return new PetRuntimeOwner(registration(calls), scheduler, () -> calls.add("despawn"));
    }

    private static PetRuntimeRegistration registration(List<String> calls) {
        return new PetRuntimeRegistration() {
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

    private static final class FakeTaskScheduler implements PetTaskScheduler {

        private final List<String> calls;
        private final FakeTask tickTask;
        private final FakeTask orphanTask;
        private boolean failOrphan;

        private FakeTaskScheduler(List<String> calls) {
            this.calls = calls;
            this.tickTask = new FakeTask("tick", "cancel-tick", calls);
            this.orphanTask = new FakeTask("orphan", "cancel-orphan", calls);
        }

        @Override
        public TaskHandle scheduleTickTask() {
            calls.add("tick");
            return tickTask;
        }

        @Override
        public TaskHandle scheduleOrphanScan() {
            calls.add("orphan");
            if (failOrphan) {
                throw new IllegalStateException("orphan scheduling failed");
            }
            return orphanTask;
        }
    }

    private static final class FakeTask implements TaskHandle {

        private final String name;
        private final String cancellationCall;
        private final List<String> calls;
        private boolean cancelled;
        private boolean failFirstCancellation;

        private FakeTask(String name, String cancellationCall, List<String> calls) {
            this.name = name;
            this.cancellationCall = cancellationCall;
            this.calls = calls;
        }

        @Override
        public void cancel() {
            calls.add(cancellationCall);
            if (failFirstCancellation) {
                failFirstCancellation = false;
                throw new IllegalStateException(name + " cancellation failed");
            }
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
