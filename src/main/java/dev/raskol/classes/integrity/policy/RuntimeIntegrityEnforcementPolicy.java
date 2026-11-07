// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.policy;

import dev.raskol.classes.integrity.runtime.RuntimeIntegritySnapshot;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityState;
import dev.raskol.classes.integrity.verification.IntegrityStatus;

/**
 * Determines whether a runtime integrity failure requires plugin shutdown.
 *
 * <p>Confirmed integrity violations are enforced immediately.
 * Temporary artifact access and resource reading failures are tolerated
 * up to a configurable number of consecutive checks.</p>
 */
public final class RuntimeIntegrityEnforcementPolicy {

    private static final int DEFAULT_TRANSIENT_FAILURE_THRESHOLD = 3;

    private final int transientFailureThreshold;

    public RuntimeIntegrityEnforcementPolicy() {
        this(DEFAULT_TRANSIENT_FAILURE_THRESHOLD);
    }

    public RuntimeIntegrityEnforcementPolicy(int transientFailureThreshold) {
        if (transientFailureThreshold <= 0) {
            throw new IllegalArgumentException("Transient failure threshold must be positive");
        }

        this.transientFailureThreshold = transientFailureThreshold;
    }

    public boolean shouldDisable(RuntimeIntegritySnapshot snapshot) {
        if (snapshot == null || snapshot.state() != RuntimeIntegrityState.FAILED) {
            return false;
        }

        if (snapshot.lastResult() == null) {
            return snapshot.consecutiveFailures() >= transientFailureThreshold;
        }

        IntegrityStatus status = snapshot.lastResult().status();

        return switch (status) {
            case ARTIFACT_UNAVAILABLE,
                    TRUSTED_KEY_UNAVAILABLE,
                    PROTECTED_FILE_READ_ERROR,
                    RESOURCE_READ_ERROR -> snapshot.consecutiveFailures() >= transientFailureThreshold;

            case VERIFIED, DEVELOPMENT_BUILD -> false;

            default -> true;
        };
    }

    public int getTransientFailureThreshold() {
        return transientFailureThreshold;
    }
}
