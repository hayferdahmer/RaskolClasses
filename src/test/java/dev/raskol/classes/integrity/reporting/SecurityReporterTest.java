// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.reporting;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityReporterTest {

    private Logger logger;
    private SecurityReporter reporter;
    private TestLogHandler handler;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger(
                "RIPS-Test-" + System.nanoTime()
        );

        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        reporter = new SecurityReporter(logger);
    }

    @Test
    void shouldCreateVerifiedReport() {
        IntegrityVerificationResult result = result(
                IntegrityStatus.VERIFIED,
                "Integrity verification successful",
                5,
                List.of()
        );

        SecurityReport report = reporter.createReport(result);

        assertEquals(
                IntegrityStatus.VERIFIED,
                report.status()
        );

        assertEquals(
                SecuritySeverity.INFO,
                report.severity()
        );

        assertEquals(5, report.checkedFiles());
        assertTrue(report.failedFiles().isEmpty());

        assertTrue(handler.records.isEmpty());
    }

    @Test
    void shouldClassifyDevelopmentBuildAsInfo() {
        assertEquals(
                SecuritySeverity.INFO,
                reporter.severityFor(
                        IntegrityStatus.DEVELOPMENT_BUILD
                )
        );
    }

    @Test
    void shouldClassifyUnavailableTrustedKeyAsWarning() {
        assertEquals(
                SecuritySeverity.WARNING,
                reporter.severityFor(
                        IntegrityStatus.TRUSTED_KEY_UNAVAILABLE
                )
        );
    }

    @Test
    void shouldClassifyUnavailableArtifactAsWarning() {
        assertEquals(
                SecuritySeverity.WARNING,
                reporter.severityFor(
                        IntegrityStatus.ARTIFACT_UNAVAILABLE
                )
        );
    }

    @Test
    void shouldClassifyInvalidSignatureAsCritical() {
        assertEquals(
                SecuritySeverity.CRITICAL,
                reporter.severityFor(
                        IntegrityStatus.INVALID_SIGNATURE
                )
        );
    }

    @Test
    void shouldClassifyProtectedFileHashMismatchAsCritical() {
        assertEquals(
                SecuritySeverity.CRITICAL,
                reporter.severityFor(
                        IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH
                )
        );
    }

    @Test
    void shouldLogVerifiedReportAtInfoLevel() {
        IntegrityVerificationResult result = result(
                IntegrityStatus.VERIFIED,
                "Verification successful",
                3,
                List.of()
        );

        reporter.report(result);

        assertEquals(1, handler.records.size());

        LogRecord record = handler.records.getFirst();

        assertEquals(Level.INFO, record.getLevel());
        assertTrue(record.getMessage().contains("[RIPS] INFO"));
        assertTrue(record.getMessage().contains("VERIFIED"));
        assertTrue(record.getMessage().contains("checkedFiles=3"));
    }

    @Test
    void shouldLogCriticalReportAtSevereLevel() {
        IntegrityVerificationResult result = result(
                IntegrityStatus.INVALID_SIGNATURE,
                "Signature verification failed",
                0,
                List.of()
        );

        reporter.report(result);

        assertEquals(1, handler.records.size());

        LogRecord record = handler.records.getFirst();

        assertEquals(Level.SEVERE, record.getLevel());
        assertTrue(record.getMessage().contains("CRITICAL"));
        assertTrue(record.getMessage().contains("INVALID_SIGNATURE"));
    }

    @Test
    void shouldIncludeFailedFilesInReport() {
        IntegrityVerificationResult result = result(
                IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH,
                "Protected file hash mismatch",
                2,
                List.of("Example.class")
        );

        reporter.report(result);

        String message = handler.records.getFirst().getMessage();

        assertTrue(message.contains("failedFiles=Example.class"));
    }

    @Test
    void shouldSanitizeMultilineMessages() {
        IntegrityVerificationResult result = result(
                IntegrityStatus.INVALID_MANIFEST,
                "Invalid manifest\nInjected log entry",
                0,
                List.of()
        );

        reporter.report(result);

        String message = handler.records.getFirst().getMessage();

        assertFalse(message.contains("\n"));
        assertTrue(message.contains("Invalid manifest Injected log entry"));
    }

    @Test
    void shouldReturnImmutableFailedFiles() {
        IntegrityVerificationResult result = result(
                IntegrityStatus.PROTECTED_FILE_MISSING,
                "Protected file missing",
                1,
                List.of("Example.class")
        );

        SecurityReport report = reporter.createReport(result);

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.failedFiles().add("Another.class")
        );
    }

    @Test
    void shouldRejectNullResult() {
        assertThrows(
                NullPointerException.class,
                () -> reporter.createReport(null)
        );
    }

    private static IntegrityVerificationResult result(
            IntegrityStatus status,
            String message,
            int checkedFiles,
            List<String> failedFiles
    ) {
        return new IntegrityVerificationResult(
                status,
                message,
                checkedFiles,
                failedFiles
        );
    }

    private static final class TestLogHandler extends Handler {

        private final List<LogRecord> records =
                new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            records.clear();
        }
    }
}
