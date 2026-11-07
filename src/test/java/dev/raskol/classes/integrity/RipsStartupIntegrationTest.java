// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import dev.raskol.classes.integrity.manifest.Sha256HashService;
import dev.raskol.classes.integrity.policy.IntegrityFailureDecision;
import dev.raskol.classes.integrity.policy.IntegrityFailurePolicy;
import dev.raskol.classes.integrity.reporting.SecurityReport;
import dev.raskol.classes.integrity.reporting.SecurityReporter;
import dev.raskol.classes.integrity.reporting.SecuritySeverity;
import dev.raskol.classes.integrity.verification.IntegrityResourceProvider;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.integrity.verification.StartupIntegrityVerifier;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests for the RIPS startup verification pipeline.
 *
 * Tests the interaction between:
 * - StartupIntegrityVerifier
 * - SecurityReporter
 * - IntegrityFailurePolicy
 *
 * No Paper server or permanent signing keys are required.
 */
class RipsStartupIntegrationTest {

    private static final String PROTECTED_FILE =
            "dev/raskol/classes/Example.class";

    private static final byte[] ORIGINAL_CONTENT =
            "raskol-protected-class-content"
                    .getBytes(StandardCharsets.UTF_8);

    private static final BuildIdentity OFFICIAL_IDENTITY =
            new BuildIdentity(
                    "RaskolClasses",
                    "1.15.8",
                    "BUILD-INTEGRATION-001",
                    "0123456789abcdef",
                    Instant.parse("2026-11-07T10:00:00Z"),
                    "https://github.com/hayferdahmer/RaskolClasses",
                    BuildType.OFFICIAL
            );

    private KeyPair trustedKeyPair;

    private Map<String, byte[]> resources;

    private IntegrityFailurePolicy failurePolicy;

    private SecurityReporter securityReporter;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        trustedKeyPair = Ed25519SignatureService.generateKeyPair();

        resources = new HashMap<>();

        failurePolicy = new IntegrityFailurePolicy();

        Logger logger = Logger.getLogger(
                "RIPS-Integration-Test-" + System.nanoTime()
        );

        logger.setUseParentHandlers(false);

        securityReporter = new SecurityReporter(logger);
    }

    @Test
    void shouldAllowVerifiedOfficialBuild()
            throws GeneralSecurityException {

        installSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_CONTENT
        );

        IntegrityVerificationResult result = verify(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        );

        SecurityReport report = securityReporter.report(result);

        assertEquals(
                IntegrityStatus.VERIFIED,
                result.status()
        );

        assertEquals(
                SecuritySeverity.INFO,
                report.severity()
        );

        assertEquals(
                IntegrityFailureDecision.CONTINUE,
                failurePolicy.evaluate(result)
        );

        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldAllowDevelopmentBuild()
            throws GeneralSecurityException {

        BuildIdentity developmentIdentity =
                BuildIdentity.development("1.15.8-SNAPSHOT");

        IntegrityVerificationResult result = verify(
                developmentIdentity,
                null
        );

        SecurityReport report = securityReporter.report(result);

        assertEquals(
                IntegrityStatus.DEVELOPMENT_BUILD,
                result.status()
        );

        assertEquals(
                SecuritySeverity.INFO,
                report.severity()
        );

        assertEquals(
                IntegrityFailureDecision.CONTINUE,
                failurePolicy.evaluate(result)
        );

        assertTrue(result.isDevelopmentBuild());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldRejectBuildWithModifiedProtectedFile()
            throws GeneralSecurityException {

        installSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_CONTENT
        );

        byte[] modifiedContent = ORIGINAL_CONTENT.clone();
        modifiedContent[0] ^= 1;

        resources.put(
                "/" + PROTECTED_FILE,
                modifiedContent
        );

        IntegrityVerificationResult result = verify(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        );

        SecurityReport report = securityReporter.report(result);

        assertEquals(
                IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH,
                result.status()
        );

        assertEquals(
                SecuritySeverity.CRITICAL,
                report.severity()
        );

        assertEquals(
                IntegrityFailureDecision.DISABLE_PLUGIN,
                failurePolicy.evaluate(result)
        );

        assertEquals(
                List.of(PROTECTED_FILE),
                result.failedFiles()
        );
    }

    @Test
    void shouldRejectBuildWithInvalidSignature()
            throws GeneralSecurityException {

        installSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_CONTENT
        );

        byte[] invalidSignature = new byte[64];

        resources.put(
                StartupIntegrityVerifier.SIGNATURE_RESOURCE,
                encodeSignature(invalidSignature)
        );

        IntegrityVerificationResult result = verify(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        );

        SecurityReport report = securityReporter.report(result);

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );

        assertEquals(
                SecuritySeverity.CRITICAL,
                report.severity()
        );

        assertEquals(
                IntegrityFailureDecision.DISABLE_PLUGIN,
                failurePolicy.evaluate(result)
        );
    }

    @Test
    void shouldRejectBuildWhenTrustedKeyIsUnavailable()
            throws GeneralSecurityException {

        installSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_CONTENT
        );

        IntegrityVerificationResult result = verify(
                OFFICIAL_IDENTITY,
                null
        );

        SecurityReport report = securityReporter.report(result);

        assertEquals(
                IntegrityStatus.TRUSTED_KEY_UNAVAILABLE,
                result.status()
        );

        assertEquals(
                SecuritySeverity.WARNING,
                report.severity()
        );

        assertEquals(
                IntegrityFailureDecision.DISABLE_PLUGIN,
                failurePolicy.evaluate(result)
        );
    }

    @Test
    void shouldRejectBuildWithIdentityMismatch()
            throws GeneralSecurityException {

        BuildIdentity differentIdentity =
                new BuildIdentity(
                        "RaskolClasses",
                        "1.15.7",
                        "BUILD-OLD-001",
                        "0123456789abcdef",
                        Instant.parse("2026-11-06T10:00:00Z"),
                        "https://github.com/hayferdahmer/RaskolClasses",
                        BuildType.OFFICIAL
                );

        installSignedManifest(
                differentIdentity,
                ORIGINAL_CONTENT
        );

        IntegrityVerificationResult result = verify(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        );

        SecurityReport report = securityReporter.report(result);

        assertEquals(
                IntegrityStatus.BUILD_IDENTITY_MISMATCH,
                result.status()
        );

        assertEquals(
                SecuritySeverity.CRITICAL,
                report.severity()
        );

        assertEquals(
                IntegrityFailureDecision.DISABLE_PLUGIN,
                failurePolicy.evaluate(result)
        );
    }

    private void installSignedManifest(
            BuildIdentity identity,
            byte[] fileContent
    ) throws GeneralSecurityException {

        IntegrityManifest manifest = new IntegrityManifest(
                identity,
                List.of(
                        new ManifestFileEntry(
                                PROTECTED_FILE,
                                fileContent.length,
                                Sha256HashService.hash(fileContent)
                        )
                )
        );

        byte[] manifestBytes = manifest.toCanonicalBytes();

        byte[] signature = Ed25519SignatureService.sign(
                manifestBytes,
                trustedKeyPair.getPrivate()
        );

        resources.put(
                StartupIntegrityVerifier.MANIFEST_RESOURCE,
                manifestBytes
        );

        resources.put(
                StartupIntegrityVerifier.SIGNATURE_RESOURCE,
                encodeSignature(signature)
        );

        resources.put(
                "/" + PROTECTED_FILE,
                fileContent.clone()
        );
    }

    private IntegrityVerificationResult verify(
            BuildIdentity identity,
            java.security.PublicKey publicKey
    ) {

        IntegrityResourceProvider resourceProvider = resourcePath -> {
            byte[] content = resources.get(resourcePath);

            if (content == null) {
                return null;
            }

            return new ByteArrayInputStream(content);
        };

        StartupIntegrityVerifier verifier =
                new StartupIntegrityVerifier(
                        resourceProvider,
                        identity,
                        publicKey
                );

        return verifier.verify();
    }

    private static byte[] encodeSignature(byte[] signature) {
        return (
                ManifestSignatureService.encodeSignature(signature) + "\n"
        ).getBytes(StandardCharsets.US_ASCII);
    }
}
