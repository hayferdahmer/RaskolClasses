// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

final class InstallationCleanupCoordinator {

    private final Map<UUID, ActiveInstallation> installations;
    private final Function<InstallationType, InstallationHandler> handlers;
    private final FailureReporter failureReporter;

    @FunctionalInterface
    interface FailureReporter {
        void report(String operation, ActiveInstallation installation, RuntimeException exception);
    }

    InstallationCleanupCoordinator(
            Map<UUID, ActiveInstallation> installations,
            Function<InstallationType, InstallationHandler> handlers,
            FailureReporter failureReporter) {
        this.installations = installations;
        this.handlers = handlers;
        this.failureReporter = failureReporter;
    }

    int cleanup(Predicate<ActiveInstallation> selector, String operation) {
        int failures = 0;
        for (ActiveInstallation installation : List.copyOf(installations.values())) {
            if (!selector.test(installation)) {
                continue;
            }
            if (!expireAndRemove(installation, operation)) {
                failures++;
            }
        }
        return failures;
    }

    private boolean expireAndRemove(ActiveInstallation installation, String operation) {
        if (installations.get(installation.id()) != installation) {
            return true;
        }

        InstallationHandler handler = handlers.apply(installation.type());
        if (handler == null) {
            failureReporter.report(
                    operation,
                    installation,
                    new IllegalStateException("No installation handler is registered for " + installation.type()));
            return false;
        }

        try {
            handler.expire(installation);
        } catch (RuntimeException exception) {
            failureReporter.report(operation, installation, exception);
            return false;
        }

        installations.remove(installation.id(), installation);
        return true;
    }
}
