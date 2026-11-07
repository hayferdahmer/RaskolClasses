// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.integrity.verification.IntegrityStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class RuntimeIntegrityMonitor implements AutoCloseable {

    private final Supplier<IntegrityVerificationResult> verificationTask;
    private final ScheduledExecutorService executor;
    private final long intervalMillis;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean checking = new AtomicBoolean(false);

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

    public RuntimeIntegrityMonitor(
            Supplier<IntegrityVerificationResult> verificationTask,
            Duration interval
    ) {
        this(
                verificationTask,
                interval,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "RaskolClasses-RIPS-Monitor"
                    );
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

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
                    "Monitoring interval must be positive"
            );
        }

        this.executor = Objects.requireNonNull(executor, "executor");
        this.intervalMillis = interval.toMillis();

        if (intervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "Monitoring interval must be at least 1 millisecond"
            );
        }
    }

    /**
     * Starts periodic integrity verification.
     *
     * The first verification is performed after the configured interval.
     * Calling this method more than once has no additional effect.
     */
    public void start() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            throw new IllegalStateException(
                    "Runtime integrity monitor has already been stopped"
            );
        }

        if (!started.compareAndSet(false, true)) {
            return;
        }

        try {
            executor.scheduleWithFixedDelay(
                    this::runScheduledCheck,
                    intervalMillis,
                    intervalMillis,
                    TimeUnit.MILLISECONDS
            );
        } catch (RejectedExecutionException exception) {
            started.set(false);

            if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
                throw new IllegalStateException(
                        "Runtime integrity monitor has already been stopped",
                        exception
                );
            }

            throw exception;
        }
    }

    /**
     * Requests an asynchronous integrity verification.
     *
     * The returned future completes with the resulting snapshot.
     */
    public CompletableFuture<RuntimeIntegritySnapshot> checkNow() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Runtime integrity monitor has been stopped"
                    )
            );
        }

        try {
            return CompletableFuture.supplyAsync(
                    this::performCheck,
                    executor
            );
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public RuntimeIntegritySnapshot getSnapshot() {
        return snapshot.get();
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    private void runScheduledCheck() {
        if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
            return;
        }

        try {
            performCheck();
        } catch (RuntimeException exception) {
            // Prevent an unexpected runtime exception from terminating
            // subsequent scheduled executions.
        }
    }

    private RuntimeIntegritySnapshot performCheck() {
        if (!checking.compareAndSet(false, true)) {
            return snapshot.get();
        }

        try {
            RuntimeIntegritySnapshot previous = snapshot.get();

            if (previous.state() == RuntimeIntegrityState.STOPPED) {
                return previous;
            }

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

            long startNanos = System.nanoTime();

            try {
                IntegrityVerificationResult result = Objects.requireNonNull(
                        verificationTask.get(),
                        "Integrity verification task returned null"
                );

                long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startNanos
                );

                RuntimeIntegrityState resultState = mapState(result.status());

                RuntimeIntegritySnapshot current = snapshot.get();

                if (current.state() == RuntimeIntegrityState.STOPPED) {
                    return current;
                }

                boolean successful =
                        resultState == RuntimeIntegrityState.VERIFIED
                                || resultState == RuntimeIntegrityState.DEVELOPMENT_BUILD;

                long consecutiveFailures = successful
                        ? 0
                        : current.consecutiveFailures() + 1;

                RuntimeIntegritySnapshot completed =
                        new RuntimeIntegritySnapshot(
                                resultState,
                                Instant.now(),
                                result,
                                current.totalChecks() + 1,
                                consecutiveFailures,
                                durationMillis,
                                null
                        );

                // Do not overwrite STOPPED if close() was called
                // while the verification task was running.
                if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
                    return snapshot.get();
                }

                snapshot.set(completed);
                return completed;

            } catch (RuntimeException exception) {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startNanos
                );

                RuntimeIntegritySnapshot current = snapshot.get();

                // Preserve STOPPED if close() happened during verification.
                if (current.state() == RuntimeIntegrityState.STOPPED) {
                    return current;
                }

                RuntimeIntegritySnapshot failed =
                        new RuntimeIntegritySnapshot(
                                RuntimeIntegrityState.FAILED,
                                Instant.now(),
                                null,
                                current.totalChecks() + 1,
                                current.consecutiveFailures() + 1,
                                durationMillis,
                                sanitizeError(exception)
                        );

                if (snapshot.get().state() == RuntimeIntegrityState.STOPPED) {
                    return snapshot.get();
                }

                snapshot.set(failed);
                return failed;
            }

        } finally {
            checking.set(false);
        }
    }

    private RuntimeIntegrityState mapState(IntegrityStatus status) {
        return switch (status) {
            case VERIFIED -> RuntimeIntegrityState.VERIFIED;
            case DEVELOPMENT_BUILD -> RuntimeIntegrityState.DEVELOPMENT_BUILD;
            default -> RuntimeIntegrityState.FAILED;
        };
    }

    private String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        return message
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }

    @Override
    public void close() {
        RuntimeIntegritySnapshot current = snapshot.get();

        if (current.state() == RuntimeIntegrityState.STOPPED) {
            return;
        }

        snapshot.set(
                new RuntimeIntegritySnapshot(
                        RuntimeIntegrityState.STOPPED,
                        current.lastCheckAt(),
                        current.lastResult(),
                        current.totalChecks(),
                        current.consecutiveFailures(),
                        current.lastDurationMillis(),
                        current.lastError()
                )
        );

        executor.shutdownNow();
    }
}
