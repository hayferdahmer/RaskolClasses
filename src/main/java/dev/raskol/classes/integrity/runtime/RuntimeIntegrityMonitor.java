// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Set<CompletableFuture<RuntimeIntegritySnapshot>> pendingChecks =
            ConcurrentHashMap.newKeySet();

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

    /**
     * The monitor takes ownership of the supplied executor
     * and shuts it down when close() is called.
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
     * The first check is performed after the configured interval.
     */
    public void start() {
        ensureNotClosed();

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

            if (closed.get()) {
                throw new IllegalStateException(
                        "Runtime integrity monitor has been stopped",
                        exception
                );
            }

            throw exception;
        }
    }

    /**
     * Requests an asynchronous integrity verification.
     *
     * If another verification is already running, this request
     * is executed later by the executor, unless the monitor is closed.
     */
    public CompletableFuture<RuntimeIntegritySnapshot> checkNow() {
        if (closed.get()) {
            return failedFuture(
                    new IllegalStateException(
                            "Runtime integrity monitor has been stopped"
                    )
            );
        }

        CompletableFuture<RuntimeIntegritySnapshot> future =
                new CompletableFuture<>();

        pendingChecks.add(future);

        // close() may have been called between the initial check
        // and registration of this future.
        if (closed.get()) {
            pendingChecks.remove(future);
            future.completeExceptionally(
                    new IllegalStateException(
                            "Runtime integrity monitor has been stopped"
                    )
            );
            return future;
        }

        try {
            executor.execute(() -> {
                try {
                    if (!future.isDone()) {
                        future.complete(performCheck());
                    }
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                } finally {
                    pendingChecks.remove(future);
                }
            });
        } catch (RejectedExecutionException exception) {
            pendingChecks.remove(future);
            future.completeExceptionally(exception);
        }

        return future;
    }

    public RuntimeIntegritySnapshot getSnapshot() {
        return snapshot.get();
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    private void runScheduledCheck() {
        if (closed.get()) {
            return;
        }

        try {
            performCheck();
        } catch (RuntimeException exception) {
            // Keep the periodic scheduler alive after unexpected failures.
        }
    }

    private RuntimeIntegritySnapshot performCheck() {
        if (closed.get()) {
            return snapshot.get();
        }

        if (!checking.compareAndSet(false, true)) {
            return snapshot.get();
        }

        try {
            RuntimeIntegritySnapshot runningSnapshot =
                    snapshot.updateAndGet(current -> {
                        if (current.state() == RuntimeIntegrityState.STOPPED) {
                            return current;
                        }

                        return new RuntimeIntegritySnapshot(
                                RuntimeIntegrityState.RUNNING,
                                current.lastCheckAt(),
                                current.lastResult(),
                                current.totalChecks(),
                                current.consecutiveFailures(),
                                current.lastDurationMillis(),
                                current.lastError()
                        );
                    });

            if (runningSnapshot.state() == RuntimeIntegrityState.STOPPED) {
                return runningSnapshot;
            }

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

                return snapshot.updateAndGet(current -> {
                    if (current.state() == RuntimeIntegrityState.STOPPED) {
                        return current;
                    }

                    boolean successful =
                            resultState == RuntimeIntegrityState.VERIFIED
                                    || resultState == RuntimeIntegrityState.DEVELOPMENT_BUILD;

                    long consecutiveFailures = successful
                            ? 0
                            : current.consecutiveFailures() + 1;

                    return new RuntimeIntegritySnapshot(
                            resultState,
                            Instant.now(),
                            result,
                            current.totalChecks() + 1,
                            consecutiveFailures,
                            durationMillis,
                            null
                    );
                });

            } catch (RuntimeException exception) {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startNanos
                );

                String error = sanitizeError(exception);

                return snapshot.updateAndGet(current -> {
                    if (current.state() == RuntimeIntegrityState.STOPPED) {
                        return current;
                    }

                    return new RuntimeIntegritySnapshot(
                            RuntimeIntegrityState.FAILED,
                            Instant.now(),
                            null,
                            current.totalChecks() + 1,
                            current.consecutiveFailures() + 1,
                            durationMillis,
                            error
                    );
                });
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

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "Runtime integrity monitor has been stopped"
            );
        }
    }

    private <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        snapshot.updateAndGet(current ->
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

        IllegalStateException stoppedException = new IllegalStateException(
                "Runtime integrity monitor has been stopped"
        );

        for (CompletableFuture<RuntimeIntegritySnapshot> future : pendingChecks) {
            future.completeExceptionally(stoppedException);
        }

        pendingChecks.clear();
        executor.shutdownNow();
    }
}
