
package dev.raskol.classes.integrity.reporting;

import dev.raskol.classes.integrity.verification.IntegrityStatus;

import java.util.List;
import java.util.Objects;

/**
 * Immutable report describing the result of an integrity check.
 */
public record SecurityReport(
        IntegrityStatus status,
        SecuritySeverity severity,
        String message,
        int checkedFiles,
        List<String> failedFiles
) {

    public SecurityReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");

        if (checkedFiles < 0) {
            throw new IllegalArgumentException(
                    "checkedFiles cannot be negative"
            );
        }

        failedFiles = List.copyOf(
                Objects.requireNonNull(failedFiles, "failedFiles")
        );
    }
}
