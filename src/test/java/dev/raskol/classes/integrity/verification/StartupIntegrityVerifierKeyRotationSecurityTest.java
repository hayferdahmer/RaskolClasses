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
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupIntegrityVerifierKeyRotationSecurityTest {

    private static final String MANIFEST_RESOURCE =
            StartupIntegrityVerifier.MANIFEST_RESOURCE;

    private static final String SIGNATURE_RESOURCE =
            StartupIntegrityVerifier.SIGNATURE_RESOURCE;

    private static final String PROTECTED_PATH =
            "dev/raskol/classes/Example.class";

    private static final byte[] PROTECTED_CONTENT =
            "raskol-key-rotation-security-test"
                    .getBytes(StandardCharsets.UTF_8);

    private static final BuildIdentity BUILD_IDENTITY =
            new BuildIdentity(
                    "RaskolClasses",
                    "1.15.8",
                    "BUILD-ROTATION-SECURITY-TEST",
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
    void shouldVerifyWithMatchingKeyWhenItIsNotFirstInRegistry()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        IntegrityVerificationResult result = createVerifier(
                List.of(
                        unknownKeyPair.getPublic(),
                        oldKeyPair.getPublic(),
                        newKeyPair.getPublic()
                )
        ).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
    }

    @Test
    void shouldRejectOldSignatureWhenOldKeyIsNoLongerTrusted()
            throws GeneralSecurityException {

        installManifest(oldKeyPair);

        IntegrityVerificationResult result = createVerifier(
                List.of(newKeyPair.getPublic())
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldRejectNewSignatureWhenNewKeyIsNotTrusted()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        IntegrityVerificationResult result = createVerifier(
                List.of(oldKeyPair.getPublic())
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldRejectSignatureModifiedByOneBit()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        String encodedSignature = new String(
                resources.get(SIGNATURE_RESOURCE),
                StandardCharsets.US_ASCII
        ).strip();

        byte[] modifiedSignature =
                ManifestSignatureService.decodeSignature(encodedSignature);

        modifiedSignature[0] ^= 1;

        resources.put(
                SIGNATURE_RESOURCE,
                (
                        ManifestSignatureService.encodeSignature(
                                modifiedSignature
                        ) + "\n"
                ).getBytes(StandardCharsets.US_ASCII)
        );

        IntegrityVerificationResult result = createVerifier(
                List.of(oldKeyPair.getPublic(), newKeyPair.getPublic())
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldRejectSignatureCopiedFromAnotherManifest()
            throws GeneralSecurityException {

        installManifest(oldKeyPair);

        byte[] oldManifestSignature =
                resources.get(SIGNATURE_RESOURCE).clone();

        installManifest(newKeyPair);

        /*
         * The manifest is now signed by the new key,
         * but the detached signature belongs to the old manifest.
         */
        resources.put(
                SIGNATURE_RESOURCE,
                oldManifestSignature
        );

        IntegrityVerificationResult result = createVerifier(
                List.of(oldKeyPair.getPublic(), newKeyPair.getPublic())
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldRejectEmptyTrustedKeyList()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        IntegrityVerificationResult result = createVerifier(
                List.of()
        ).verify();

        assertEquals(
                IntegrityStatus.TRUSTED_KEY_UNAVAILABLE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldNotReadProtectedFilesWhenSignatureIsInvalid()
            throws GeneralSecurityException {

        installManifest(newKeyPair);

        String encodedSignature = new String(
                resources.get(SIGNATURE_RESOURCE),
                StandardCharsets.US_ASCII
        ).strip();

        byte[] modifiedSignature =
                ManifestSignatureService.decodeSignature(encodedSignature);

        modifiedSignature[0] ^= 1;

        resources.put(
                SIGNATURE_RESOURCE,
                (
                        ManifestSignatureService.encodeSignature(
                                modifiedSignature
                        ) + "\n"
                ).getBytes(StandardCharsets.US_ASCII)
        );

        AtomicInteger protectedFileReadAttempts =
                new AtomicInteger();

        IntegrityVerificationResult result = createVerifier(
                List.of(oldKeyPair.getPublic(), newKeyPair.getPublic()),
                protectedFileReadAttempts
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());

        assertEquals(
                0,
                protectedFileReadAttempts.get(),
                "Protected files must not be read before signature verification"
        );
    }

    private StartupIntegrityVerifier createVerifier(
            List<PublicKey> trustedKeys
    ) {
        return createVerifier(
                trustedKeys,
                new AtomicInteger()
        );
    }

    private StartupIntegrityVerifier createVerifier(
            List<PublicKey> trustedKeys,
            AtomicInteger protectedFileReadAttempts
    ) {

        IntegrityResourceProvider provider = resourcePath -> {
            if (("/" + PROTECTED_PATH).equals(resourcePath)) {
                protectedFileReadAttempts.incrementAndGet();
            }

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
                trustedKeys
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
