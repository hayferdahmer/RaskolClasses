package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RuntimeIntegrityStateManager {

    private static final int DEFAULT_HISTORY_LIMIT = 100;

    private final int historyLimit;
    private final ArrayDeque<RuntimeIntegrityTransition> history =
            new ArrayDeque<>();

    private RuntimeIntegritySnapshot snapshot =
            new RuntimeIntegritySnapshot(
                    RuntimeIntegrityState.NOT_STARTED,
                    null,
                    null,
                    0,
                    0,
                    0,
                    null
            );

    public RuntimeIntegrityStateManager() {
        this(DEFAULT_HISTORY_LIMIT);
    }

    public RuntimeIntegrityStateManager(int historyLimit) {
        if (historyLimit <= 0) {
            throw new IllegalArgumentException(
                    "History limit must be positive"
            );
        }

        this.historyLimit = historyLimit;
    }

    /**
     * Moves the manager into RUNNING state.
     *
     * @return true if a new check may start; false if another check
     *         is already running or the manager has been stopped.
     */
    public synchronized boolean beginCheck() {
        if (snapshot.state() == RuntimeIntegrityState.STOPPED
                || snapshot.state() == RuntimeIntegrityState.RUNNING) {
            return false;
        }

        transitionTo(
                RuntimeIntegrityState.RUNNING,
                "Integrity verification started"
        );

        return true;
    }

    /**
     * Records a completed integrity verification.
     */
    public synchronized RuntimeIntegritySnapshot completeCheck(
            IntegrityVerificationResult result,
            long durationMillis
    ) {
        Objects.requireNonNull(result, "result");
        validateDuration(durationMillis);

        if (snapshot.state() == RuntimeIntegrityState.STOPPED) {
            return snapshot;
        }

        requireRunning();

        RuntimeIntegrityState newState = mapState(result.status());

        boolean successful =
                newState == RuntimeIntegrityState.VERIFIED
                        || newState == RuntimeIntegrityState.DEVELOPMENT_BUILD;

        long consecutiveFailures = successful
                ? 0
                : snapshot.consecutiveFailures() + 1;

        RuntimeIntegritySnapshot previous = snapshot;

        snapshot = new RuntimeIntegritySnapshot(
                newState,
                Instant.now(),
                result,
                previous.totalChecks() + 1,
                consecutiveFailures,
                durationMillis,
                null
        );

        recordTransition(
                previous.state(),
                newState,
                result.message()
        );

        return snapshot;
    }

    /**
     * Records an unexpected runtime failure during verification.
     */
    public synchronized RuntimeIntegritySnapshot failCheck(
            RuntimeException exception,
            long durationMillis
    ) {
        Objects.requireNonNull(exception, "exception");
        validateDuration(durationMillis);

        if (snapshot.state() == RuntimeIntegrityState.STOPPED) {
            return snapshot;
        }

        requireRunning();

        RuntimeIntegritySnapshot previous = snapshot;
        String error = sanitizeError(exception);

        snapshot = new RuntimeIntegritySnapshot(
                RuntimeIntegrityState.FAILED,
                Instant.now(),
                null,
                previous.totalChecks() + 1,
                previous.consecutiveFailures() + 1,
                durationMillis,
                error
        );

        recordTransition(
                previous.state(),
                RuntimeIntegrityState.FAILED,
                error
        );

        return snapshot;
    }

    /**
     * Permanently stops state management.
     * Subsequent checks cannot change the STOPPED state.
     */
    public synchronized RuntimeIntegritySnapshot stop() {
        if (snapshot.state() == RuntimeIntegrityState.STOPPED) {
            return snapshot;
        }

        transitionTo(
                RuntimeIntegrityState.STOPPED,
                "Runtime integrity monitoring stopped"
        );

        return snapshot;
    }

    public synchronized RuntimeIntegritySnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Returns an immutable copy of the transition history.
     */
    public synchronized List<RuntimeIntegrityTransition> getHistory() {
        return List.copyOf(new ArrayList<>(history));
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    private void transitionTo(
            RuntimeIntegrityState newState,
            String reason
    ) {
        RuntimeIntegritySnapshot previous = snapshot;

        snapshot = new RuntimeIntegritySnapshot(
                newState,
                previous.lastCheckAt(),
                previous.lastResult(),
                previous.totalChecks(),
                previous.consecutiveFailures(),
                previous.lastDurationMillis(),
                previous.lastError()
        );

        recordTransition(previous.state(), newState, reason);
    }

    private void recordTransition(
            RuntimeIntegrityState previousState,
            RuntimeIntegrityState newState,
            String reason
    ) {
        history.addLast(
                new RuntimeIntegrityTransition(
                        Instant.now(),
                        previousState,
                        newState,
                        reason == null ? "" : reason
                )
        );

        while (history.size() > historyLimit) {
            history.removeFirst();
        }
    }

    private RuntimeIntegrityState mapState(IntegrityStatus status) {
        return switch (status) {
            case VERIFIED -> RuntimeIntegrityState.VERIFIED;
            case DEVELOPMENT_BUILD -> RuntimeIntegrityState.DEVELOPMENT_BUILD;
            default -> RuntimeIntegrityState.FAILED;
        };
    }

    private void requireRunning() {
        if (snapshot.state() != RuntimeIntegrityState.RUNNING) {
            throw new IllegalStateException(
                    "Integrity verification is not running"
            );
        }
    }

    private void validateDuration(long durationMillis) {
        if (durationMillis < 0) {
            throw new IllegalArgumentException(
                    "Duration cannot be negative"
            );
        }
    }

    private String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        message = message
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');

        if (message.length() > 512) {
            message = message.substring(0, 512);
        }

        return message;
    }
}
