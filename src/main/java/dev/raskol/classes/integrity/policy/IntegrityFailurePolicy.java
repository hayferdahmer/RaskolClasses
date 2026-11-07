// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.policy;

import java.util.Objects;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

/**
 * Defines whether RaskolClasses may continue startup after RIPS verification.
 *
 * Official builds use a fail-closed policy:
 * only VERIFIED is accepted as a trusted official build.
 *
 * Development builds are allowed to start, but remain explicitly marked
 * as development builds.
 */
public final class IntegrityFailurePolicy {

    /**
     * Evaluates the integrity result and determines the startup action.
     *
     * @param result startup integrity verification result
     * @return the action that must be taken
     */
    public IntegrityFailureDecision evaluate(IntegrityVerificationResult result) {
        Objects.requireNonNull(result, "result");

        IntegrityStatus status = result.status();

        return switch (status) {
            case VERIFIED, DEVELOPMENT_BUILD -> IntegrityFailureDecision.CONTINUE;

            default -> IntegrityFailureDecision.DISABLE_PLUGIN;
        };
    }

    /**
     * Returns whether startup is permitted for the given result.
     *
     * @param result startup integrity verification result
     * @return true when startup may continue
     */
    public boolean mayContinueStartup(IntegrityVerificationResult result) {
        return evaluate(result) == IntegrityFailureDecision.CONTINUE;
    }
}
