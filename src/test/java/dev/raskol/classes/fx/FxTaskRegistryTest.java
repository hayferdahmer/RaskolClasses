// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class FxTaskRegistryTest {

    @Test
    void closeCancelsEveryTrackedTaskAndIsIdempotent() {
        FxTaskRegistry registry = new FxTaskRegistry();
        FakeTask first = new FakeTask();
        FakeTask second = new FakeTask();
        registry.register(first);
        registry.register(second);

        registry.close();
        registry.close();

        assertTrue(first.cancelled);
        assertTrue(second.cancelled);
        assertEquals(0, registry.trackedCount());
    }

    @Test
    void failedCancellationRemainsTrackedForRetry() {
        FxTaskRegistry registry = new FxTaskRegistry();
        FakeTask task = new FakeTask();
        task.failOnce = true;
        registry.register(task);

        assertThrows(IllegalStateException.class, registry::close);
        assertTrue(registry.isClosed());
        assertEquals(1, registry.trackedCount());

        registry.close();

        assertTrue(task.cancelled);
        assertEquals(0, registry.trackedCount());
    }

    @Test
    void closeAttemptsAllTasksWhenOneCancellationFails() {
        FxTaskRegistry registry = new FxTaskRegistry();
        FakeTask failing = new FakeTask();
        FakeTask succeeding = new FakeTask();
        failing.failOnce = true;
        registry.register(failing);
        registry.register(succeeding);

        assertThrows(IllegalStateException.class, registry::close);

        assertTrue(succeeding.cancelled);
        assertEquals(1, registry.trackedCount());
    }

    @Test
    void completedOneShotTaskCanBeReleasedWithoutCancellation() {
        FxTaskRegistry registry = new FxTaskRegistry();
        FakeTask task = new FakeTask();
        registry.register(task);

        registry.release(task);

        assertEquals(0, registry.trackedCount());
        assertTrue(!task.cancelled);
    }

    @Test
    void closedRegistryRejectsAndCancelsNewTasks() {
        FxTaskRegistry registry = new FxTaskRegistry();
        registry.close();
        FakeTask task = new FakeTask();

        assertThrows(IllegalStateException.class, () -> registry.register(task));

        assertTrue(task.cancelled);
        assertEquals(0, registry.trackedCount());
    }

    private static final class FakeTask implements BukkitTask {
        private boolean cancelled;
        private boolean failOnce;

        @Override
        public int getTaskId() {
            return 1;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            if (failOnce) {
                failOnce = false;
                throw new IllegalStateException("cancel failed");
            }
            cancelled = true;
        }
    }
}
