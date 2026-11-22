// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.bootstrap.lifecycle.TaskHandle;

class HudRuntimeOwnerTest {

    @Test
    void registerAndStartAreIdempotentAndQuitCleansPlayer() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeSessionEvents events = new FakeSessionEvents();
        RecordingCleanup cleanup = new RecordingCleanup();
        AtomicInteger refreshes = new AtomicInteger();
        HudRuntimeOwner owner = owner(scheduler, events, cleanup, refreshes::incrementAndGet);

        owner.register();
        owner.register();
        owner.start();
        owner.start();

        assertEquals(1, events.registrations);
        assertEquals(1, scheduler.scheduled.size());
        assertTrue(owner.isRegistered());
        assertTrue(owner.isStarted());

        scheduler.scheduled.getFirst().run();
        UUID playerId = UUID.randomUUID();
        events.fireQuit(playerId);

        assertEquals(1, refreshes.get());
        assertEquals(List.of(playerId), cleanup.clearedPlayers);
    }

    @Test
    void startRequiresSuccessfulRegistration() {
        FakeScheduler scheduler = new FakeScheduler();
        HudRuntimeOwner owner = owner(scheduler, new FakeSessionEvents(), new RecordingCleanup(), () -> {});

        assertThrows(IllegalStateException.class, owner::start);
        assertTrue(scheduler.scheduled.isEmpty());
    }

    @Test
    void schedulingFailureClearsPartialOutputAndCanBeRetried() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.failNext = true;
        RecordingCleanup cleanup = new RecordingCleanup();
        HudRuntimeOwner owner = owner(scheduler, new FakeSessionEvents(), cleanup, () -> {});
        owner.register();

        assertThrows(IllegalStateException.class, owner::start);

        assertEquals(1, cleanup.clearAllCalls);
        assertFalse(owner.isStarted());

        owner.start();

        assertTrue(owner.isStarted());
        assertEquals(1, scheduler.scheduled.size());
    }

    @Test
    void stopCancelsProducerBeforeClearingOutput() {
        FakeScheduler scheduler = new FakeScheduler();
        RecordingCleanup cleanup = new RecordingCleanup();
        cleanup.beforeClearAll = () -> assertTrue(scheduler.scheduled.getFirst().cancelled);
        HudRuntimeOwner owner = owner(scheduler, new FakeSessionEvents(), cleanup, () -> {});
        owner.register();
        owner.start();

        owner.stop();

        assertEquals(1, scheduler.scheduled.getFirst().cancelCalls);
        assertEquals(1, cleanup.clearAllCalls);
        assertFalse(owner.isStarted());
    }

    @Test
    void cancellationFailureRetainsOwnershipAndPreventsDuplicateStart() {
        FakeScheduler scheduler = new FakeScheduler();
        HudRuntimeOwner owner = owner(scheduler, new FakeSessionEvents(), new RecordingCleanup(), () -> {});
        owner.register();
        owner.start();
        scheduler.scheduled.getFirst().failCancelOnce = true;

        assertThrows(IllegalStateException.class, owner::stop);

        assertTrue(owner.isStarted());
        owner.start();
        assertEquals(1, scheduler.scheduled.size());

        owner.stop();

        assertFalse(owner.isStarted());
        assertTrue(scheduler.scheduled.getFirst().cancelled);
    }

    @Test
    void closeUnregistersAndRemainsIdempotent() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeSessionEvents events = new FakeSessionEvents();
        RecordingCleanup cleanup = new RecordingCleanup();
        HudRuntimeOwner owner = owner(scheduler, events, cleanup, () -> {});
        owner.register();
        owner.start();

        owner.close();
        owner.close();

        assertTrue(owner.isClosed());
        assertFalse(owner.isRegistered());
        assertFalse(owner.isStarted());
        assertEquals(1, events.closeCalls);
        assertEquals(1, scheduler.scheduled.getFirst().cancelCalls);
        assertEquals(1, cleanup.clearAllCalls);
        assertThrows(IllegalStateException.class, owner::start);
    }

    @Test
    void closeContinuesUnregistrationWhenOutputCleanupFails() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeSessionEvents events = new FakeSessionEvents();
        RecordingCleanup cleanup = new RecordingCleanup();
        cleanup.failClearAll = true;
        HudRuntimeOwner owner = owner(scheduler, events, cleanup, () -> {});
        owner.register();
        owner.start();

        assertThrows(IllegalStateException.class, owner::close);

        assertTrue(scheduler.scheduled.getFirst().cancelled);
        assertEquals(1, events.closeCalls);
        assertFalse(owner.isClosed());
        assertTrue(owner.isRegistered());

        cleanup.failClearAll = false;
        owner.close();

        assertTrue(owner.isClosed());
        assertFalse(owner.isRegistered());
        assertEquals(1, events.closeCalls);
        assertEquals(2, cleanup.clearAllCalls);
    }

    @Test
    void closeCanRetryListenerUnregistrationAfterFailure() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeSessionEvents events = new FakeSessionEvents();
        events.failCloseOnce = true;
        RecordingCleanup cleanup = new RecordingCleanup();
        HudRuntimeOwner owner = owner(scheduler, events, cleanup, () -> {});
        owner.register();
        owner.start();

        assertThrows(IllegalStateException.class, owner::close);

        assertFalse(owner.isClosed());
        assertTrue(owner.isRegistered());
        assertThrows(IllegalStateException.class, owner::start);

        owner.close();

        assertTrue(owner.isClosed());
        assertFalse(owner.isRegistered());
        assertEquals(1, events.closeCalls);
    }

    @Test
    void rejectsInvalidScheduleIntervals() {
        FakeScheduler scheduler = new FakeScheduler();
        FakeSessionEvents events = new FakeSessionEvents();
        RecordingCleanup cleanup = new RecordingCleanup();

        assertThrows(
                IllegalArgumentException.class,
                () -> new HudRuntimeOwner(scheduler, events, cleanup, () -> {}, -1, 20));
        assertThrows(
                IllegalArgumentException.class, () -> new HudRuntimeOwner(scheduler, events, cleanup, () -> {}, 0, 0));
    }

    private static HudRuntimeOwner owner(
            FakeScheduler scheduler, FakeSessionEvents events, RecordingCleanup cleanup, Runnable refreshAction) {
        return new HudRuntimeOwner(scheduler, events, cleanup, refreshAction, 0, 20);
    }

    private static final class FakeScheduler implements HudTaskScheduler {

        private final List<FakeTask> scheduled = new ArrayList<>();
        private boolean failNext;

        @Override
        public TaskHandle scheduleRepeating(Runnable action, long initialDelayTicks, long periodTicks) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Ошибка тестового планировщика");
            }
            FakeTask task = new FakeTask(action);
            scheduled.add(task);
            return task;
        }
    }

    private static final class FakeTask implements TaskHandle {

        private final Runnable action;
        private boolean cancelled;
        private boolean failCancelOnce;
        private int cancelCalls;

        private FakeTask(Runnable action) {
            this.action = action;
        }

        private void run() {
            if (!cancelled) {
                action.run();
            }
        }

        @Override
        public void cancel() {
            cancelCalls++;
            if (failCancelOnce) {
                failCancelOnce = false;
                throw new IllegalStateException("Временная ошибка отмены задачи");
            }
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class FakeSessionEvents implements HudSessionEvents {

        private java.util.function.Consumer<UUID> quitHandler;
        private int registrations;
        private int closeCalls;
        private boolean failCloseOnce;

        @Override
        public HudRegistration onQuit(java.util.function.Consumer<UUID> handler) {
            registrations++;
            quitHandler = handler;
            AtomicReference<Boolean> registrationClosed = new AtomicReference<>(false);
            return () -> {
                if (failCloseOnce) {
                    failCloseOnce = false;
                    throw new IllegalStateException("Временная ошибка снятия listener");
                }
                if (!registrationClosed.getAndSet(true)) {
                    closeCalls++;
                    quitHandler = null;
                }
            };
        }

        private void fireQuit(UUID playerId) {
            if (quitHandler != null) {
                quitHandler.accept(playerId);
            }
        }
    }

    private static final class RecordingCleanup implements HudOutputCleanup {

        private final List<UUID> clearedPlayers = new ArrayList<>();
        private int clearAllCalls;
        private boolean failClearAll;
        private Runnable beforeClearAll = () -> {};

        @Override
        public void clearPlayer(UUID playerId) {
            clearedPlayers.add(playerId);
        }

        @Override
        public void clearAll() {
            beforeClearAll.run();
            clearAllCalls++;
            if (failClearAll) {
                throw new IllegalStateException("Ошибка очистки output");
            }
        }
    }
}
