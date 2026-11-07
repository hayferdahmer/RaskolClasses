package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.reporting.SecurityReporter;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RuntimeSecurityReporter {

    private final SecurityReporter securityReporter;
    private final Logger logger;

    private RuntimeIntegrityState lastReportedState =
            RuntimeIntegrityState.NOT_STARTED;

    private Object lastReportedResult;
    private String lastReportedError;

    public RuntimeSecurityReporter(
            SecurityReporter securityReporter,
            Logger logger
    ) {
        this.securityReporter = Objects.requireNonNull(
                securityReporter,
                "securityReporter"
        );

        this.logger = Objects.requireNonNull(
                logger,
                "logger"
        );
    }

    /**
     * Reports runtime integrity state changes and new verification results.
     *
     * Identical consecutive results are suppressed to avoid log flooding.
     */
    public synchronized void report(RuntimeIntegritySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        reportStateTransition(snapshot);
        reportVerificationResult(snapshot);
        reportRuntimeError(snapshot);
    }

    private void reportStateTransition(RuntimeIntegritySnapshot snapshot) {
        RuntimeIntegrityState currentState = snapshot.state();

        if (currentState == lastReportedState) {
            return;
        }

        logger.info(
                "[RIPS] Runtime integrity state changed: "
                        + lastReportedState
                        + " -> "
                        + currentState
        );

        if (lastReportedState == RuntimeIntegrityState.FAILED
                && (currentState == RuntimeIntegrityState.VERIFIED
                || currentState == RuntimeIntegrityState.DEVELOPMENT_BUILD)) {
            logger.info(
                    "[RIPS] Runtime integrity verification recovered"
            );
        }

        lastReportedState = currentState;
    }

    private void reportVerificationResult(RuntimeIntegritySnapshot snapshot) {
        Object result = snapshot.lastResult();

        if (result == null) {
            lastReportedResult = null;
            return;
        }

        if (result.equals(lastReportedResult)) {
            return;
        }

        try {
            securityReporter.report(snapshot.lastResult());
            lastReportedResult = result;
        } catch (RuntimeException exception) {
            logger.log(
                    Level.SEVERE,
                    "[RIPS] Failed to report runtime integrity result",
                    exception
            );
        }
    }

    private void reportRuntimeError(RuntimeIntegritySnapshot snapshot) {
        String error = snapshot.lastError();

        if (error == null || error.isBlank()) {
            lastReportedError = null;
            return;
        }

        if (error.equals(lastReportedError)) {
            return;
        }

        logger.severe(
                "[RIPS] Runtime integrity verification error: "
                        + sanitize(error)
        );

        lastReportedError = error;
    }

    private String sanitize(String value) {
        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }
}
