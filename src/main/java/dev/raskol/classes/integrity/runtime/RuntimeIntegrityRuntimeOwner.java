// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import dev.raskol.classes.integrity.reporting.SecurityReporter;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

/**
 * Владелец runtime RIPS monitor и выделенного executor.
 *
 * <p>Paper-specific обработка отказа передаётся извне; этот класс не зависит
 * от Bukkit API и отвечает только за запуск и освобождение runtime-проверки.</p>
 */
public final class RuntimeIntegrityRuntimeOwner implements AutoCloseable {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(30);

    private final RuntimeIntegrityMonitor monitor;
    private final Logger logger;
    private final AtomicBoolean startLogged = new AtomicBoolean(false);

    public RuntimeIntegrityRuntimeOwner(
            Supplier<IntegrityVerificationResult> verificationTask,
            Logger logger,
            Consumer<RuntimeIntegritySnapshot> failureHandler) {
        this(
                verificationTask,
                logger,
                failureHandler,
                DEFAULT_INTERVAL,
                createExecutor());
    }

    RuntimeIntegrityRuntimeOwner(
            Supplier<IntegrityVerificationResult> verificationTask,
            Logger logger,
            Consumer<RuntimeIntegritySnapshot> failureHandler,
            Duration interval,
            ScheduledExecutorService executor) {
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(executor, "executor");

        RuntimeSecurityReporter runtimeReporter =
                new RuntimeSecurityReporter(new SecurityReporter(logger), logger);

        this.monitor = new RuntimeIntegrityMonitor(
                Objects.requireNonNull(verificationTask, "verificationTask"),
                Objects.requireNonNull(interval, "interval"),
                executor,
                new RuntimeIntegrityStateManager(),
                runtimeReporter,
                Objects.requireNonNull(failureHandler, "failureHandler"));
    }

    /** Запускает периодические проверки; повторный вызов безопасен. */
    public void start() {
        if (!startLogged.compareAndSet(false, true)) {
            monitor.start();
            return;
        }

        try {
            monitor.start();
        } catch (RuntimeException exception) {
            startLogged.set(false);
            throw exception;
        }

        logger.info("[RIPS] Artifact-bound runtime integrity monitoring started (interval: "
                + Duration.ofMillis(monitor.getIntervalMillis()).toMinutes()
                + " minutes)");
    }

    long intervalMillis() {
        return monitor.getIntervalMillis();
    }

    /** Останавливает монитор и освобождает принадлежащий ему executor. */
    @Override
    public void close() {
        monitor.close();
    }

    private static ScheduledExecutorService createExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "RaskolClasses-RIPS-Monitor");
            thread.setDaemon(true);
            return thread;
        };

        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }
}
