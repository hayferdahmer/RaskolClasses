
package dev.raskol.classes.integrity.verification;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of startup integrity verification.
 *
 * @param status verification status
 * @param message human-readable diagnostic message
 * @param checkedFiles number of successfully checked files
 * @param failedFiles paths of files that failed verification
 */
public record IntegrityVerificationResult(
        IntegrityStatus status,
        String message,
        int checkedFiles,
        List<String> failedFiles
) {

    public IntegrityVerificationResult {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(
                failedFiles,
                "failedFiles cannot be null"
        );

        if (message.isBlank()) {
            throw new IllegalArgumentException(
                    "message cannot be blank"
            );
        }

        if (checkedFiles < 0) {
            throw new IllegalArgumentException(
                    "checkedFiles cannot be negative"
            );
        }

        failedFiles = List.copyOf(failedFiles);
    }

    /**
     * Returns true only when an official build was verified.
     */
    public boolean isVerified() {
        return status == IntegrityStatus.VERIFIED;
    }

    /**
     * Returns true when the result represents a development build.
     */
    public boolean isDevelopmentBuild() {
        return status == IntegrityStatus.DEVELOPMENT_BUILD;
    }
}
