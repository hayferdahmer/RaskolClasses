// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.policy.RuntimeIntegrityEnforcementPolicy;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeIntegrityEnforcementIntegrationTest {

    private static final Duration INTERVAL = Duration.ofHours(1);

    private final RuntimeIntegrityEnforcementPolicy policy =
            new RuntimeIntegrityEnforcementPolicy();

    @Test
    void shouldEnforceShutdownAfterThreeConsecutiveTransientFailures()
            throws Exception {

        AtomicBoolean shutdownScheduled = new AtomicBoolean(false);
        AtomicInteger enforcementDecisions = new AtomicInteger();

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> result(IntegrityStatus.ARTIFACT_UNAVAILABLE),
                snapshot -> {
                    if (policy.shouldDisable(snapshot)
                            && shutdownScheduled.compareAndSet(false, true)) {
                        enforcementDecisions.incrementAndGet();
                    }
                }
        )) {
            RuntimeIntegritySnapshot first = check(monitor);

            assertEquals(1, first.consecutiveFailures());
            assertFalse(shutdownScheduled.get());

            RuntimeIntegritySnapshot second = check(monitor);

            assertEquals(2, second.consecutiveFailures());
            assertFalse(shutdownScheduled.get());

            RuntimeIntegritySnapshot third = check(monitor);

            assertEquals(3, third.consecutiveFailures());
            assertTrue(shutdownScheduled.get());
            assertEquals(1, enforcementDecisions.get());
        }
    }

    @Test
    void shouldEnforceShutdownImmediatelyForInvalidSignature()
            throws Exception {

        AtomicBoolean shutdownScheduled = new AtomicBoolean(false);

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> result(IntegrityStatus.INVALID_SIGNATURE),
                snapshot -> {
                    if (policy.shouldDisable(snapshot)) {
                        shutdownScheduled.compareAndSet(false, true);
                    }
                }
        )) {
            RuntimeIntegritySnapshot snapshot = check(monitor);

            assertEquals(1, snapshot.consecutiveFailures());
            assertEquals(
                    RuntimeIntegrityState.FAILED,
                    snapshot.state()
            );
            assertTrue(shutdownScheduled.get());
        }
    }

    @Test
    void shouldEnforceShutdownImmediatelyForArchiveIntegrityFailure()
            throws Exception {

        AtomicBoolean shutdownScheduled = new AtomicBoolean(false);

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> result(IntegrityStatus.ARCHIVE_INTEGRITY_FAILURE),
                snapshot -> {
                    if (policy.shouldDisable(snapshot)) {
                        shutdownScheduled.compareAndSet(false, true);
                    }
                }
        )) {
            RuntimeIntegritySnapshot snapshot = check(monitor);

            assertEquals(1, snapshot.consecutiveFailures());
            assertTrue(shutdownScheduled.get());
        }
    }

    @Test
    void shouldResetFailureSequenceAfterSuccessfulVerification()
            throws Exception {

        AtomicReference<IntegrityStatus> currentStatus =
                new AtomicReference<>(IntegrityStatus.ARTIFACT_UNAVAILABLE);

        AtomicBoolean shutdownScheduled = new AtomicBoolean(false);

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> result(currentStatus.get()),
                snapshot -> {
                    if (policy.shouldDisable(snapshot)) {
                        shutdownScheduled.compareAndSet(false, true);
                    }
                }
        )) {
            RuntimeIntegritySnapshot first = check(monitor);
            RuntimeIntegritySnapshot second = check(monitor);

            assertEquals(1, first.consecutiveFailures());
            assertEquals(2, second.consecutiveFailures());
            assertFalse(shutdownScheduled.get());

            currentStatus.set(IntegrityStatus.VERIFIED);

            RuntimeIntegritySnapshot recovered = check(monitor);

            assertEquals(
                    RuntimeIntegrityState.VERIFIED,
                    recovered.state()
            );
            assertEquals(0, recovered.consecutiveFailures());
            assertFalse(shutdownScheduled.get());

            currentStatus.set(IntegrityStatus.ARTIFACT_UNAVAILABLE);

            RuntimeIntegritySnapshot afterRecovery = check(monitor);

            assertEquals(1, afterRecovery.consecutiveFailures());
            assertFalse(shutdownScheduled.get());
        }
    }

    @Test
    void shouldEnforceShutdownAfterThreeConsecutiveExecutionExceptions()
            throws Exception {

        AtomicBoolean shutdownScheduled = new AtomicBoolean(false);

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> {
                    throw new IllegalStateException(
                            "Simulated verification execution failure"
                    );
                },
                snapshot -> {
                    if (policy.shouldDisable(snapshot)) {
                        shutdownScheduled.compareAndSet(false, true);
                    }
                }
        )) {
            RuntimeIntegritySnapshot first = check(monitor);

            assertEquals(1, first.consecutiveFailures());
            assertNotNull(first.lastError());
            assertFalse(shutdownScheduled.get());

            RuntimeIntegritySnapshot second = check(monitor);

            assertEquals(2, second.consecutiveFailures());
            assertFalse(shutdownScheduled.get());

            RuntimeIntegritySnapshot third = check(monitor);

            assertEquals(3, third.consecutiveFailures());
            assertTrue(shutdownScheduled.get());
        }
    }

    @Test
    void shouldScheduleEnforcementOnlyOnce()
            throws Exception {

        AtomicInteger enforcementDecisions = new AtomicInteger();
        AtomicBoolean shutdownScheduled = new AtomicBoolean(false);

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> result(IntegrityStatus.INVALID_SIGNATURE),
                snapshot -> {
                    if (policy.shouldDisable(snapshot)
                            && shutdownScheduled.compareAndSet(false, true)) {
                        enforcementDecisions.incrementAndGet();
                    }
                }
        )) {
            check(monitor);
            check(monitor);
            check(monitor);

            assertTrue(shutdownScheduled.get());
            assertEquals(1, enforcementDecisions.get());
        }
    }

    @Test
    void shouldInvokeEnforcementCallbackOnMonitoringThread()
            throws Exception {

        AtomicReference<String> callbackThread = new AtomicReference<>();

        try (RuntimeIntegrityMonitor monitor = createMonitor(
                () -> result(IntegrityStatus.INVALID_SIGNATURE),
                snapshot -> callbackThread.set(
                        Thread.currentThread().getName()
                )
        )) {
            check(monitor);

            assertNotNull(callbackThread.get());
            assertFalse(
                    Thread.currentThread().getName().equals(
                            callbackThread.get()
                    )
            );
        }
    }

    private static RuntimeIntegrityMonitor createMonitor(
            Supplier<IntegrityVerificationResult> verificationTask,
            java.util.function.Consumer<RuntimeIntegritySnapshot> failureHandler
    ) {
        return new RuntimeIntegrityMonitor(
                verificationTask,
                INTERVAL,
                Executors.newSingleThreadScheduledExecutor(),
                new RuntimeIntegrityStateManager(),
                null,
                failureHandler
        );
    }

    private static RuntimeIntegritySnapshot check(
            RuntimeIntegrityMonitor monitor
    ) throws Exception {
        return monitor.checkNow().get(2, TimeUnit.SECONDS);
    }

    private static IntegrityVerificationResult result(
            IntegrityStatus status
    ) {
        return new IntegrityVerificationResult(
                status,
                "Runtime enforcement integration test",
                1,
                List.of()
        );
    }
}
