// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class PlayerCallbackRuntimeOwnerTest {

    @Test
    void executesCallbackAndReleasesHandle() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        UUID playerId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();

        owner.schedule(playerId, 2L, () -> calls.add("respawn"));
        assertEquals(1, owner.ownedTaskCount());
        scheduler.run(0);

        assertEquals(List.of("respawn"), calls);
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void uuidAwareCallbackReceivesTrackedPlayerId() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        UUID playerId = UUID.randomUUID();
        List<UUID> calls = new ArrayList<>();

        owner.schedule(playerId, 1L, calls::add);
        scheduler.run(0);

        assertEquals(List.of(playerId), calls);
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void replacementCancelsPreviousCallbackAndOnlyLatestRuns() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        UUID playerId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();

        owner.schedule(playerId, 2L, () -> calls.add("old"));
        owner.schedule(playerId, 1L, () -> calls.add("new"));
        scheduler.run(0);
        scheduler.run(1);

        assertEquals(List.of("new"), calls);
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void staleCallbackCannotRunAfterReplacementEvenIfSchedulerDeliversIt() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        UUID playerId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();

        owner.schedule(playerId, 2L, () -> calls.add("old"));
        owner.schedule(playerId, 1L, () -> calls.add("new"));
        scheduler.runEvenIfCancelled(0);
        scheduler.run(1);

        assertEquals(List.of("new"), calls);
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void quitCancellationPreventsCallback() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        UUID playerId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();

        owner.schedule(playerId, 1L, () -> calls.add("unexpected"));
        owner.cancel(playerId);
        scheduler.run(0);

        assertTrue(calls.isEmpty());
        assertEquals(0, owner.ownedTaskCount());
    }

    @Test
    void independentOwnersDoNotReplaceCallbacksForTheSamePlayer() {
        FakeScheduler firstScheduler = new FakeScheduler();
        FakeScheduler secondScheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner first = new PlayerCallbackRuntimeOwner(firstScheduler);
        PlayerCallbackRuntimeOwner second = new PlayerCallbackRuntimeOwner(secondScheduler);
        UUID playerId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();

        first.schedule(playerId, 1L, () -> calls.add("first"));
        second.schedule(playerId, 1L, () -> calls.add("second"));
        firstScheduler.run(0);
        secondScheduler.run(0);

        assertEquals(List.of("first", "second"), calls);
        assertEquals(0, first.ownedTaskCount());
        assertEquals(0, second.ownedTaskCount());
    }

    @Test
    void closeCancelsAllCallbacksAndRejectsNewOnes() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        owner.schedule(UUID.randomUUID(), 1L, () -> {});
        owner.schedule(UUID.randomUUID(), 2L, () -> {});

        owner.close();

        assertEquals(0, owner.ownedTaskCount());
        assertTrue(owner.isClosed());
        assertThrows(IllegalStateException.class, () -> owner.schedule(UUID.randomUUID(), 1L, () -> {}));
    }

    @Test
    void cancellationFailureRemainsTrackedForRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        PlayerCallbackRuntimeOwner owner = new PlayerCallbackRuntimeOwner(scheduler);
        UUID playerId = UUID.randomUUID();
        owner.schedule(playerId, 1L, () -> {});
        FakeHandle tracked = scheduler.handles.get(0);
        tracked.failCancellation = true;

        assertThrows(RuntimeException.class, () -> owner.cancel(playerId));
        assertEquals(1, owner.ownedTaskCount());
        tracked.failCancellation = false;
        owner.cancel(playerId);
        assertEquals(0, owner.ownedTaskCount());
    }

    private static final class FakeScheduler implements PlayerCallbackScheduler {
        private final List<Runnable> callbacks = new ArrayList<>();
        private final List<FakeHandle> handles = new ArrayList<>();

        @Override
        public TaskHandle schedule(long delayTicks, Runnable callback) {
            callbacks.add(callback);
            FakeHandle handle = new FakeHandle();
            handles.add(handle);
            return handle;
        }

        private void run(int index) {
            if (!handles.get(index).cancelled) {
                callbacks.get(index).run();
            }
        }

        private void runEvenIfCancelled(int index) {
            callbacks.get(index).run();
        }
    }

    private static final class FakeHandle implements TaskHandle {
        private boolean cancelled;
        private boolean failCancellation;

        @Override
        public void cancel() {
            if (failCancellation) {
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
