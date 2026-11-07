// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

class RuntimeIntegrityMonitorConcurrencyTest {

    @Test
    void closeShouldCompleteAllPendingChecksExceptionally() throws Exception {
        CountDownLatch verificationStarted = new CountDownLatch(1);
        CountDownLatch releaseVerification = new CountDownLatch(1);

        RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> {
                    verificationStarted.countDown();

                    try {
                        if (!releaseVerification.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for test release");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();

                        throw new IllegalStateException("Verification interrupted", exception);
                    }

                    return successfulResult();
                },
                Duration.ofHours(1));

        try {
            CompletableFuture<RuntimeIntegritySnapshot> firstCheck = monitor.checkNow();

            assertTrue(verificationStarted.await(5, TimeUnit.SECONDS), "First verification did not start");

            CompletableFuture<RuntimeIntegritySnapshot> secondCheck = monitor.checkNow();

            monitor.close();

            assertThrows(ExecutionException.class, () -> firstCheck.get(5, TimeUnit.SECONDS));

            assertThrows(ExecutionException.class, () -> secondCheck.get(5, TimeUnit.SECONDS));

            assertEquals(RuntimeIntegrityState.STOPPED, monitor.getSnapshot().state());

        } finally {
            releaseVerification.countDown();
            monitor.close();
        }
    }

    @Test
    void closeShouldNotAllowRunningCheckToOverwriteStoppedState() throws Exception {
        CountDownLatch verificationStarted = new CountDownLatch(1);
        CountDownLatch releaseVerification = new CountDownLatch(1);

        RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> {
                    verificationStarted.countDown();

                    try {
                        releaseVerification.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();

                        throw new IllegalStateException("Verification interrupted", exception);
                    }

                    return successfulResult();
                },
                Duration.ofHours(1));

        try {
            monitor.checkNow();

            assertTrue(verificationStarted.await(5, TimeUnit.SECONDS), "Verification did not start");

            monitor.close();

            releaseVerification.countDown();

            assertEquals(RuntimeIntegrityState.STOPPED, monitor.getSnapshot().state());

            assertEquals(0, monitor.getSnapshot().totalChecks());

        } finally {
            releaseVerification.countDown();
            monitor.close();
        }
    }

    @Test
    void closeShouldBeIdempotent() {
        RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                RuntimeIntegrityMonitorConcurrencyTest::successfulResult, Duration.ofHours(1));

        monitor.close();
        monitor.close();
        monitor.close();

        assertEquals(RuntimeIntegrityState.STOPPED, monitor.getSnapshot().state());
    }

    @Test
    void concurrentChecksShouldNotExecuteVerificationTwice() throws Exception {
        CountDownLatch verificationStarted = new CountDownLatch(1);
        CountDownLatch releaseVerification = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger();

        RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> {
                    executionCount.incrementAndGet();
                    verificationStarted.countDown();

                    try {
                        releaseVerification.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();

                        throw new IllegalStateException("Verification interrupted", exception);
                    }

                    return successfulResult();
                },
                Duration.ofHours(1),
                java.util.concurrent.Executors.newScheduledThreadPool(2));

        try {
            CompletableFuture<RuntimeIntegritySnapshot> firstCheck = monitor.checkNow();

            assertTrue(verificationStarted.await(5, TimeUnit.SECONDS), "First verification did not start");

            CompletableFuture<RuntimeIntegritySnapshot> secondCheck = monitor.checkNow();

            RuntimeIntegritySnapshot secondResult = secondCheck.get(5, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.RUNNING, secondResult.state());

            assertEquals(1, executionCount.get());

            releaseVerification.countDown();

            assertEquals(
                    RuntimeIntegrityState.VERIFIED,
                    firstCheck.get(5, TimeUnit.SECONDS).state());

        } finally {
            releaseVerification.countDown();
            monitor.close();
        }
    }

    private static IntegrityVerificationResult successfulResult() {
        return new IntegrityVerificationResult(IntegrityStatus.VERIFIED, "Runtime integrity test", 1, List.of());
    }
}
