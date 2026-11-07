// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

import java.time.Instant;

import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

/**
 * Immutable snapshot of runtime integrity monitoring.
 *
 * @param state current monitor state
 * @param lastCheckAt time of the last completed check, or null
 * @param lastResult last verification result, or null
 * @param totalChecks total number of completed checks
 * @param consecutiveFailures number of consecutive failed checks
 * @param lastDurationMillis duration of the last check in milliseconds
 * @param lastError description of the last execution error, or null
 */
public record RuntimeIntegritySnapshot(
        RuntimeIntegrityState state,
        Instant lastCheckAt,
        IntegrityVerificationResult lastResult,
        long totalChecks,
        long consecutiveFailures,
        long lastDurationMillis,
        String lastError) {

    public RuntimeIntegritySnapshot {
        if (state == null) {
            throw new NullPointerException("state");
        }

        if (totalChecks < 0) {
            throw new IllegalArgumentException("totalChecks must not be negative");
        }

        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("consecutiveFailures must not be negative");
        }

        if (lastDurationMillis < 0) {
            throw new IllegalArgumentException("lastDurationMillis must not be negative");
        }

        if (consecutiveFailures > totalChecks) {
            throw new IllegalArgumentException("consecutiveFailures cannot exceed totalChecks");
        }
    }
}
