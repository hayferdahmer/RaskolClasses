// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrustedKeyDescriptorTest {

    private static final String FINGERPRINT = "274ed8b8a8c6dd094a1a42f44a32fe734203d85f101b39e68dd8ddb0cc485cfd";

    @Test
    void activeKeyCanVerifyAndSign() {
        TrustedKeyDescriptor descriptor = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.ACTIVE);

        assertTrue(descriptor.canVerify());
        assertTrue(descriptor.canSign());
    }

    @Test
    void verifyOnlyKeyCanVerifyButCannotSign() {
        TrustedKeyDescriptor descriptor = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.VERIFY_ONLY);

        assertTrue(descriptor.canVerify());
        assertFalse(descriptor.canSign());
    }

    @Test
    void revokedKeyCannotVerifyOrSign() {
        TrustedKeyDescriptor descriptor = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.REVOKED);

        assertFalse(descriptor.canVerify());
        assertFalse(descriptor.canSign());
    }

    @Test
    void shouldAllowActiveToVerifyOnlyTransition() {
        TrustedKeyDescriptor active = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.ACTIVE);

        TrustedKeyDescriptor retired = active.transitionTo(TrustedKeyStatus.VERIFY_ONLY);

        assertEquals(TrustedKeyStatus.ACTIVE, active.status());
        assertEquals(TrustedKeyStatus.VERIFY_ONLY, retired.status());
        assertTrue(retired.canVerify());
        assertFalse(retired.canSign());
    }

    @Test
    void shouldAllowActiveToRevokedTransition() {
        TrustedKeyDescriptor active = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.ACTIVE);

        TrustedKeyDescriptor revoked = active.transitionTo(TrustedKeyStatus.REVOKED);

        assertEquals(TrustedKeyStatus.REVOKED, revoked.status());
        assertFalse(revoked.canVerify());
        assertFalse(revoked.canSign());
    }

    @Test
    void shouldAllowVerifyOnlyToRevokedTransition() {
        TrustedKeyDescriptor verifyOnly = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.VERIFY_ONLY);

        TrustedKeyDescriptor revoked = verifyOnly.transitionTo(TrustedKeyStatus.REVOKED);

        assertEquals(TrustedKeyStatus.REVOKED, revoked.status());
    }

    @Test
    void shouldRejectReactivationOfVerifyOnlyKey() {
        TrustedKeyDescriptor verifyOnly = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.VERIFY_ONLY);

        assertThrows(IllegalStateException.class, () -> verifyOnly.transitionTo(TrustedKeyStatus.ACTIVE));
    }

    @Test
    void shouldRejectTransitionFromRevokedKey() {
        TrustedKeyDescriptor revoked = new TrustedKeyDescriptor(FINGERPRINT, TrustedKeyStatus.REVOKED);

        assertThrows(IllegalStateException.class, () -> revoked.transitionTo(TrustedKeyStatus.ACTIVE));

        assertThrows(IllegalStateException.class, () -> revoked.transitionTo(TrustedKeyStatus.VERIFY_ONLY));
    }

    @Test
    void shouldRejectInvalidFingerprint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrustedKeyDescriptor("invalid-fingerprint", TrustedKeyStatus.ACTIVE));
    }

    @Test
    void shouldRejectUppercaseFingerprint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrustedKeyDescriptor(FINGERPRINT.toUpperCase(), TrustedKeyStatus.ACTIVE));
    }

    @Test
    void shouldRejectNullFingerprint() {
        assertThrows(NullPointerException.class, () -> new TrustedKeyDescriptor(null, TrustedKeyStatus.ACTIVE));
    }

    @Test
    void shouldRejectNullStatus() {
        assertThrows(NullPointerException.class, () -> new TrustedKeyDescriptor(FINGERPRINT, null));
    }
}
