// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class CCFeedbackRuntimeOwnerTest {
    @Test
    void replaceCancelsOldTaskAndRemovesOldResourcesBeforePublishingNewOnes() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(scheduler);
        UUID target = UUID.randomUUID();

        owner.replace(target, List.of(resource(calls, "old")), 20L);
        owner.replace(target, List.of(resource(calls, "new")), 40L);

        assertEquals(List.of("schedule:20", "cancel", "remove:old", "schedule:40"), calls);
        assertEquals(1, owner.trackedTargetCount());
        assertEquals(1, owner.trackedResourceCount());
    }

    @Test
    void explicitClearCancelsTaskBeforeRemovingResources() {
        List<String> calls = new ArrayList<>();
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(new FakeScheduler(calls));
        UUID target = UUID.randomUUID();
        owner.replace(target, List.of(resource(calls, "display")), 10L);

        owner.clear(target);

        assertEquals(List.of("schedule:10", "cancel", "remove:display"), calls);
        assertEquals(0, owner.trackedTargetCount());
    }

    @Test
    void expirationRemovesOnlyItsOwnEntry() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(scheduler);
        UUID target = UUID.randomUUID();

        owner.replace(target, List.of(resource(calls, "first")), 10L);
        Runnable oldCleanup = scheduler.callbacks.get(0);
        owner.replace(target, List.of(resource(calls, "second")), 20L);
        oldCleanup.run();

        assertTrue(calls.contains("remove:first"));
        assertFalse(calls.contains("remove:second"));
        assertEquals(1, owner.trackedTargetCount());
    }

    @Test
    void schedulingFailureRollsBackResources() {
        List<String> calls = new ArrayList<>();
        CCFeedbackScheduler scheduler = (cleanup, delay) -> {
            calls.add("schedule");
            throw new IllegalStateException("schedule failed");
        };
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(scheduler);

        assertThrows(IllegalStateException.class,
                () -> owner.replace(UUID.randomUUID(), List.of(resource(calls, "display")), 5L));

        assertEquals(List.of("schedule", "remove:display"), calls);
        assertEquals(0, owner.trackedTargetCount());
    }

    @Test
    void failedResourceRemovalRemainsTrackedForRetry() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        CCFeedbackResource resource = () -> {
            calls.add("remove");
            if (failOnce[0]) {
                failOnce[0] = false;
                throw new IllegalStateException("remove failed");
            }
        };
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(new FakeScheduler(calls));
        UUID target = UUID.randomUUID();
        owner.replace(target, List.of(resource), 8L);

        assertThrows(IllegalStateException.class, () -> owner.clear(target));
        assertEquals(1, owner.trackedTargetCount());
        assertEquals(1, owner.trackedResourceCount());

        owner.clear(target);
        assertEquals(0, owner.trackedTargetCount());
        assertEquals(2, count(calls, "remove"));
    }

    @Test
    void closeAttemptsEveryTargetAndCanBeRetried() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(new FakeScheduler(calls));
        owner.replace(UUID.randomUUID(), List.of(() -> {
            calls.add("first");
            if (failOnce[0]) {
                failOnce[0] = false;
                throw new IllegalStateException("first failed");
            }
        }), 5L);
        owner.replace(UUID.randomUUID(), List.of(resource(calls, "second")), 5L);

        assertThrows(IllegalStateException.class, owner::close);
        assertTrue(owner.isClosed());
        assertTrue(owner.trackedTargetCount() >= 1);
        owner.close();
        assertEquals(0, owner.trackedTargetCount());
        assertTrue(calls.contains("second"));
    }

    @Test
    void nonPositiveDelayIsClampedToOneTick() {
        List<String> calls = new ArrayList<>();
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(new FakeScheduler(calls));
        owner.replace(UUID.randomUUID(), List.of(resource(calls, "display")), 0L);
        assertEquals(List.of("schedule:1"), calls);
    }

    @Test
    void closedOwnerRejectsNewResources() {
        CCFeedbackRuntimeOwner owner = new CCFeedbackRuntimeOwner(new FakeScheduler(new ArrayList<>()));
        owner.close();
        assertThrows(IllegalStateException.class,
                () -> owner.replace(UUID.randomUUID(), List.of(resource(new ArrayList<>(), "display")), 1L));
    }

    private static CCFeedbackResource resource(List<String> calls, String name) {
        return () -> calls.add("remove:" + name);
    }

    private static int count(List<String> calls, String value) {
        return (int) calls.stream().filter(value::equals).count();
    }

    private static final class FakeScheduler implements CCFeedbackScheduler {
        private final List<String> calls;
        private final List<Runnable> callbacks = new ArrayList<>();

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public TaskHandle schedule(Runnable cleanup, long delayTicks) {
            calls.add("schedule:" + delayTicks);
            callbacks.add(cleanup);
            return new TaskHandle() {
                private boolean cancelled;

                @Override
                public void cancel() {
                    calls.add("cancel");
                    cancelled = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };
        }
    }
}
