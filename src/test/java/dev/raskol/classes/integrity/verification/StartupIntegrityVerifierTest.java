
package dev.raskol.classes.integrity.verification;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

class StartupIntegrityVerifierTest {

    private static final String FILE_PATH =
            "dev/raskol/classes/Example.class";

    private static final byte[] ORIGINAL_FILE =
            "raskol-protected-class-content"
                    .getBytes(StandardCharsets.UTF_8);

    private static final BuildIdentity OFFICIAL_IDENTITY =
            new BuildIdentity(
                    "RaskolClasses",
                    "1.15.8",
                    "BUILD-TEST-001",
                    "0123456789abcdef",
                    Instant.parse("2026-11-07T10:00:00Z"),
                    "https://github.com/hayferdahmer/RaskolClasses",
                    BuildType.OFFICIAL
            );

    private KeyPair trustedKeyPair;
    private KeyPair foreignKeyPair;

    private Map<String, byte[]> resources;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        trustedKeyPair =
                Ed25519SignatureService.generateKeyPair();

        foreignKeyPair =
                Ed25519SignatureService.generateKeyPair();

        resources = new HashMap<>();
    }

    @Test
    void shouldVerifyValidOfficialBuild()
            throws GeneralSecurityException {

        installValidSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.VERIFIED,
                result.status()
        );

        assertTrue(result.isVerified());
        assertEquals(1, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldRecognizeDevelopmentBuild() {

        BuildIdentity development =
                BuildIdentity.development("1.15.8-SNAPSHOT");

        IntegrityVerificationResult result = verifier(
                development,
                null
        ).verify();

        assertEquals(
                IntegrityStatus.DEVELOPMENT_BUILD,
                result.status()
        );

        assertTrue(result.isDevelopmentBuild());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldRejectMissingManifest() {

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.MANIFEST_MISSING,
                result.status()
        );
    }

    @Test
    void shouldRejectMissingSignature()
            throws GeneralSecurityException {

        IntegrityManifest manifest = createManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        resources.put(
                StartupIntegrityVerifier.MANIFEST_RESOURCE,
                manifest.toCanonicalBytes()
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.SIGNATURE_MISSING,
                result.status()
        );
    }

    @Test
    void shouldRejectInvalidSignature()
            throws GeneralSecurityException {

        installValidSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        byte[] invalidSignature =
                new byte[64];

        resources.put(
                StartupIntegrityVerifier.SIGNATURE_RESOURCE,
                encodeSignature(invalidSignature)
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );
    }

    @Test
    void shouldRejectSignatureFromForeignKey()
            throws GeneralSecurityException {

        IntegrityManifest manifest = createManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        byte[] signature = Ed25519SignatureService.sign(
                manifest.toCanonicalBytes(),
                foreignKeyPair.getPrivate()
        );

        putManifest(manifest.toCanonicalBytes(), signature);

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );
    }

    @Test
    void shouldVerifySignatureBeforeParsingManifest()
            throws GeneralSecurityException {

        byte[] malformedManifest =
                "NOT-A-VALID-MANIFEST"
                        .getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(
                malformedManifest,
                foreignKeyPair.getPrivate()
        );

        putManifest(malformedManifest, signature);

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_SIGNATURE,
                result.status()
        );
    }

    @Test
    void shouldRejectInvalidSignedManifest()
            throws GeneralSecurityException {

        byte[] malformedManifest =
                "NOT-A-VALID-MANIFEST"
                        .getBytes(StandardCharsets.UTF_8);

        byte[] signature = Ed25519SignatureService.sign(
                malformedManifest,
                trustedKeyPair.getPrivate()
        );

        putManifest(malformedManifest, signature);

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        // Replace the signature with a valid signature from the
        // trusted key so that parsing is reached.
        signature = Ed25519SignatureService.sign(
                malformedManifest,
                trustedKeyPair.getPrivate()
        );

        putManifest(malformedManifest, signature);

        result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_MANIFEST,
                result.status()
        );
    }

    @Test
    void shouldRejectBuildIdentityMismatch()
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

        installValidSignedManifest(
                differentIdentity,
                ORIGINAL_FILE
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.BUILD_IDENTITY_MISMATCH,
                result.status()
        );
    }

    @Test
    void shouldRejectMissingProtectedFile()
            throws GeneralSecurityException {

        installValidSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        resources.remove("/" + FILE_PATH);

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.PROTECTED_FILE_MISSING,
                result.status()
        );

        assertEquals(
                List.of(FILE_PATH),
                result.failedFiles()
        );
    }

    @Test
    void shouldRejectModifiedProtectedFile()
            throws GeneralSecurityException {

        installValidSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        resources.put(
                "/" + FILE_PATH,
                "modified-class-content"
                        .getBytes(StandardCharsets.UTF_8)
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.PROTECTED_FILE_SIZE_MISMATCH,
                result.status()
        );
    }

    @Test
    void shouldRejectModifiedProtectedFileWithSameSize()
            throws GeneralSecurityException {

        installValidSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        byte[] modified = ORIGINAL_FILE.clone();

        modified[0] ^= 1;

        resources.put(
                "/" + FILE_PATH,
                modified
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH,
                result.status()
        );
    }

    @Test
    void shouldRejectEmptyOfficialManifest()
            throws GeneralSecurityException {

        IntegrityManifest manifest = new IntegrityManifest(
                OFFICIAL_IDENTITY,
                List.of()
        );

        byte[] signature = Ed25519SignatureService.sign(
                manifest.toCanonicalBytes(),
                trustedKeyPair.getPrivate()
        );

        putManifest(manifest.toCanonicalBytes(), signature);

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.INVALID_MANIFEST,
                result.status()
        );
    }

    @Test
    void shouldRejectUnavailableTrustedPublicKey()
            throws GeneralSecurityException {

        installValidSignedManifest(
                OFFICIAL_IDENTITY,
                ORIGINAL_FILE
        );

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                null
        ).verify();

        assertEquals(
                IntegrityStatus.TRUSTED_KEY_UNAVAILABLE,
                result.status()
        );
    }

    @Test
    void shouldVerifyMultipleProtectedFiles()
            throws GeneralSecurityException {

        byte[] secondFile =
                "second-protected-file"
                        .getBytes(StandardCharsets.UTF_8);

        IntegrityManifest manifest = new IntegrityManifest(
                OFFICIAL_IDENTITY,
                List.of(
                        new ManifestFileEntry(
                                FILE_PATH,
                                ORIGINAL_FILE.length,
                                dev.raskol.classes.integrity.manifest
                                        .Sha256HashService.hash(ORIGINAL_FILE)
                        ),
                        new ManifestFileEntry(
                                "dev/raskol/classes/Second.class",
                                secondFile.length,
                                dev.raskol.classes.integrity.manifest
                                        .Sha256HashService.hash(secondFile)
                        )
                )
        );

        resources.put(
                "/" + FILE_PATH,
                ORIGINAL_FILE
        );

        resources.put(
                "/dev/raskol/classes/Second.class",
                secondFile
        );

        byte[] signature = Ed25519SignatureService.sign(
                manifest.toCanonicalBytes(),
                trustedKeyPair.getPrivate()
        );

        putManifest(manifest.toCanonicalBytes(), signature);

        IntegrityVerificationResult result = verifier(
                OFFICIAL_IDENTITY,
                trustedKeyPair.getPublic()
        ).verify();

        assertEquals(
                IntegrityStatus.VERIFIED,
                result.status()
        );

        assertEquals(2, result.checkedFiles());
    }

    private void installValidSignedManifest(
            BuildIdentity identity,
            byte[] fileContent
    ) throws GeneralSecurityException {

        IntegrityManifest manifest = createManifest(
                identity,
                fileContent
        );

        resources.put(
                "/" + FILE_PATH,
                fileContent.clone()
        );

        byte[] signature = Ed25519SignatureService.sign(
                manifest.toCanonicalBytes(),
                trustedKeyPair.getPrivate()
        );

        putManifest(manifest.toCanonicalBytes(), signature);
    }

    private IntegrityManifest createManifest(
            BuildIdentity identity,
            byte[] fileContent
    ) {
        return new IntegrityManifest(
                identity,
                List.of(
                        new ManifestFileEntry(
                                FILE_PATH,
                                fileContent.length,
                                dev.raskol.classes.integrity.manifest
                                        .Sha256HashService.hash(fileContent)
                        )
                )
        );
    }

    private void putManifest(
            byte[] manifestBytes,
            byte[] signature
    ) {
        resources.put(
                StartupIntegrityVerifier.MANIFEST_RESOURCE,
                manifestBytes.clone()
        );

        resources.put(
                StartupIntegrityVerifier.SIGNATURE_RESOURCE,
                encodeSignature(signature)
        );
    }

    private static byte[] encodeSignature(byte[] signature) {
        return (
                ManifestSignatureService.encodeSignature(signature) + "\n"
        ).getBytes(StandardCharsets.US_ASCII);
    }

    private StartupIntegrityVerifier verifier(
            BuildIdentity identity,
            java.security.PublicKey publicKey
    ) {
        IntegrityResourceProvider provider = path -> {
            byte[] content = resources.get(path);

            if (content == null) {
                return null;
            }

            return new ByteArrayInputStream(content);
        };

        return new StartupIntegrityVerifier(
                provider,
                identity,
                publicKey
        );
    }
}
