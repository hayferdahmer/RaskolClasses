// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import dev.raskol.classes.integrity.reporting.SecurityReporter;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSecurityReporterTest {

    @Test
    void shouldReportStateTransitionOnlyOnce() {
        TestLogHandler handler = new TestLogHandler();
        Logger logger = createLogger(handler);

        RuntimeSecurityReporter reporter = new RuntimeSecurityReporter(
                new SecurityReporter(logger),
                logger
        );

        RuntimeIntegritySnapshot snapshot = snapshot(
                RuntimeIntegrityState.VERIFIED,
                result(IntegrityStatus.VERIFIED),
                null
        );

        reporter.report(snapshot);
        reporter.report(snapshot);
        reporter.report(snapshot);

        assertEquals(
                1,
                countMessages(
                        handler,
                        "Runtime integrity state changed: NOT_STARTED -> VERIFIED"
                )
        );
    }

    @Test
    void shouldReportNewStateTransition() {
        TestLogHandler handler = new TestLogHandler();
        Logger logger = createLogger(handler);

        RuntimeSecurityReporter reporter = new RuntimeSecurityReporter(
                new SecurityReporter(logger),
                logger
        );

        reporter.report(
                snapshot(
                        RuntimeIntegrityState.VERIFIED,
                        result(IntegrityStatus.VERIFIED),
                        null
                )
        );

        reporter.report(
                snapshot(
                        RuntimeIntegrityState.FAILED,
                        result(IntegrityStatus.INVALID_SIGNATURE),
                        null
                )
        );

        assertEquals(
                1,
                countMessages(
                        handler,
                        "Runtime integrity state changed: NOT_STARTED -> VERIFIED"
                )
        );

        assertEquals(
                1,
                countMessages(
                        handler,
                        "Runtime integrity state changed: VERIFIED -> FAILED"
                )
        );
    }

    @Test
    void shouldReportRecoveryAfterFailure() {
        TestLogHandler handler = new TestLogHandler();
        Logger logger = createLogger(handler);

        RuntimeSecurityReporter reporter = new RuntimeSecurityReporter(
                new SecurityReporter(logger),
                logger
        );

        reporter.report(
                snapshot(
                        RuntimeIntegrityState.FAILED,
                        result(IntegrityStatus.INVALID_SIGNATURE),
                        null
                )
        );

        reporter.report(
                snapshot(
                        RuntimeIntegrityState.VERIFIED,
                        result(IntegrityStatus.VERIFIED),
                        null
                )
        );

        assertEquals(
                1,
                countMessages(
                        handler,
                        "Runtime integrity verification recovered"
                )
        );
    }

    @Test
    void shouldSuppressDuplicateRuntimeErrors() {
        TestLogHandler handler = new TestLogHandler();
        Logger logger = createLogger(handler);

        RuntimeSecurityReporter reporter = new RuntimeSecurityReporter(
                new SecurityReporter(logger),
                logger
        );

        RuntimeIntegritySnapshot snapshot = snapshot(
                RuntimeIntegrityState.FAILED,
                null,
                "Verification failed"
        );

        reporter.report(snapshot);
        reporter.report(snapshot);
        reporter.report(snapshot);

        assertEquals(
                1,
                countMessages(
                        handler,
                        "Runtime integrity verification error: Verification failed"
                )
        );
    }

    @Test
    void shouldSanitizeRuntimeErrors() {
        TestLogHandler handler = new TestLogHandler();
        Logger logger = createLogger(handler);

        RuntimeSecurityReporter reporter = new RuntimeSecurityReporter(
                new SecurityReporter(logger),
                logger
        );

        reporter.report(
                snapshot(
                        RuntimeIntegrityState.FAILED,
                        null,
                        "First line\nSecond line\tThird line"
                )
        );

        assertEquals(
                1,
                countMessages(
                        handler,
                        "Runtime integrity verification error: First line Second line Third line"
                )
        );
    }

    @Test
    void shouldReportStoppedState() {
        TestLogHandler handler = new TestLogHandler();
        Logger logger = createLogger(handler);

        RuntimeSecurityReporter reporter = new RuntimeSecurityReporter(
                new SecurityReporter(logger),
                logger
        );

        reporter.report(
                snapshot(
                        RuntimeIntegrityState.STOPPED,
                        null,
                        null
                )
        );

        assertTrue(
                handler.messages().stream().anyMatch(
                        message -> message.contains(
                                "Runtime integrity state changed: NOT_STARTED -> STOPPED"
                        )
                )
        );
    }

    private static RuntimeIntegritySnapshot snapshot(
            RuntimeIntegrityState state,
            IntegrityVerificationResult result,
            String error
    ) {
        return new RuntimeIntegritySnapshot(
                state,
                Instant.now(),
                result,
                1,
                state == RuntimeIntegrityState.FAILED ? 1 : 0,
                10,
                error
        );
    }

    private static IntegrityVerificationResult result(
            IntegrityStatus status
    ) {
        return new IntegrityVerificationResult(
                status,
                "Runtime security reporter test",
                1,
                List.of()
        );
    }

    private static Logger createLogger(TestLogHandler handler) {
        Logger logger = Logger.getLogger(
                "RIPS-RuntimeSecurityReporterTest-" + System.nanoTime()
        );

        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);

        return logger;
    }

    private static long countMessages(
            TestLogHandler handler,
            String expectedMessage
    ) {
        return handler.messages().stream()
                .filter(message -> message.contains(expectedMessage))
                .count();
    }

    private static final class TestLogHandler extends Handler {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            messages.clear();
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
