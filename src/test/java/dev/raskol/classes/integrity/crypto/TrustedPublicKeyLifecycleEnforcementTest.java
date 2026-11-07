// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedPublicKeyLifecycleEnforcementTest {

    @Test
    void shouldIncludeActiveKeyInVerificationKeys()
            throws GeneralSecurityException {

        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        List<TrustedPublicKeyEntry> entries =
                decode(keyPair, TrustedKeyStatus.ACTIVE);

        assertEquals(1, entries.size());
        assertTrue(entries.getFirst().canVerify());
        assertTrue(entries.getFirst().canSign());

        assertEquals(
                1,
                entries.stream()
                        .filter(TrustedPublicKeyEntry::canVerify)
                        .count()
        );
    }

    @Test
    void shouldIncludeVerifyOnlyKeyInVerificationKeys()
            throws GeneralSecurityException {

        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        List<TrustedPublicKeyEntry> entries =
                decode(keyPair, TrustedKeyStatus.VERIFY_ONLY);

        assertEquals(1, entries.size());
        assertTrue(entries.getFirst().canVerify());
        assertFalse(entries.getFirst().canSign());
    }

    @Test
    void shouldExcludeRevokedKeyFromVerificationKeys()
            throws GeneralSecurityException {

        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        List<TrustedPublicKeyEntry> entries =
                decode(keyPair, TrustedKeyStatus.REVOKED);

        assertEquals(1, entries.size());

        assertFalse(entries.getFirst().canVerify());
        assertFalse(entries.getFirst().canSign());

        List<TrustedPublicKeyEntry> verificationEntries =
                entries.stream()
                        .filter(TrustedPublicKeyEntry::canVerify)
                        .toList();

        assertTrue(verificationEntries.isEmpty());
    }

    @Test
    void shouldKeepRevokedEntryInRegistryForInspection()
            throws GeneralSecurityException {

        KeyPair activeKey = Ed25519SignatureService.generateKeyPair();
        KeyPair revokedKey = Ed25519SignatureService.generateKeyPair();

        List<TrustedPublicKeyEntry> entries =
                TrustedPublicKeyRegistry.decodePinnedEntries(
                        List.of(
                                configuration(
                                        activeKey,
                                        TrustedKeyStatus.ACTIVE
                                ),
                                configuration(
                                        revokedKey,
                                        TrustedKeyStatus.REVOKED
                                )
                        )
                );

        assertEquals(2, entries.size());

        long verificationKeyCount = entries.stream()
                .filter(TrustedPublicKeyEntry::canVerify)
                .count();

        assertEquals(1, verificationKeyCount);

        assertEquals(
                TrustedKeyStatus.REVOKED,
                entries.get(1).descriptor().status()
        );
    }

    @Test
    void shouldRejectNullConfiguration()
            throws GeneralSecurityException {

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedEntries(
                        java.util.Arrays.asList(
                                (TrustedPublicKeyRegistry.PinnedKeyConfiguration) null
                        )
                )
        );
    }

    @Test
    void shouldRejectNullStatus()
            throws GeneralSecurityException {

        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedEntries(
                        List.of(
                                new TrustedPublicKeyRegistry.PinnedKeyConfiguration(
                                        encode(keyPair),
                                        null
                                )
                        )
                )
        );
    }

    @Test
    void shouldRejectDuplicateKeysEvenWithDifferentStatuses()
            throws GeneralSecurityException {

        KeyPair keyPair = Ed25519SignatureService.generateKeyPair();

        String encodedKey = encode(keyPair);

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedEntries(
                        List.of(
                                new TrustedPublicKeyRegistry.PinnedKeyConfiguration(
                                        encodedKey,
                                        TrustedKeyStatus.ACTIVE
                                ),
                                new TrustedPublicKeyRegistry.PinnedKeyConfiguration(
                                        encodedKey,
                                        TrustedKeyStatus.REVOKED
                                )
                        )
                )
        );
    }

    @Test
    void shouldRejectEmptyRegistry()
            throws GeneralSecurityException {

        assertThrows(
                GeneralSecurityException.class,
                () -> TrustedPublicKeyRegistry.decodePinnedEntries(
                        List.of()
                )
        );
    }

    @Test
    void shouldExposeOnlyVerificationKeysInTrustedKeyList() {

        List<TrustedPublicKeyEntry> entries =
                TrustedPublicKeyRegistry.allEntries();

        List<java.security.PublicKey> verificationKeys =
                TrustedPublicKeyRegistry.trustedKeys();

        assertEquals(
                entries.stream()
                        .filter(TrustedPublicKeyEntry::canVerify)
                        .count(),
                verificationKeys.size()
        );

        assertFalse(verificationKeys.isEmpty());
    }

    @Test
    void shouldNotExposeRevokedKeysInFingerprintList()
            throws GeneralSecurityException {

        KeyPair activeKey = Ed25519SignatureService.generateKeyPair();
        KeyPair revokedKey = Ed25519SignatureService.generateKeyPair();

        List<TrustedPublicKeyEntry> entries =
                TrustedPublicKeyRegistry.decodePinnedEntries(
                        List.of(
                                configuration(
                                        activeKey,
                                        TrustedKeyStatus.ACTIVE
                                ),
                                configuration(
                                        revokedKey,
                                        TrustedKeyStatus.REVOKED
                                )
                        )
                );

        List<String> verificationFingerprints =
                entries.stream()
                        .filter(TrustedPublicKeyEntry::canVerify)
                        .map(entry -> entry.descriptor().fingerprint())
                        .toList();

        String revokedFingerprint =
                TrustedPublicKeyRegistry.fingerprint(
                        revokedKey.getPublic()
                );

        assertFalse(
                verificationFingerprints.contains(revokedFingerprint)
        );
    }

    private static List<TrustedPublicKeyEntry> decode(
            KeyPair keyPair,
            TrustedKeyStatus status
    ) throws GeneralSecurityException {

        return TrustedPublicKeyRegistry.decodePinnedEntries(
                List.of(configuration(keyPair, status))
        );
    }

    private static TrustedPublicKeyRegistry.PinnedKeyConfiguration configuration(
            KeyPair keyPair,
            TrustedKeyStatus status
    ) {

        return new TrustedPublicKeyRegistry.PinnedKeyConfiguration(
                encode(keyPair),
                status
        );
    }

    private static String encode(KeyPair keyPair) {
        return Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());
    }
}
