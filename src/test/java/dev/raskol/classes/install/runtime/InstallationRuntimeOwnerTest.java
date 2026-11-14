// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class InstallationRuntimeOwnerTest {

    @Test
    void startSchedulesSweepOnce() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        InstallationRuntimeOwner owner = new InstallationRuntimeOwner(scheduler);

        owner.start();
        owner.start();

        assertEquals(List.of("schedule"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void closeCancelsOwnedTaskOnce() {
        List<String> calls = new ArrayList<>();
        InstallationRuntimeOwner owner = new InstallationRuntimeOwner(new FakeScheduler(calls));

        owner.start();
        owner.close();
        owner.close();

        assertEquals(List.of("schedule", "cancel"), calls);
        assertTrue(owner.isClosed());
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void schedulingFailureLeavesOwnerClosedAndDoesNotLeakTask() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.failScheduling = true;
        InstallationRuntimeOwner owner = new InstallationRuntimeOwner(scheduler);

        assertThrows(IllegalStateException.class, owner::start);

        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
        assertThrows(IllegalStateException.class, owner::start);
    }

    @Test
    void cancellationFailureCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeScheduler scheduler = new FakeScheduler(calls);
        scheduler.task.failFirstCancellation = true;
        InstallationRuntimeOwner owner = new InstallationRuntimeOwner(scheduler);
        owner.start();

        assertThrows(IllegalStateException.class, owner::close);

        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.close();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(List.of("schedule", "cancel", "cancel"), calls);
    }

    @Test
    void closeBeforeStartPreventsLaterStart() {
        List<String> calls = new ArrayList<>();
        InstallationRuntimeOwner owner = new InstallationRuntimeOwner(new FakeScheduler(calls));

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
        assertEquals(List.of(), calls);
    }

    private static final class FakeScheduler implements InstallationTaskScheduler {

        private final List<String> calls;
        private final FakeTask task = new FakeTask();
        private boolean failScheduling;

        private FakeScheduler(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public TaskHandle scheduleSweep() {
            calls.add("schedule");
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
