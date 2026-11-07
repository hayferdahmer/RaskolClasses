// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;

class IntegrityFailurePolicyTest {

    private final IntegrityFailurePolicy policy = new IntegrityFailurePolicy();

    @Test
    void shouldAllowVerifiedBuild() {
        IntegrityVerificationResult result = result(IntegrityStatus.VERIFIED);

        assertEquals(IntegrityFailureDecision.CONTINUE, policy.evaluate(result));
    }

    @Test
    void shouldAllowDevelopmentBuild() {
        IntegrityVerificationResult result = result(IntegrityStatus.DEVELOPMENT_BUILD);

        assertEquals(IntegrityFailureDecision.CONTINUE, policy.evaluate(result));
    }

    @Test
    void shouldRejectEveryOtherIntegrityStatus() {
        for (IntegrityStatus status : IntegrityStatus.values()) {
            if (status == IntegrityStatus.VERIFIED || status == IntegrityStatus.DEVELOPMENT_BUILD) {
                continue;
            }

            assertEquals(
                    IntegrityFailureDecision.DISABLE_PLUGIN,
                    policy.evaluate(result(status)),
                    "Unexpected startup decision for status: " + status);
        }
    }

    @Test
    void shouldAllowStartupOnlyForVerifiedAndDevelopmentBuilds() {
        assertTrue(policy.mayContinueStartup(result(IntegrityStatus.VERIFIED)));
        assertTrue(policy.mayContinueStartup(result(IntegrityStatus.DEVELOPMENT_BUILD)));

        for (IntegrityStatus status : IntegrityStatus.values()) {
            if (status == IntegrityStatus.VERIFIED || status == IntegrityStatus.DEVELOPMENT_BUILD) {
                continue;
            }

            assertFalse(policy.mayContinueStartup(result(status)), "Startup must be rejected for status: " + status);
        }
    }

    @Test
    void shouldRejectNullResult() {
        assertThrows(NullPointerException.class, () -> policy.evaluate(null));
    }

    private IntegrityVerificationResult result(IntegrityStatus status) {
        return new IntegrityVerificationResult(status, "Test result", 0, List.of());
    }
}
