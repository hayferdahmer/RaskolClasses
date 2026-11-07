// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.verification;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.build.DevelopmentMode;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import dev.raskol.classes.integrity.manifest.Sha256HashService;

class StartupIntegrityVerifierTest {

    private static final String MANIFEST_RESOURCE = "/META-INF/raskol/integrity/manifest.rips";

    private static final String SIGNATURE_RESOURCE = "/META-INF/raskol/integrity/manifest.sig";

    private static final String PROTECTED_FILE = "dev/raskol/classes/Example.class";

    private static final byte[] PROTECTED_CONTENT = "raskol-protected-content".getBytes(StandardCharsets.UTF_8);

    private static final BuildIdentity OFFICIAL_IDENTITY = new BuildIdentity(
            "RaskolClasses",
            "1.15.8",
            "BUILD-TEST-001",
            "0123456789abcdef",
            Instant.parse("2026-11-07T10:00:00Z"),
            "https://github.com/hayferdahmer/RaskolClasses",
            BuildType.OFFICIAL);

    private KeyPair trustedKeyPair;

    private Map<String, byte[]> resources;

    private String previousDevelopmentProperty;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        previousDevelopmentProperty = System.getProperty(DevelopmentMode.PROPERTY_NAME);

        System.clearProperty(DevelopmentMode.PROPERTY_NAME);

        trustedKeyPair = Ed25519SignatureService.generateKeyPair();
        resources = new HashMap<>();
    }

    @AfterEach
    void restoreDevelopmentProperty() {
        if (previousDevelopmentProperty == null) {
            System.clearProperty(DevelopmentMode.PROPERTY_NAME);
        } else {
            System.setProperty(DevelopmentMode.PROPERTY_NAME, previousDevelopmentProperty);
        }
    }

    @Test
    void shouldVerifyValidSignedManifest() throws GeneralSecurityException {

        installValidManifest();

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertFalse(result.isDevelopmentBuild());
        assertEquals(1, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldRejectDevelopmentBuildWithoutExplicitJvmProperty() {
        BuildIdentity developmentIdentity = BuildIdentity.development("1.15.8-SNAPSHOT");

        IntegrityVerificationResult result = verifier(developmentIdentity, null).verify();

        assertEquals(IntegrityStatus.DEVELOPMENT_MODE_REQUIRED, result.status());

        assertFalse(result.isDevelopmentBuild());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldAllowDevelopmentBuildWhenExplicitJvmPropertyIsEnabled() {
        System.setProperty(DevelopmentMode.PROPERTY_NAME, "true");

        BuildIdentity developmentIdentity = BuildIdentity.development("1.15.8-SNAPSHOT");

        IntegrityVerificationResult result = verifier(developmentIdentity, null).verify();

        assertEquals(IntegrityStatus.DEVELOPMENT_BUILD, result.status());

        assertTrue(result.isDevelopmentBuild());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldRejectDevelopmentBuildWhenJvmPropertyIsFalse() {
        System.setProperty(DevelopmentMode.PROPERTY_NAME, "false");

        BuildIdentity developmentIdentity = BuildIdentity.development("1.15.8-SNAPSHOT");

        IntegrityVerificationResult result = verifier(developmentIdentity, null).verify();

        assertEquals(IntegrityStatus.DEVELOPMENT_MODE_REQUIRED, result.status());
    }

    @Test
    void shouldRejectMissingManifest() throws GeneralSecurityException {

        resources.put(
                SIGNATURE_RESOURCE,
                encodeSignature(Ed25519SignatureService.sign(new byte[] {1}, trustedKeyPair.getPrivate())));

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.MANIFEST_MISSING, result.status());
    }

    @Test
    void shouldRejectMissingSignature() throws GeneralSecurityException {

        IntegrityManifest manifest = createManifest(OFFICIAL_IDENTITY, PROTECTED_FILE, PROTECTED_CONTENT);

        resources.put(MANIFEST_RESOURCE, manifest.toCanonicalBytes());

        resources.put("/" + PROTECTED_FILE, PROTECTED_CONTENT.clone());

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.SIGNATURE_MISSING, result.status());
    }

    @Test
    void shouldRejectInvalidSignature() throws GeneralSecurityException {

        installValidManifest();

        resources.put(SIGNATURE_RESOURCE, encodeSignature(new byte[64]));

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.INVALID_SIGNATURE, result.status());
    }

    @Test
    void shouldRejectInvalidSignedManifest() throws GeneralSecurityException {

        byte[] malformedManifest = "NOT-A-VALID-MANIFEST".getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(malformedManifest, trustedKeyPair.getPrivate());

        putManifest(malformedManifest, signature);

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.INVALID_MANIFEST, result.status());
    }

    @Test
    void shouldRejectBuildIdentityMismatch() throws GeneralSecurityException {

        BuildIdentity differentIdentity = new BuildIdentity(
                "RaskolClasses",
                "1.15.7",
                "BUILD-OLD-001",
                "0123456789abcdef",
                Instant.parse("2026-11-06T10:00:00Z"),
                "https://github.com/hayferdahmer/RaskolClasses",
                BuildType.OFFICIAL);

        installManifest(differentIdentity, PROTECTED_FILE, PROTECTED_CONTENT);

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.BUILD_IDENTITY_MISMATCH, result.status());
    }

    @Test
    void shouldRejectMissingProtectedFile() throws GeneralSecurityException {

        IntegrityManifest manifest = createManifest(OFFICIAL_IDENTITY, PROTECTED_FILE, PROTECTED_CONTENT);

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature = Ed25519SignatureService.sign(manifestBytes, trustedKeyPair.getPrivate());

        putManifest(manifestBytes, signature);

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.PROTECTED_FILE_MISSING, result.status());

        assertEquals(List.of(PROTECTED_FILE), result.failedFiles());
    }

    @Test
    void shouldRejectModifiedProtectedFile() throws GeneralSecurityException {

        installValidManifest();

        byte[] modifiedContent = PROTECTED_CONTENT.clone();
        modifiedContent[0] ^= 1;

        resources.put("/" + PROTECTED_FILE, modifiedContent);

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH, result.status());

        assertEquals(List.of(PROTECTED_FILE), result.failedFiles());
    }

    @Test
    void shouldRejectProtectedFileSizeMismatch() throws GeneralSecurityException {

        installValidManifest();

        byte[] modifiedContent = ("different-content-with-another-size").getBytes(StandardCharsets.UTF_8);

        resources.put("/" + PROTECTED_FILE, modifiedContent);

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.PROTECTED_FILE_SIZE_MISMATCH, result.status());
    }

    @Test
    void shouldRejectEmptyManifest() throws GeneralSecurityException {

        IntegrityManifest manifest = new IntegrityManifest(OFFICIAL_IDENTITY, List.of());

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature = Ed25519SignatureService.sign(manifestBytes, trustedKeyPair.getPrivate());

        putManifest(manifestBytes, signature);

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.INVALID_MANIFEST, result.status());
    }

    @Test
    void shouldRejectUnavailableTrustedKey() throws GeneralSecurityException {

        installValidManifest();

        IntegrityVerificationResult result = verifier(OFFICIAL_IDENTITY, null).verify();

        assertEquals(IntegrityStatus.TRUSTED_KEY_UNAVAILABLE, result.status());
    }

    @Test
    void shouldVerifyMultipleProtectedFiles() throws GeneralSecurityException {

        String secondPath = "dev/raskol/classes/AnotherExample.class";

        byte[] secondContent = "another-protected-content".getBytes(StandardCharsets.UTF_8);

        IntegrityManifest manifest = new IntegrityManifest(
                OFFICIAL_IDENTITY, List.of(entry(PROTECTED_FILE, PROTECTED_CONTENT), entry(secondPath, secondContent)));

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature = Ed25519SignatureService.sign(manifestBytes, trustedKeyPair.getPrivate());

        putManifest(manifestBytes, signature);

        resources.put("/" + PROTECTED_FILE, PROTECTED_CONTENT.clone());

        resources.put("/" + secondPath, secondContent.clone());

        IntegrityVerificationResult result =
                verifier(OFFICIAL_IDENTITY, trustedKeyPair.getPublic()).verify();

        assertEquals(IntegrityStatus.VERIFIED, result.status());
        assertEquals(2, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    private void installValidManifest() throws GeneralSecurityException {

        installManifest(OFFICIAL_IDENTITY, PROTECTED_FILE, PROTECTED_CONTENT);
    }

    private void installManifest(BuildIdentity identity, String path, byte[] content) throws GeneralSecurityException {

        IntegrityManifest manifest = createManifest(identity, path, content);

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature = Ed25519SignatureService.sign(manifestBytes, trustedKeyPair.getPrivate());

        putManifest(manifestBytes, signature);

        resources.put("/" + path, content.clone());
    }

    private IntegrityManifest createManifest(BuildIdentity identity, String path, byte[] content) {

        return new IntegrityManifest(identity, List.of(entry(path, content)));
    }

    private ManifestFileEntry entry(String path, byte[] content) {

        return new ManifestFileEntry(path, content.length, Sha256HashService.hash(content));
    }

    private void putManifest(byte[] manifestBytes, byte[] signature) {

        resources.put(MANIFEST_RESOURCE, manifestBytes.clone());

        resources.put(SIGNATURE_RESOURCE, encodeSignature(signature));
    }

    private StartupIntegrityVerifier verifier(BuildIdentity identity, java.security.PublicKey publicKey) {

        IntegrityResourceProvider provider = resourcePath -> {
            byte[] content = resources.get(resourcePath);

            if (content == null) {
                return null;
            }

            return new ByteArrayInputStream(content);
        };

        return new StartupIntegrityVerifier(provider, identity, publicKey);
    }

    private static byte[] encodeSignature(byte[] signature) {
        return (ManifestSignatureService.encodeSignature(signature) + "\n").getBytes(StandardCharsets.US_ASCII);
    }
}
