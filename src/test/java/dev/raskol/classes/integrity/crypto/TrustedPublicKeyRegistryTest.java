// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedPublicKeyRegistryTest {

    private static final String EXPECTED_FINGERPRINT =
            "274ed8b8a8c6dd094a1a42f44a32fe734203d85f101b39e68dd8ddb0cc485cfd";

    @Test
    void shouldContainPinnedPublicKey() {
        List<PublicKey> keys =
                TrustedPublicKeyRegistry.trustedKeys();

        assertFalse(keys.isEmpty());
        assertNotNull(keys.get(0));
    }

    @Test
    void shouldExposeExpectedFingerprint() {
        assertEquals(
                List.of(EXPECTED_FINGERPRINT),
                TrustedPublicKeyRegistry.fingerprints()
        );
    }

    @Test
    void shouldRecognizePinnedPublicKeyAsTrusted() {
        PublicKey pinnedKey =
                TrustedPublicKeyRegistry.trustedKeys().get(0);

        assertTrue(
                TrustedPublicKeyRegistry.isTrusted(pinnedKey)
        );
    }

    @Test
    void shouldRejectUnknownPublicKey() throws Exception {
        PublicKey unknownKey = Ed25519SignatureService
                .generateKeyPair()
                .getPublic();

        assertFalse(
                TrustedPublicKeyRegistry.isTrusted(unknownKey)
        );
    }

    @Test
    void shouldRejectNullPublicKey() {
        assertFalse(
                TrustedPublicKeyRegistry.isTrusted(null)
        );
    }

    @Test
    void shouldLoadMultipleKeysForRotation()
            throws GeneralSecurityException {

        PublicKey oldKey =
                TrustedPublicKeyRegistry.trustedKeys().get(0);

        KeyPair newKeyPair =
                Ed25519SignatureService.generateKeyPair();

        String encodedOldKey =
                Ed25519SignatureService.encodePublicKey(oldKey);

        String encodedNewKey =
                Ed25519SignatureService.encodePublicKey(
                        newKeyPair.getPublic()
                );

        List<PublicKey> rotatedKeys =
                TrustedPublicKeyRegistry.decodePinnedKeys(
                        List.of(encodedOldKey, encodedNewKey)
                );

        assertEquals(2, rotatedKeys.size());

        assertArrayEquals(
                oldKey.getEncoded(),
                rotatedKeys.get(0).getEncoded()
        );

        assertArrayEquals(
                newKeyPair.getPublic().getEncoded(),
                rotatedKeys.get(1).getEncoded()
        );
    }

    @Test
    void shouldPreserveOldKeyWhenNewKeyIsAdded()
            throws GeneralSecurityException {

        PublicKey oldKey =
                TrustedPublicKeyRegistry.trustedKeys().get(0);

        KeyPair newKeyPair =
                Ed25519SignatureService.generateKeyPair();

        List<PublicKey> rotatedKeys =
                TrustedPublicKeyRegistry.decodePinnedKeys(
                        List.of(
                                Ed25519SignatureService.encodePublicKey(oldKey),
                                Ed25519SignatureService.encodePublicKey(
                                        newKeyPair.getPublic()
                                )
                        )
                );

        assertTrue(
                rotatedKeys.stream().anyMatch(key ->
                        java.security.MessageDigest.isEqual(
                                key.getEncoded(),
                                oldKey.getEncoded()
                        )
                )
        );

        assertTrue(
                rotatedKeys.stream().anyMatch(key ->
                        java.security.MessageDigest.isEqual(
                                key.getEncoded(),
                                newKeyPair.getPublic().getEncoded()
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicateKeys()
            throws GeneralSecurityException {

        String encodedKey =
                Ed25519SignatureService.encodePublicKey(
                        TrustedPublicKeyRegistry.trustedKeys().get(0)
                );

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedKeys(
                        List.of(encodedKey, encodedKey)
                )
        );
    }

    @Test
    void shouldRejectEmptyKeyList() {

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedKeys(
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectBlankKey() {

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedKeys(
                        List.of(" ")
                )
        );
    }

    @Test
    void shouldRejectInvalidBase64Key() {

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedKeys(
                        List.of("not-a-valid-key")
                )
        );
    }

    @Test
    void shouldReturnImmutableTrustedKeyList() {
        List<PublicKey> keys =
                TrustedPublicKeyRegistry.trustedKeys();

        assertThrows(
                UnsupportedOperationException.class,
                () -> keys.add(keys.get(0))
        );
    }

    @Test
    void shouldReturnImmutableFingerprintList() {
        List<String> fingerprints =
                TrustedPublicKeyRegistry.fingerprints();

        assertThrows(
                UnsupportedOperationException.class,
                () -> fingerprints.add("unknown")
        );
    }
}
