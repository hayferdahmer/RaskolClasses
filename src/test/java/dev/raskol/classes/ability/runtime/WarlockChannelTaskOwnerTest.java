// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class WarlockChannelTaskOwnerTest {

    @Test
    void cancelOnlyTouchesTheSelectedCaster() {
        WarlockChannelTaskOwner owner = new WarlockChannelTaskOwner();
        FakeTask first = new FakeTask();
        FakeTask second = new FakeTask();
        UUID firstCaster = UUID.randomUUID();
        UUID secondCaster = UUID.randomUUID();
        owner.register(firstCaster, first);
        owner.register(secondCaster, second);

        owner.cancel(firstCaster);

        assertTrue(first.cancelled);
        assertEquals(1, owner.trackedCasterCount());
        assertEquals(1, owner.trackedTaskCount());
        assertTrue(!second.cancelled);
    }

    @Test
    void failedCancellationRemainsTrackedForRetry() {
        WarlockChannelTaskOwner owner = new WarlockChannelTaskOwner();
        FakeTask task = new FakeTask();
        task.failOnce = true;
        UUID caster = UUID.randomUUID();
        owner.register(caster, task);

        assertThrows(IllegalStateException.class, () -> owner.cancel(caster));
        assertEquals(1, owner.trackedTaskCount());

        owner.cancel(caster);
        assertEquals(0, owner.trackedTaskCount());
    }

    @Test
    void closeAttemptsAllCastersAndCanBeRetried() {
        WarlockChannelTaskOwner owner = new WarlockChannelTaskOwner();
        FakeTask first = new FakeTask();
        FakeTask second = new FakeTask();
        first.failOnce = true;
        owner.register(UUID.randomUUID(), first);
        owner.register(UUID.randomUUID(), second);

        assertThrows(IllegalStateException.class, owner::close);
        assertTrue(owner.isClosed());
        assertTrue(second.cancelled);
        assertEquals(1, owner.trackedTaskCount());

        owner.close();
        assertEquals(0, owner.trackedTaskCount());
    }

    @Test
    void completedChannelDropsHandlesWithoutCancelling() {
        WarlockChannelTaskOwner owner = new WarlockChannelTaskOwner();
        FakeTask task = new FakeTask();
        UUID caster = UUID.randomUUID();
        owner.register(caster, task);

        owner.complete(caster);

        assertEquals(0, owner.trackedTaskCount());
        assertTrue(!task.cancelled);
    }

    @Test
    void closedOwnerRejectsNewTasks() {
        WarlockChannelTaskOwner owner = new WarlockChannelTaskOwner();
        owner.close();

        assertThrows(IllegalStateException.class, () -> owner.register(UUID.randomUUID(), new FakeTask()));
    }

    private static final class FakeTask implements TaskHandle {
        private boolean cancelled;
        private boolean failOnce;

        @Override
        public void cancel() {
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
