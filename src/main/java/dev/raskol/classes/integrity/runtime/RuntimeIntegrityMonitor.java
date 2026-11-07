// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Periodically verifies the integrity of the running plugin resources.
 *
 * The monitor is independent of the Paper API.
 * It does not decide whether the plugin should be disabled.
 */
public final class RuntimeIntegrityMonitor implements AutoCloseable {

    private final Supplier<IntegrityVerificationResult> verificationTask;
    private final ScheduledExecutorService executor;
    private final long intervalMillis;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean checking = new AtomicBoolean();

    private final AtomicReference<RuntimeIntegritySnapshot> snapshot =
            new AtomicReference<>(
                    new RuntimeIntegritySnapshot(
                            RuntimeIntegrityState.NOT_STARTED,
                            null,
                            null,
                            0,
                            0,
                            0,
                            null
                    )
            );

    /**
     * Creates a monitor with its own daemon thread.
     *
     * @param verificationTask integrity verification operation
     * @param interval interval between periodic checks
     */
    public RuntimeIntegrityMonitor(
            Supplier<IntegrityVerificationResult> verificationTask,
            Duration interval
    ) {
        this(
                verificationTask,
                interval,
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(
                            task,
                            "RaskolClasses-RIPS-Monitor"
                    );
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    /**
     * Creates a monitor with an explicitly supplied executor.
     *
     * The monitor assumes ownership of the executor and shuts it down
     * when close() is called.
     *
     * @param verificationTask integrity verification operation
     * @param interval interval between periodic checks
     * @param executor scheduled executor
     */
    public RuntimeIntegrityMonitor(
            Supplier<IntegrityVerificationResult> verificationTask,
            Duration interval,
            ScheduledExecutorService executor
    ) {
        this.verificationTask = Objects.requireNonNull(
                verificationTask,
                "verificationTask"
        );

        Objects.requireNonNull(interval, "interval");

        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException(
                    "interval must be positive"
            );
        }

        try {
            this.intervalMillis = interval.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "interval is too large",
                    exception
            );
        }

        if (intervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "interval must be at least 1 millisecond"
            );
        }

        this.executor = Objects.requireNonNull(
                executor,
                "executor"
        );
    }

    /**
     * Starts periodic integrity verification.
     *
     * The first scheduled check runs after the configured interval.
     * Calling start() more than once has no additional effect.
     */
    public void start() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            throw new IllegalStateException(
                    "Runtime integrity monitor has been stopped"
            );
        }

        if (!started.compareAndSet(false, true)) {
            return;
        }

        executor.scheduleWithFixedDelay(
                this::runScheduledCheck,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Schedules an immediate integrity check.
     *
     * If another check is already running, the current snapshot is returned
     * without starting a concurrent verification.
     *
     * @return future containing the resulting snapshot
     */
    public CompletableFuture<RuntimeIntegritySnapshot> checkNow() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Runtime integrity monitor has been stopped"
                    )
            );
        }

        return CompletableFuture.supplyAsync(
                this::performCheck,
                executor
        );
    }

    /**
     * Returns the latest immutable monitoring snapshot.
     *
     * @return current snapshot
     */
    public RuntimeIntegritySnapshot getSnapshot() {
        return snapshot.get();
    }

    /**
     * Returns the configured interval in milliseconds.
     *
     * @return interval in milliseconds
     */
    public long getIntervalMillis() {
        return intervalMillis;
    }

    private void runScheduledCheck() {
        try {
            performCheck();
        } catch (RuntimeException ignored) {
            // A scheduled execution must not terminate future executions.
            // The actual verification errors are recorded by performCheck().
        }
    }

    private RuntimeIntegritySnapshot performCheck() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            return snapshot.get();
        }

        if (!checking.compareAndSet(false, true)) {
            return snapshot.get();
        }

        RuntimeIntegritySnapshot previous = snapshot.get();

        snapshot.set(
                new RuntimeIntegritySnapshot(
                        RuntimeIntegrityState.RUNNING,
                        previous.lastCheckAt(),
                        previous.lastResult(),
                        previous.totalChecks(),
                        previous.consecutiveFailures(),
                        previous.lastDurationMillis(),
                        previous.lastError()
                )
        );

        long startedAt = System.nanoTime();

        try {
            IntegrityVerificationResult result = Objects.requireNonNull(
                    verificationTask.get(),
                    "verificationTask returned null"
            );

            long durationMillis = elapsedMillis(startedAt);
            Instant completedAt = Instant.now();

            boolean successful = isSuccessful(result.status());

            long failures = successful
                    ? 0
                    : previous.consecutiveFailures() + 1;

            RuntimeIntegrityState newState = stateFor(result.status());

            RuntimeIntegritySnapshot completed =
                    new RuntimeIntegritySnapshot(
                            newState,
                            completedAt,
                            result,
                            previous.totalChecks() + 1,
                            failures,
                            durationMillis,
                            null
                    );

            snapshot.set(completed);

            return completed;
        } catch (RuntimeException exception) {
            long durationMillis = elapsedMillis(startedAt);

            RuntimeIntegritySnapshot failed =
                    new RuntimeIntegritySnapshot(
                            RuntimeIntegrityState.FAILED,
                            Instant.now(),
                            previous.lastResult(),
                            previous.totalChecks() + 1,
                            previous.consecutiveFailures() + 1,
                            durationMillis,
                            sanitizeError(exception)
                    );

            snapshot.set(failed);

            return failed;
        } finally {
            checking.set(false);
        }
    }

    private static RuntimeIntegrityState stateFor(IntegrityStatus status) {
        return switch (status) {
            case VERIFIED -> RuntimeIntegrityState.VERIFIED;
            case DEVELOPMENT_BUILD -> RuntimeIntegrityState.DEVELOPMENT_BUILD;
            default -> RuntimeIntegrityState.FAILED;
        };
    }

    private static boolean isSuccessful(IntegrityStatus status) {
        return status == IntegrityStatus.VERIFIED
                || status == IntegrityStatus.DEVELOPMENT_BUILD;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    private static String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return (
                exception.getClass().getSimpleName()
                        + ": "
                        + message
        ).replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }

    /**
     * Stops periodic checks and shuts down the executor.
     */
    @Override
    public void close() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            return;
        }

        started.set(false);

        snapshot.updateAndGet(previous ->
                new RuntimeIntegritySnapshot(
                        RuntimeIntegrityState.STOPPED,
                        previous.lastCheckAt(),
                        previous.lastResult(),
                        previous.totalChecks(),
                        previous.consecutiveFailures(),
                        previous.lastDurationMillis(),
                        previous.lastError()
                )
        );

        executor.shutdownNow();
    }
}
