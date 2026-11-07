// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.policy;

import dev.raskol.classes.integrity.runtime.RuntimeIntegritySnapshot;
import dev.raskol.classes.integrity.runtime.RuntimeIntegrityState;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeIntegrityEnforcementPolicyTest {

    private final RuntimeIntegrityEnforcementPolicy policy =
            new RuntimeIntegrityEnforcementPolicy();

    @Test
    void shouldDisableImmediatelyForInvalidSignature() {
        assertTrue(policy.shouldDisable(
                snapshot(IntegrityStatus.INVALID_SIGNATURE, 1)
        ));
    }

    @Test
    void shouldDisableImmediatelyForHashMismatch() {
        assertTrue(policy.shouldDisable(
                snapshot(IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH, 1)
        ));
    }

    @Test
    void shouldDisableImmediatelyForArchiveIntegrityFailure() {
        assertTrue(policy.shouldDisable(
                snapshot(IntegrityStatus.ARCHIVE_INTEGRITY_FAILURE, 1)
        ));
    }

    @Test
    void shouldAllowFirstTransientFailure() {
        assertFalse(policy.shouldDisable(
                snapshot(IntegrityStatus.ARTIFACT_UNAVAILABLE, 1)
        ));
    }

    @Test
    void shouldAllowSecondTransientFailure() {
        assertFalse(policy.shouldDisable(
                snapshot(IntegrityStatus.ARTIFACT_UNAVAILABLE, 2)
        ));
    }

    @Test
    void shouldDisableAfterThirdTransientFailure() {
        assertTrue(policy.shouldDisable(
                snapshot(IntegrityStatus.ARTIFACT_UNAVAILABLE, 3)
        ));
    }

    @Test
    void shouldApplyThresholdToTrustedKeyFailure() {
        assertFalse(policy.shouldDisable(
                snapshot(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, 2)
        ));

        assertTrue(policy.shouldDisable(
                snapshot(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, 3)
        ));
    }

    @Test
    void shouldApplyThresholdToResourceReadFailure() {
        assertFalse(policy.shouldDisable(
                snapshot(IntegrityStatus.RESOURCE_READ_ERROR, 2)
        ));

        assertTrue(policy.shouldDisable(
                snapshot(IntegrityStatus.RESOURCE_READ_ERROR, 3)
        ));
    }

    @Test
    void shouldApplyThresholdToUnexpectedExecutionFailure() {
        assertFalse(policy.shouldDisable(
                snapshot(null, 2)
        ));

        assertTrue(policy.shouldDisable(
                snapshot(null, 3)
        ));
    }

    @Test
    void shouldNotDisableForSuccessfulState() {
        assertFalse(policy.shouldDisable(
                new RuntimeIntegritySnapshot(
                        RuntimeIntegrityState.VERIFIED,
                        null,
                        result(IntegrityStatus.VERIFIED),
                        1,
                        0,
                        10,
                        null
                )
        ));
    }

    @Test
    void shouldNotDisableForStoppedState() {
        assertFalse(policy.shouldDisable(
                new RuntimeIntegritySnapshot(
                        RuntimeIntegrityState.STOPPED,
                        null,
                        null,
                        1,
                        0,
                        10,
                        null
                )
        ));
    }

    @Test
    void shouldNotDisableForNullSnapshot() {
        assertFalse(policy.shouldDisable(null));
    }

    @Test
    void shouldRejectNonPositiveThreshold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeIntegrityEnforcementPolicy(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeIntegrityEnforcementPolicy(-1)
        );
    }

    @Test
    void shouldExposeConfiguredThreshold() {
        RuntimeIntegrityEnforcementPolicy customPolicy =
                new RuntimeIntegrityEnforcementPolicy(5);

        assertEquals(5, customPolicy.getTransientFailureThreshold());
    }

    private static RuntimeIntegritySnapshot snapshot(
            IntegrityStatus status,
            long consecutiveFailures
    ) {
        return new RuntimeIntegritySnapshot(
                RuntimeIntegrityState.FAILED,
                null,
                status == null ? null : result(status),
                consecutiveFailures,
                consecutiveFailures,
                10,
                status == null ? "Verification execution error" : null
        );
    }

    private static IntegrityVerificationResult result(
            IntegrityStatus status
    ) {
        return new IntegrityVerificationResult(
                status,
                "Runtime enforcement test",
                1,
                List.of()
        );
    }
}
