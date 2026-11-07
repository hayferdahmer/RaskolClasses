// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestSignatureServiceTest {

    private KeyPair trustedKeyPair;
    private KeyPair foreignKeyPair;
    private IntegrityManifest manifest;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        trustedKeyPair = Ed25519SignatureService.generateKeyPair();
        foreignKeyPair = Ed25519SignatureService.generateKeyPair();

        BuildIdentity identity =
                BuildIdentity.development("1.15.8-SNAPSHOT");

        manifest = new IntegrityManifest(
                identity,
                List.of(
                        new ManifestFileEntry(
                                "dev/raskol/Test.class",
                                1024,
                                "a".repeat(64)
                        )
                )
        );
    }

    @Test
    void shouldSignAndVerifyManifest()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        assertTrue(
                ManifestSignatureService.verify(
                        signedManifest,
                        trustedKeyPair.getPublic()
                )
        );
    }

    @Test
    void shouldRejectSignatureFromForeignKey()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        assertFalse(
                ManifestSignatureService.verify(
                        signedManifest,
                        foreignKeyPair.getPublic()
                )
        );
    }

    @Test
    void shouldRejectModifiedManifestBytes()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        byte[] modifiedBytes = signedManifest.manifestBytes();
        modifiedBytes[0] ^= 1;

        assertFalse(
                ManifestSignatureService.verify(
                        modifiedBytes,
                        signedManifest.signature(),
                        trustedKeyPair.getPublic()
                )
        );
    }

    @Test
    void shouldRejectModifiedSignature()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        byte[] modifiedSignature = signedManifest.signature();
        modifiedSignature[0] ^= 1;

        assertFalse(
                ManifestSignatureService.verify(
                        signedManifest.manifestBytes(),
                        modifiedSignature,
                        trustedKeyPair.getPublic()
                )
        );
    }

    @Test
    void shouldEncodeAndDecodeSignature()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        String encoded = ManifestSignatureService.encodeSignature(
                signedManifest.signature()
        );

        byte[] decoded =
                ManifestSignatureService.decodeSignature(encoded);

        assertArrayEquals(
                signedManifest.signature(),
                decoded
        );
    }

    @Test
    void shouldRejectInvalidBase64Signature() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestSignatureService.decodeSignature("not-base64!")
        );
    }

    @Test
    void shouldRejectWrongDecodedSignatureLength() {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(new byte[10]);

        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestSignatureService.decodeSignature(encoded)
        );
    }

    @Test
    void shouldProduceDifferentSignaturesForDifferentManifestBytes()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        byte[] modifiedBytes = signedManifest.manifestBytes();
        modifiedBytes[modifiedBytes.length - 1] ^= 1;

        byte[] secondSignature = Ed25519SignatureService.sign(
                modifiedBytes,
                trustedKeyPair.getPrivate()
        );

        assertNotEquals(
                ManifestSignatureService.encodeSignature(
                        signedManifest.signature()
                ),
                ManifestSignatureService.encodeSignature(secondSignature)
        );
    }

    @Test
    void shouldPreserveExactCanonicalBytes()
            throws GeneralSecurityException {
        SignedManifest signedManifest =
                ManifestSignatureService.sign(
                        manifest,
                        trustedKeyPair.getPrivate()
                );

        assertArrayEquals(
                manifest.toCanonicalBytes(),
                signedManifest.manifestBytes()
        );

        assertTrue(
                ManifestSignatureService.verify(
                        signedManifest.manifestBytes(),
                        signedManifest.signature(),
                        trustedKeyPair.getPublic()
                )
        );
    }

    @Test
    void shouldRejectNullManifest() {
        assertThrows(
                NullPointerException.class,
                () -> ManifestSignatureService.sign(
                        null,
                        trustedKeyPair.getPrivate()
                )
        );
    }

    @Test
    void shouldRejectNullPublicKey() {
        assertThrows(
                NullPointerException.class,
                () -> ManifestSignatureService.verify(
                        new SignedManifest(
                                "manifest".getBytes(StandardCharsets.UTF_8),
                                new byte[64]
                        ),
                        null
                )
        );
    }
}
