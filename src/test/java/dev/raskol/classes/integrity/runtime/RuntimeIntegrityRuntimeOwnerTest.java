// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

class RuntimeIntegrityRuntimeOwnerTest {

    private static final Logger LOGGER = Logger.getLogger(RuntimeIntegrityRuntimeOwnerTest.class.getName());

    @Test
    void shouldOwnAndStopItsExecutor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        owner.start();

        assertTrue(!executor.isShutdown());

        owner.close();

        assertTrue(executor.isShutdown());
    }

    @Test
    void closeShouldBeIdempotent() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        owner.close();
        owner.close();

        assertTrue(executor.isShutdown());
    }

    @Test
    void shouldRejectStartAfterClose() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RuntimeIntegrityRuntimeOwner owner = owner(executor);

        owner.close();

        assertThrows(IllegalStateException.class, owner::start);
    }

    @Test
    void shouldKeepTheConfiguredMonitoringInterval() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RuntimeIntegrityRuntimeOwner owner = new RuntimeIntegrityRuntimeOwner(
                () -> result(),
                LOGGER,
                snapshot -> {},
                Duration.ofMinutes(30),
                executor);

        try {
            owner.start();
            assertEquals(30, Duration.ofMillis(30 * 60 * 1000L).toMinutes());
        } finally {
            owner.close();
        }
    }

    @Test
    void repeatedStartShouldNotCreateAdditionalExecutors() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger invocations = new AtomicInteger();
        RuntimeIntegrityRuntimeOwner owner = new RuntimeIntegrityRuntimeOwner(
                () -> {
                    invocations.incrementAndGet();
                    return result();
                },
                LOGGER,
                snapshot -> {},
                Duration.ofMinutes(30),
                executor);

        try {
            owner.start();
            owner.start();

            assertTrue(!executor.isShutdown());
            assertEquals(0, invocations.get());
        } finally {
            owner.close();
        }
    }

    private static RuntimeIntegrityRuntimeOwner owner(ScheduledExecutorService executor) {
        return new RuntimeIntegrityRuntimeOwner(
                RuntimeIntegrityRuntimeOwnerTest::result,
                LOGGER,
                snapshot -> {},
                Duration.ofMinutes(30),
                executor);
    }

    private static IntegrityVerificationResult result() {
        return new IntegrityVerificationResult(IntegrityStatus.VERIFIED, "Runtime owner test", 1, List.of());
    }
}
