// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class PurgeRuntimeOwnerTest {

    @Test
    void startIsIdempotentAndOwnsOneTask() {
        List<String> calls = new ArrayList<>();
        PurgeRuntimeOwner owner = new PurgeRuntimeOwner(() -> {
            calls.add("schedule");
            return new FakeTask(calls);
        });

        owner.start();
        owner.start();

        assertEquals(List.of("schedule"), calls);
        assertTrue(owner.isRunning());
        assertEquals(1, owner.ownedTaskCount());
    }

    @Test
    void closeCancelsProducerAndIsIdempotent() {
        List<String> calls = new ArrayList<>();
        PurgeRuntimeOwner owner = new PurgeRuntimeOwner(() -> {
            calls.add("schedule");
            return new FakeTask(calls);
        });

        owner.start();
        owner.close();
        owner.close();

        assertEquals(List.of("schedule", "cancel"), calls);
        assertTrue(owner.isClosed());
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void cancellationFailureCanBeRetried() {
        List<String> calls = new ArrayList<>();
        FakeTask task = new FakeTask(calls);
        task.failFirstCancellation = true;
        PurgeRuntimeOwner owner = new PurgeRuntimeOwner(() -> task);
        owner.start();

        assertThrows(IllegalStateException.class, owner::close);
        assertFalse(owner.isClosed());
        assertEquals(1, owner.ownedTaskCount());

        owner.close();

        assertTrue(owner.isClosed());
        assertEquals(0, owner.ownedTaskCount());
        assertEquals(List.of("cancel", "cancel"), calls);
    }

    @Test
    void schedulingFailurePreventsRestart() {
        PurgeRuntimeOwner owner = new PurgeRuntimeOwner(() -> {
            throw new IllegalStateException("schedule failed");
        });

        assertThrows(IllegalStateException.class, owner::start);
        assertFalse(owner.isRunning());
        assertEquals(0, owner.ownedTaskCount());
        assertThrows(IllegalStateException.class, owner::start);
    }

    @Test
    void closeBeforeStartPreventsLaterStart() {
        PurgeRuntimeOwner owner = new PurgeRuntimeOwner(() -> {
            throw new AssertionError("scheduler must not be called");
        });

        owner.close();

        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, owner::start);
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
