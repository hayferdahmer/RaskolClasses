// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import dev.raskol.classes.integrity.manifest.Sha256HashService;
import dev.raskol.classes.integrity.verification.IntegrityResourceProvider;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.integrity.verification.StartupIntegrityVerifier;

class TrustedKeyLifecycleIntegrationTest {

    private static final String MANIFEST_RESOURCE = StartupIntegrityVerifier.MANIFEST_RESOURCE;

    private static final String SIGNATURE_RESOURCE = StartupIntegrityVerifier.SIGNATURE_RESOURCE;

    private static final String PROTECTED_PATH = "dev/raskol/classes/Example.class";

    private static final byte[] PROTECTED_CONTENT =
            "raskol-key-lifecycle-integration-test".getBytes(StandardCharsets.UTF_8);

    private static final BuildIdentity BUILD_IDENTITY = new BuildIdentity(
            "RaskolClasses",
            "1.15.8",
            "BUILD-KEY-LIFECYCLE-INTEGRATION",
            "0123456789abcdef",
            Instant.parse("2026-11-07T10:00:00Z"),
            "https://github.com/hayferdahmer/RaskolClasses",
            BuildType.OFFICIAL);

    private KeyPair activeKeyPair;

    private KeyPair verifyOnlyKeyPair;

    private KeyPair revokedKeyPair;

    private Map<String, byte[]> resources;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        activeKeyPair = Ed25519SignatureService.generateKeyPair();
        verifyOnlyKeyPair = Ed25519SignatureService.generateKeyPair();
        revokedKeyPair = Ed25519SignatureService.generateKeyPair();

        resources = new HashMap<>();
    }

    @Test
    void shouldAcceptSignatureFromActiveKey() throws GeneralSecurityException {

        installManifest(activeKeyPair);

        List<TrustedPublicKeyEntry> entries = entries(activeKeyPair, TrustedKeyStatus.ACTIVE);

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
    }

    @Test
    void shouldAcceptSignatureFromVerifyOnlyKey() throws GeneralSecurityException {

        installManifest(verifyOnlyKeyPair);

        List<TrustedPublicKeyEntry> entries = entries(verifyOnlyKeyPair, TrustedKeyStatus.VERIFY_ONLY);

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
    }

    @Test
    void shouldRejectSignatureFromRevokedKey() throws GeneralSecurityException {

        installManifest(revokedKeyPair);

        List<TrustedPublicKeyEntry> entries = entries(revokedKeyPair, TrustedKeyStatus.REVOKED);

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, result.status());

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldRejectRevokedKeyWhenAnotherKeyRemainsActive() throws GeneralSecurityException {

        installManifest(revokedKeyPair);

        List<TrustedPublicKeyEntry> entries = TrustedPublicKeyRegistry.decodePinnedEntries(List.of(
                configuration(activeKeyPair, TrustedKeyStatus.ACTIVE),
                configuration(revokedKeyPair, TrustedKeyStatus.REVOKED)));

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.INVALID_SIGNATURE, result.status());

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    @Test
    void shouldAcceptActiveKeyWhenRevokedKeyIsAlsoPresent() throws GeneralSecurityException {

        installManifest(activeKeyPair);

        List<TrustedPublicKeyEntry> entries = TrustedPublicKeyRegistry.decodePinnedEntries(List.of(
                configuration(revokedKeyPair, TrustedKeyStatus.REVOKED),
                configuration(activeKeyPair, TrustedKeyStatus.ACTIVE)));

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
    }

    @Test
    void shouldAcceptVerifyOnlyKeyDuringRotation() throws GeneralSecurityException {

        installManifest(verifyOnlyKeyPair);

        List<TrustedPublicKeyEntry> entries = TrustedPublicKeyRegistry.decodePinnedEntries(List.of(
                configuration(activeKeyPair, TrustedKeyStatus.ACTIVE),
                configuration(verifyOnlyKeyPair, TrustedKeyStatus.VERIFY_ONLY)));

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
    }

    @Test
    void shouldRejectOldSignatureAfterOldKeyIsRevoked() throws GeneralSecurityException {

        installManifest(verifyOnlyKeyPair);

        List<TrustedPublicKeyEntry> beforeRevocation = TrustedPublicKeyRegistry.decodePinnedEntries(List.of(
                configuration(activeKeyPair, TrustedKeyStatus.ACTIVE),
                configuration(verifyOnlyKeyPair, TrustedKeyStatus.VERIFY_ONLY)));

        IntegrityVerificationResult beforeResult =
                createVerifier(beforeRevocation).verify();

        assertEquals(IntegrityStatus.VERIFIED, beforeResult.status());

        List<TrustedPublicKeyEntry> afterRevocation = TrustedPublicKeyRegistry.decodePinnedEntries(List.of(
                configuration(activeKeyPair, TrustedKeyStatus.ACTIVE),
                configuration(verifyOnlyKeyPair, TrustedKeyStatus.REVOKED)));

        IntegrityVerificationResult afterResult =
                createVerifier(afterRevocation).verify();

        assertEquals(IntegrityStatus.INVALID_SIGNATURE, afterResult.status());

        assertFalse(afterResult.isVerified());
        assertEquals(0, afterResult.checkedFiles());
    }

    @Test
    void shouldReturnTrustedKeyUnavailableWhenAllKeysAreRevoked() throws GeneralSecurityException {

        installManifest(activeKeyPair);

        List<TrustedPublicKeyEntry> entries = TrustedPublicKeyRegistry.decodePinnedEntries(List.of(
                configuration(activeKeyPair, TrustedKeyStatus.REVOKED),
                configuration(verifyOnlyKeyPair, TrustedKeyStatus.REVOKED)));

        IntegrityVerificationResult result = createVerifier(entries).verify();

        assertEquals(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, result.status());

        assertFalse(result.isVerified());
        assertEquals(0, result.checkedFiles());
    }

    private List<TrustedPublicKeyEntry> entries(KeyPair keyPair, TrustedKeyStatus status)
            throws GeneralSecurityException {

        return TrustedPublicKeyRegistry.decodePinnedEntries(List.of(configuration(keyPair, status)));
    }

    private TrustedPublicKeyRegistry.PinnedKeyConfiguration configuration(KeyPair keyPair, TrustedKeyStatus status) {

        return new TrustedPublicKeyRegistry.PinnedKeyConfiguration(
                java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()), status);
    }

    private StartupIntegrityVerifier createVerifier(List<TrustedPublicKeyEntry> entries) {

        List<java.security.PublicKey> verificationKeys = TrustedPublicKeyRegistry.verificationKeys(entries);

        IntegrityResourceProvider provider = resourcePath -> {
            byte[] content = resources.get(resourcePath);

            if (content == null) {
                return null;
            }

            return new ByteArrayInputStream(content);
        };

        return new StartupIntegrityVerifier(provider, BUILD_IDENTITY, null, verificationKeys);
    }

    private void installManifest(KeyPair signingKey) throws GeneralSecurityException {

        ManifestFileEntry entry = new ManifestFileEntry(
                PROTECTED_PATH, PROTECTED_CONTENT.length, Sha256HashService.hash(PROTECTED_CONTENT));

        IntegrityManifest manifest = new IntegrityManifest(BUILD_IDENTITY, List.of(entry));

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature =
                ManifestSignatureService.sign(manifest, signingKey.getPrivate()).signature();

        resources.put(MANIFEST_RESOURCE, manifestBytes);

        resources.put(
                SIGNATURE_RESOURCE,
                (ManifestSignatureService.encodeSignature(signature) + "\n").getBytes(StandardCharsets.US_ASCII));

        resources.put("/" + PROTECTED_PATH, PROTECTED_CONTENT.clone());
    }
}
