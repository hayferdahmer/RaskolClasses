// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.reporting;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

/**
 * Converts integrity verification results into structured security reports
 * and writes them to the configured logger.
 *
 * This class does not decide whether the plugin may continue starting.
 * Startup policy is handled by a separate RIPS component.
 */
public final class SecurityReporter {

    private final Logger logger;

    public SecurityReporter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Creates a structured report without writing it to the logger.
     */
    public SecurityReport createReport(IntegrityVerificationResult result) {
        Objects.requireNonNull(result, "result");

        return new SecurityReport(
                result.status(),
                severityFor(result.status()),
                sanitize(result.message()),
                result.checkedFiles(),
                result.failedFiles());
    }

    /**
     * Creates and logs a security report.
     */
    public SecurityReport report(IntegrityVerificationResult result) {
        SecurityReport report = createReport(result);

        log(report);

        return report;
    }

    /**
     * Determines the severity associated with an integrity status.
     */
    public SecuritySeverity severityFor(IntegrityStatus status) {
        Objects.requireNonNull(status, "status");

        return switch (status) {
            case VERIFIED, DEVELOPMENT_BUILD -> SecuritySeverity.INFO;

            case TRUSTED_KEY_UNAVAILABLE, ARTIFACT_UNAVAILABLE, PROTECTED_FILE_READ_ERROR, RESOURCE_READ_ERROR ->
                SecuritySeverity.WARNING;

            case DEVELOPMENT_MODE_REQUIRED,
                    ARCHIVE_INTEGRITY_FAILURE,
                    MANIFEST_MISSING,
                    MANIFEST_TOO_LARGE,
                    SIGNATURE_MISSING,
                    INVALID_SIGNATURE,
                    INVALID_MANIFEST,
                    BUILD_IDENTITY_MISMATCH,
                    PROTECTED_FILE_MISSING,
                    PROTECTED_FILE_TOO_LARGE,
                    PROTECTED_FILE_SIZE_MISMATCH,
                    PROTECTED_FILE_HASH_MISMATCH,
                    CRYPTOGRAPHIC_ERROR -> SecuritySeverity.CRITICAL;
        };
    }

    private void log(SecurityReport report) {
        Level level = toLogLevel(report.severity());

        StringBuilder message = new StringBuilder()
                .append("[RIPS] ")
                .append(report.severity())
                .append(" | ")
                .append(report.status())
                .append(" | ")
                .append(report.message())
                .append(" | checkedFiles=")
                .append(report.checkedFiles());

        if (!report.failedFiles().isEmpty()) {
            message.append(" | failedFiles=").append(sanitize(String.join(", ", report.failedFiles())));
        }

        logger.log(level, message.toString());
    }

    private static Level toLogLevel(SecuritySeverity severity) {
        return switch (severity) {
            case INFO -> Level.INFO;
            case NOTICE, WARNING -> Level.WARNING;
            case CRITICAL -> Level.SEVERE;
        };
    }

    /**
     * Prevents multiline log injection from externally supplied text.
     */
    private static String sanitize(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }
}
