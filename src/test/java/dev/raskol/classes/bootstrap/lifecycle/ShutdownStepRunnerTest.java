// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

class ShutdownStepRunnerTest {

    private static final Logger LOGGER = Logger.getLogger("ShutdownStepRunnerTest");

    @Test
    void continuesAfterFailureAndReportsAllFailedSteps() {
        ShutdownStepRunner runner = new ShutdownStepRunner(LOGGER);
        List<String> executionOrder = new ArrayList<>();

        runner.run("first", () -> executionOrder.add("first"));
        runner.run("broken-a", () -> {
            executionOrder.add("broken-a");
            throw new IllegalStateException("failure-a");
        });
        runner.run("middle", () -> executionOrder.add("middle"));
        runner.run("broken-b", () -> {
            executionOrder.add("broken-b");
            throw new IllegalArgumentException("failure-b");
        });
        runner.run("last", () -> executionOrder.add("last"));

        runner.logSummary();

        assertEquals(List.of("first", "broken-a", "middle", "broken-b", "last"), executionOrder);
        assertEquals(2, runner.failureCount());
    }

    @Test
    void reportsSuccessfulShutdownWithoutFailures() {
        ShutdownStepRunner runner = new ShutdownStepRunner(LOGGER);

        runner.run("only-step", () -> {});

        runner.logSummary();

        assertEquals(0, runner.failureCount());
    }
}
