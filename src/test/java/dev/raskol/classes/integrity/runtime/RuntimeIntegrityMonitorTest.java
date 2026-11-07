// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

class RuntimeIntegrityMonitorTest {

    private static final Duration INTERVAL = Duration.ofSeconds(1800);

    @Test
    void shouldStartInNotStartedState() {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.VERIFIED))) {
            assertEquals(
                    RuntimeIntegrityState.NOT_STARTED, monitor.getSnapshot().state());

            assertEquals(1800_000L, monitor.getIntervalMillis());
        }
    }

    @Test
    void shouldCompleteSuccessfulVerification() throws Exception {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.VERIFIED))) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.VERIFIED, snapshot.state());

            assertEquals(1, snapshot.totalChecks());
            assertEquals(0, snapshot.consecutiveFailures());
            assertNotNull(snapshot.lastCheckAt());
            assertNotNull(snapshot.lastResult());
            assertTrue(snapshot.lastDurationMillis() >= 0);
            assertEquals(null, snapshot.lastError());
        }
    }

    @Test
    void shouldRecognizeDevelopmentBuild() throws Exception {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.DEVELOPMENT_BUILD))) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.DEVELOPMENT_BUILD, snapshot.state());

            assertEquals(0, snapshot.consecutiveFailures());
        }
    }

    @Test
    void shouldRecordIntegrityFailure() throws Exception {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.INVALID_SIGNATURE))) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.FAILED, snapshot.state());

            assertEquals(1, snapshot.totalChecks());
            assertEquals(1, snapshot.consecutiveFailures());
            assertEquals(
                    IntegrityStatus.INVALID_SIGNATURE, snapshot.lastResult().status());
        }
    }

    @Test
    void shouldRecordVerificationException() throws Exception {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> {
            throw new IllegalStateException("Test\nverification\tfailure");
        })) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.FAILED, snapshot.state());

            assertEquals(1, snapshot.totalChecks());
            assertEquals(1, snapshot.consecutiveFailures());

            assertNotNull(snapshot.lastError());
            assertFalse(snapshot.lastError().contains("\n"));
            assertFalse(snapshot.lastError().contains("\t"));
        }
    }

    @Test
    void shouldInvokeFailureHandlerForFailedVerification() throws Exception {

        AtomicReference<RuntimeIntegritySnapshot> handled = new AtomicReference<>();

        try (RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> result(IntegrityStatus.INVALID_SIGNATURE),
                INTERVAL,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                new RuntimeIntegrityStateManager(),
                null,
                handled::set)) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.FAILED, snapshot.state());
            assertNotNull(handled.get());
            assertEquals(RuntimeIntegrityState.FAILED, handled.get().state());
            assertEquals(
                    IntegrityStatus.INVALID_SIGNATURE,
                    handled.get().lastResult().status());
        }
    }

    @Test
    void shouldNotInvokeFailureHandlerForSuccessfulVerification() throws Exception {

        AtomicInteger invocations = new AtomicInteger();

        try (RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> result(IntegrityStatus.VERIFIED),
                INTERVAL,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                new RuntimeIntegrityStateManager(),
                null,
                snapshot -> invocations.incrementAndGet())) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.VERIFIED, snapshot.state());
            assertEquals(0, invocations.get());
        }
    }

    @Test
    void shouldContinueWhenFailureHandlerThrows() throws Exception {
        try (RuntimeIntegrityMonitor monitor = new RuntimeIntegrityMonitor(
                () -> result(IntegrityStatus.INVALID_SIGNATURE),
                INTERVAL,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                new RuntimeIntegrityStateManager(),
                null,
                snapshot -> {
                    throw new IllegalStateException("Handler failure");
                })) {
            RuntimeIntegritySnapshot snapshot = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.FAILED, snapshot.state());
            assertEquals(1, snapshot.totalChecks());
        }
    }

    @Test
    void shouldResetConsecutiveFailuresAfterSuccessfulCheck() throws Exception {

        int[] invocation = {0};

        try (RuntimeIntegrityMonitor monitor = monitor(() -> {
            invocation[0]++;

            if (invocation[0] == 1) {
                return result(IntegrityStatus.INVALID_SIGNATURE);
            }

            return result(IntegrityStatus.VERIFIED);
        })) {

            RuntimeIntegritySnapshot first = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.FAILED, first.state());

            assertEquals(1, first.consecutiveFailures());

            RuntimeIntegritySnapshot second = monitor.checkNow().get(2, TimeUnit.SECONDS);

            assertEquals(RuntimeIntegrityState.VERIFIED, second.state());

            assertEquals(2, second.totalChecks());
            assertEquals(0, second.consecutiveFailures());
        }
    }

    @Test
    void shouldNotStartPeriodicCheckImmediately() {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.VERIFIED))) {
            monitor.start();

            assertEquals(
                    RuntimeIntegrityState.NOT_STARTED, monitor.getSnapshot().state());

            assertEquals(0, monitor.getSnapshot().totalChecks());
        }
    }

    @Test
    void shouldIgnoreRepeatedStartCalls() {
        try (RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.VERIFIED))) {
            monitor.start();
            monitor.start();

            assertEquals(
                    RuntimeIntegrityState.NOT_STARTED, monitor.getSnapshot().state());
        }
    }

    @Test
    void shouldStopMonitor() {
        RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.VERIFIED));

        monitor.close();

        assertEquals(RuntimeIntegrityState.STOPPED, monitor.getSnapshot().state());
    }

    @Test
    void shouldRejectChecksAfterStop() {
        RuntimeIntegrityMonitor monitor = monitor(() -> result(IntegrityStatus.VERIFIED));

        monitor.close();

        assertThrows(ExecutionException.class, () -> monitor.checkNow().get(2, TimeUnit.SECONDS));
    }

    @Test
    void shouldRejectZeroInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeIntegrityMonitor(() -> result(IntegrityStatus.VERIFIED), Duration.ZERO));
    }

    @Test
    void shouldRejectNegativeInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeIntegrityMonitor(() -> result(IntegrityStatus.VERIFIED), Duration.ofSeconds(-1)));
    }

    private static RuntimeIntegrityMonitor monitor(java.util.function.Supplier<IntegrityVerificationResult> task) {
        return new RuntimeIntegrityMonitor(task, INTERVAL);
    }

    private static IntegrityVerificationResult result(IntegrityStatus status) {
        return new IntegrityVerificationResult(status, "Runtime integrity test", 1, List.of());
    }
}
