// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class BossBarRuntimeOwnerTest {

    @Test
    void startIsIdempotentAndUsesConfiguredPeriod() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        BossBarRuntimeOwner owner = new BossBarRuntimeOwner(scheduler, () -> 7L, () -> calls.add("clear"));

        owner.start();
        owner.start();

        assertEquals(List.of("schedule:7"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void restartCancelsOldTaskClearsBarsAndSchedulesNewPeriod() {
        List<String> calls = new ArrayList<>();
        AtomicLong period = new AtomicLong(5L);
        FakeScheduler scheduler = new FakeScheduler(calls);
        BossBarRuntimeOwner owner = new BossBarRuntimeOwner(scheduler, period::get, () -> calls.add("clear"));

        owner.start();
        period.set(9L);
        owner.restart();

        assertEquals(List.of("schedule:5", "cancel", "clear", "schedule:9"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void closeCancelsTaskBeforeClearingBars() {
        List<String> calls = new ArrayList<>();
        BossBarRuntimeOwner owner =
                new BossBarRuntimeOwner(new FakeScheduler(calls), () -> 1L, () -> calls.add("clear"));

        owner.start();
        owner.close();

        assertEquals(List.of("schedule:1", "cancel", "clear"), calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void schedulingFailureAttemptsTaskScopeCleanup() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.failSchedule = true;
        BossBarRuntimeOwner owner = new BossBarRuntimeOwner(scheduler, () -> 2L, () -> calls.add("clear"));

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(List.of("schedule:2"), calls);
        assertFalse(owner.isRunning());
    }

    @Test
    void closeAttemptsClearEvenWhenTaskCancellationFailsAndCanRetry() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        BossBarRuntimeOwner owner = new BossBarRuntimeOwner(scheduler, () -> 2L, () -> calls.add("clear"));

        owner.start();
        scheduler.task.failCancelOnce = true;

        assertThrows(IllegalStateException.class, owner::close);
        assertEquals(List.of("schedule:2", "cancel", "clear"), calls);
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.close();

        assertEquals(List.of("schedule:2", "cancel", "clear", "cancel", "clear"), calls);
        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void closeCanRetryWhenBarCleanupFails() {
        List<String> calls = new ArrayList<>();
        boolean[] failOnce = {true};
        BossBarRuntimeOwner owner = new BossBarRuntimeOwner(new FakeScheduler(calls), () -> 2L, () -> {
            calls.add("clear");
            if (failOnce[0]) {
                failOnce[0] = false;
                throw new IllegalStateException("clear failed");
            }
        });

        owner.start();
        assertThrows(IllegalStateException.class, owner::close);
        assertFalse(owner.isClosed());
        owner.close();

        assertTrue(owner.isClosed());
        assertEquals(2, count(calls, "clear"));
    }

    @Test
    void closeBeforeStartIsSafeAndPreventsLaterStart() {
        List<String> calls = new ArrayList<>();
        BossBarRuntimeOwner owner =
                new BossBarRuntimeOwner(new FakeScheduler(calls), () -> 2L, () -> calls.add("clear"));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of("clear"), calls);
    }

    @Test
    void configuredPeriodIsClampedToOneTick() {
        List<String> calls = new ArrayList<>();
        BossBarRuntimeOwner owner =
                new BossBarRuntimeOwner(new FakeScheduler(calls), () -> 0L, () -> calls.add("clear"));

        owner.start();

        assertEquals(List.of("schedule:1"), calls);
    }

    private static int count(List<String> calls, String value) {
        return (int) calls.stream().filter(value::equals).count();
    }

    private static final class FakeScheduler implements BossBarTaskScheduler {

        private final List<String> calls;
        private FakeTask task;
        private boolean failSchedule;

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public TaskHandle schedule(long periodTicks) {
            calls.add("schedule:" + periodTicks);
            if (failSchedule) {
                throw new IllegalStateException("schedule failed");
            }
            task = new FakeTask(calls);
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
