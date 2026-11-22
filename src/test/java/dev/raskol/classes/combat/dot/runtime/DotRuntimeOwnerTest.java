// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.dot.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class DotRuntimeOwnerTest {
    @Test
    void startRegistersListenerAndSchedulesProducerOnce() {
        List<String> calls = new ArrayList<>();
        DotRuntimeOwner owner = owner(calls, new FakeTask(calls));
        owner.start();
        owner.start();
        assertEquals(List.of("register", "schedule"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void closeCancelsProducerBeforeUnregisteringListener() {
        List<String> calls = new ArrayList<>();
        DotRuntimeOwner owner = owner(calls, new FakeTask(calls));
        owner.start();
        owner.close();
        assertEquals(List.of("register", "schedule", "cancel", "unregister"), calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void schedulingFailureRollsBackListenerRegistration() {
        List<String> calls = new ArrayList<>();
        DotRuntimeOwner owner = new DotRuntimeOwner(
                registration(calls),
                tick -> {
                    calls.add("schedule");
                    throw new IllegalStateException("schedule failed");
                },
                () -> calls.add("tick"));
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of("register", "schedule", "unregister"), calls);
        assertFalse(owner.isRunning());
    }

    @Test
    void cancellationFailureStillUnregistersAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeTask task = new FakeTask(calls);
        task.failOnce = true;
        DotRuntimeOwner owner = owner(calls, task);
        owner.start();
        assertThrows(IllegalStateException.class, owner::close);
        assertEquals(List.of("register", "schedule", "cancel", "unregister"), calls);
        assertEquals(1, owner.ownedTaskCount());
        assertFalse(owner.isClosed());
        owner.close();
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void unregisterFailureCanBeRetriedWithoutRepeatingCancellation() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        DotRuntimeRegistration registration = new DotRuntimeRegistration() {
            @Override
            public void register() {
                calls.add("register");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
                if (failOnce[0]) {
                    failOnce[0] = false;
                    throw new IllegalStateException("unregister failed");
                }
            }
        };
        DotRuntimeOwner owner = new DotRuntimeOwner(registration, tick -> new FakeTask(calls), () -> {});
        owner.start();
        assertThrows(IllegalStateException.class, owner::close);
        owner.close();
        assertEquals(1, count(calls, "cancel"));
        assertEquals(2, count(calls, "unregister"));
        assertTrue(owner.isClosed());
    }

    @Test
    void registrationFailureAttemptsUnregistration() {
        List<String> calls = new ArrayList<>();
        DotRuntimeRegistration registration = new DotRuntimeRegistration() {
            @Override
            public void register() {
                calls.add("register");
                throw new IllegalStateException("register failed");
            }

            @Override
            public void unregister() {
                calls.add("unregister");
            }
        };
        DotRuntimeOwner owner = new DotRuntimeOwner(registration, tick -> new FakeTask(calls), () -> {});
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of("register", "unregister"), calls);
    }

    @Test
    void closeBeforeStartIsSafeAndPreventsStart() {
        List<String> calls = new ArrayList<>();
        DotRuntimeOwner owner = owner(calls, new FakeTask(calls));
        owner.close();
        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of(), calls);
    }

    private static DotRuntimeOwner owner(List<String> calls, FakeTask task) {
        return new DotRuntimeOwner(
                registration(calls),
                tick -> {
                    calls.add("schedule");
                    return task;
                },
                () -> {});
    }

    private static DotRuntimeRegistration registration(List<String> calls) {
        return new DotRuntimeRegistration() {
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

    private static int count(List<String> calls, String value) {
        return (int) calls.stream().filter(value::equals).count();
    }

    private static final class FakeTask implements TaskHandle {
        private final List<String> calls;
        private boolean cancelled;
        private boolean failOnce;

        private FakeTask(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void cancel() {
            calls.add("cancel");
            if (failOnce) {
                failOnce = false;
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
