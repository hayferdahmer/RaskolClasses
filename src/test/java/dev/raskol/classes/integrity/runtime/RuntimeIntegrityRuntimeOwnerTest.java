// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

class RuntimeIntegrityRuntimeOwnerTest {

    private static final Logger LOGGER = Logger.getLogger(RuntimeIntegrityRuntimeOwnerTest.class.getName());

    @Test
    void shouldOwnAndStopItsExecutor() {
        CountingExecutor executor = new CountingExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        owner.start();

        assertFalse(executor.isShutdown());

        owner.close();

        assertTrue(executor.isShutdown());
    }

    @Test
    void closeShouldBeIdempotent() {
        CountingExecutor executor = new CountingExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        owner.close();
        owner.close();

        assertTrue(executor.isShutdown());
    }

    @Test
    void shouldRejectStartAfterClose() {
        CountingExecutor executor = new CountingExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        owner.close();

        assertThrows(IllegalStateException.class, owner::start);
    }

    @Test
    void shouldKeepTheConfiguredMonitoringInterval() {
        CountingExecutor executor = new CountingExecutor();
        RuntimeIntegrityRuntimeOwner owner = new RuntimeIntegrityRuntimeOwner(
                RuntimeIntegrityRuntimeOwnerTest::result, LOGGER, snapshot -> {}, Duration.ofMinutes(30), executor);

        try {
            owner.start();
            assertEquals(Duration.ofMinutes(30).toMillis(), owner.intervalMillis());
        } finally {
            owner.close();
        }
    }

    @Test
    void concurrentStartsShouldScheduleOnlyOnePeriodicTask() throws InterruptedException {
        CountingExecutor executor = new CountingExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Runnable startAction = () -> {
            ready.countDown();
            try {
                start.await();
                owner.start();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        };

        Thread first = new Thread(startAction, "rips-owner-start-test-1");
        Thread second = new Thread(startAction, "rips-owner-start-test-2");

        try {
            first.start();
            second.start();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.join(5000);
            second.join(5000);

            assertFalse(first.isAlive());
            assertFalse(second.isAlive());
            assertEquals(1, executor.scheduleCount.get());
        } finally {
            start.countDown();
            owner.close();
        }
    }

    @Test
    void failedStartShouldCloseOwnedExecutor() {
        RejectingExecutor executor = new RejectingExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        assertThrows(RejectedExecutionException.class, owner::start);

        assertTrue(executor.isShutdown());
        assertThrows(IllegalStateException.class, owner::start);
    }

    private static RuntimeIntegrityRuntimeOwner owner(ScheduledThreadPoolExecutor executor) {
        return new RuntimeIntegrityRuntimeOwner(
                RuntimeIntegrityRuntimeOwnerTest::result, LOGGER, snapshot -> {}, Duration.ofMinutes(30), executor);
    }

    private static IntegrityVerificationResult result() {
        return new IntegrityVerificationResult(IntegrityStatus.VERIFIED, "Runtime owner test", 1, List.of());
    }

    private static class CountingExecutor extends ScheduledThreadPoolExecutor {

        private final AtomicInteger scheduleCount = new AtomicInteger();

        private CountingExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
            return super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
    }

    private static final class RejectingExecutor extends CountingExecutor {

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new RejectedExecutionException("Test rejection");
        }
    }
}
