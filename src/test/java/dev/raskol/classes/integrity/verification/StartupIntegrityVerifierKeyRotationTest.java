// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.verification;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import dev.raskol.classes.integrity.manifest.Sha256HashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupIntegrityVerifierKeyRotationTest {

    private static final String MANIFEST_RESOURCE =
            StartupIntegrityVerifier.MANIFEST_RESOURCE;

    private static final String SIGNATURE_RESOURCE =
            StartupIntegrityVerifier.SIGNATURE_RESOURCE;

    private static final String PROTECTED_PATH =
            "dev/raskol/classes/Example.class";

    private static final byte[] PROTECTED_CONTENT =
            "raskol-key-rotation-test"
                    .getBytes(StandardCharsets.UTF_8);

    private static final BuildIdentity BUILD_IDENTITY =
            new BuildIdentity(
                    "RaskolClasses",
                    "1.15.8",
                    "BUILD-ROTATION-TEST",
                    "0123456789abcdef",
                    Instant.parse("2026-11-07T10:00:00Z"),
                    "https://github.com/hayferdahmer/RaskolClasses",
                    BuildType.OFFICIAL
            );

    private KeyPair oldKeyPair;

    private KeyPair newKeyPair;

    private KeyPair unknownKeyPair;

    private Map<String, byte[]> resources;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        oldKeyPair = Ed25519SignatureService.generateKeyPair();
        newKeyPair = Ed25519SignatureService.generateKeyPair();
        unknownKeyPair = Ed25519SignatureService.generateKeyPair();

        resources = new HashMap<>();
    }

    @Test
    void shouldVerifyManifestSignedWithOldKey()
            throws GeneralSecurityException {

        installManifest(oldKeyPair);

        IntegrityVerificationResult result =
                createVerifier().verify();

        assertEquals(
                IntegrityStatus.VERIFIED,
                result.status()
        );

        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldVerifyManifestSignedWithNewKey()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        IntegrityVerificationResult result =
                createVerifier().verify();

        assertEquals(
                IntegrityStatus.VERIFIED,
                result.status()
        );

        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldRejectManifestSignedWithUnknownKey()
            throws GeneralSecurityException {

        installManifest(unknownKeyPair);

        IntegrityVerificationResult result =
                createVerifier().verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldRejectModifiedManifestSignedWithNewKey()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        byte[] modifiedManifest =
                resources.get(MANIFEST_RESOURCE).clone();

        modifiedManifest[0] ^= 1;

        resources.put(
                MANIFEST_RESOURCE,
                modifiedManifest
        );

        IntegrityVerificationResult result =
                createVerifier().verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
    }

    private StartupIntegrityVerifier createVerifier() {

        IntegrityResourceProvider provider = resourcePath -> {
            byte[] content = resources.get(resourcePath);

            if (content == null) {
                return null;
            }

            return new ByteArrayInputStream(content);
        };

        return new StartupIntegrityVerifier(
                provider,
                BUILD_IDENTITY,
                null,
                List.of(
                        oldKeyPair.getPublic(),
                        newKeyPair.getPublic()
                )
        );
    }

    private void installManifest(KeyPair signingKey)
            throws GeneralSecurityException {

        ManifestFileEntry entry = new ManifestFileEntry(
                PROTECTED_PATH,
                PROTECTED_CONTENT.length,
                Sha256HashService.hash(PROTECTED_CONTENT)
        );

        IntegrityManifest manifest = new IntegrityManifest(
                BUILD_IDENTITY,
                List.of(entry)
        );

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature = ManifestSignatureService.sign(
                manifest,
                signingKey.getPrivate()
        ).signature();

        resources.put(
                MANIFEST_RESOURCE,
                manifestBytes
        );

        resources.put(
                SIGNATURE_RESOURCE,
                (
                        ManifestSignatureService.encodeSignature(signature)
                                + "\n"
                ).getBytes(StandardCharsets.US_ASCII)
        );

        resources.put(
                "/" + PROTECTED_PATH,
                PROTECTED_CONTENT.clone()
        );
    }
}
