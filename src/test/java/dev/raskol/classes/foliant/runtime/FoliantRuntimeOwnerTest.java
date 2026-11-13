// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.foliant.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class FoliantRuntimeOwnerTest {

    @Test
    void startRegistersListenerAndSchedulesFlushOnce() {
        List<String> calls = new ArrayList<>();
        FoliantRuntimeOwner owner = owner(calls, new FakeTask(calls));

        owner.start();
        owner.start();

        assertEquals(List.of("register", "schedule"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void closeCancelsFlushBeforeUnregisteringListener() {
        List<String> calls = new ArrayList<>();
        FoliantRuntimeOwner owner = owner(calls, new FakeTask(calls));

        owner.start();
        owner.close();

        assertEquals(List.of("register", "schedule", "cancel", "unregister"), calls);
        assertTrue(owner.isClosed());
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void repeatedCloseDoesNotRepeatSuccessfulCleanup() {
        List<String> calls = new ArrayList<>();
        FoliantRuntimeOwner owner = owner(calls, new FakeTask(calls));

        owner.start();
        owner.close();
        owner.close();

        assertEquals(1, count(calls, "cancel"));
        assertEquals(1, count(calls, "unregister"));
    }

    @Test
    void schedulingFailureRollsBackListenerRegistration() {
        List<String> calls = new ArrayList<>();
        FoliantRuntimeOwner owner = new FoliantRuntimeOwner(registration(calls), period -> {
            calls.add("schedule");
            throw new IllegalStateException("schedule failed");
        }, 1200L);

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("register", "schedule", "unregister"), calls);
        assertFalse(owner.isRunning());
    }

    @Test
    void cancellationFailureDoesNotSkipUnregistrationAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeTask task = new FakeTask(calls);
        task.failCancellationOnce = true;
        FoliantRuntimeOwner owner = owner(calls, task);

        owner.start();
        assertThrows(IllegalStateException.class, owner::close);

        assertEquals(List.of("register", "schedule", "cancel", "unregister"), calls);
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.close();

        assertEquals(2, count(calls, "cancel"));
        assertEquals(1, count(calls, "unregister"));
        assertTrue(owner.isClosed());
    }

    @Test
    void unregisterFailureCanBeRetriedWithoutRepeatingSuccessfulCancellation() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        FoliantRuntimeRegistration registration = new FoliantRuntimeRegistration() {
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
        FoliantRuntimeOwner owner = new FoliantRuntimeOwner(registration, period -> {
            calls.add("schedule");
            return new FakeTask(calls);
        }, 1200L);

        owner.start();
        assertThrows(IllegalStateException.class, owner::close);
        owner.close();

        assertEquals(1, count(calls, "cancel"));
        assertEquals(2, count(calls, "unregister"));
        assertTrue(owner.isClosed());
    }

    @Test
    void registrationFailureAttemptsRollback() {
        List<String> calls = new ArrayList<>();
        FoliantRuntimeRegistration registration = new FoliantRuntimeRegistration() {
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
        FoliantRuntimeOwner owner = new FoliantRuntimeOwner(registration, period -> {
            calls.add("schedule");
            return new FakeTask(calls);
        }, 1200L);

        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of("register", "unregister"), calls);
    }

    @Test
    void closeBeforeStartIsSafe() {
        List<String> calls = new ArrayList<>();
        FoliantRuntimeOwner owner = owner(calls, new FakeTask(calls));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of(), calls);
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FoliantRuntimeOwner(registration(new ArrayList<>()), period -> null, 0));
    }

    private static FoliantRuntimeOwner owner(List<String> calls, FakeTask task) {
        return new FoliantRuntimeOwner(registration(calls), period -> {
            calls.add("schedule");
            return task;
        }, 1200L);
    }

    private static FoliantRuntimeRegistration registration(List<String> calls) {
        return new FoliantRuntimeRegistration() {
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

    private static final class FakeTask implements TaskHandle {

        private final List<String> calls;
        private boolean cancelled;
        private boolean failCancellationOnce;

        private FakeTask(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void cancel() {
            calls.add("cancel");
            if (failCancellationOnce) {
                failCancellationOnce = false;
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
