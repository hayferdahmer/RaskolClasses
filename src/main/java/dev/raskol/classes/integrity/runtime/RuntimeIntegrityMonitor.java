package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class RuntimeIntegrityMonitor implements AutoCloseable {

    private final Supplier<IntegrityVerificationResult> verificationTask;
    private final ScheduledExecutorService executor;
    private final long intervalMillis;
    private final RuntimeIntegrityStateManager stateManager;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Set<CompletableFuture<RuntimeIntegritySnapshot>> pendingChecks =
            ConcurrentHashMap.newKeySet();

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
                }),
                new RuntimeIntegrityStateManager()
        );
    }

    public RuntimeIntegrityMonitor(
            Supplier<IntegrityVerificationResult> verificationTask,
            Duration interval,
            ScheduledExecutorService executor
    ) {
        this(
                verificationTask,
                interval,
                executor,
                new RuntimeIntegrityStateManager()
        );
    }

    public RuntimeIntegrityMonitor(
            Supplier<IntegrityVerificationResult> verificationTask,
            Duration interval,
            ScheduledExecutorService executor,
            RuntimeIntegrityStateManager stateManager
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
        this.stateManager = Objects.requireNonNull(
                stateManager,
                "stateManager"
        );

        this.intervalMillis = interval.toMillis();

        if (intervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "Monitoring interval must be at least 1 millisecond"
            );
        }

        if (stateManager.getSnapshot().state() == RuntimeIntegrityState.STOPPED) {
            throw new IllegalArgumentException(
                    "Cannot use a stopped state manager"
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
        return stateManager.getSnapshot();
    }

    public RuntimeIntegrityStateManager getStateManager() {
        return stateManager;
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
            return stateManager.getSnapshot();
        }

        if (!checking.compareAndSet(false, true)) {
            return stateManager.getSnapshot();
        }

        try {
            if (closed.get()) {
                return stateManager.getSnapshot();
            }

            if (!stateManager.beginCheck()) {
                return stateManager.getSnapshot();
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

                return stateManager.completeCheck(
                        result,
                        durationMillis
                );

            } catch (RuntimeException exception) {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startNanos
                );

                return stateManager.failCheck(
                        exception,
                        durationMillis
                );
            }

        } finally {
            checking.set(false);
        }
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

        stateManager.stop();

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
