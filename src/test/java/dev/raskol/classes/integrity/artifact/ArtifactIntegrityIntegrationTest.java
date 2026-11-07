// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.crypto.Ed25519SignatureService;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.ManifestSignatureService;
import dev.raskol.classes.integrity.manifest.Sha256HashService;
import dev.raskol.classes.integrity.manifest.SignedManifest;
import dev.raskol.classes.integrity.verification.IntegrityStatus;
import dev.raskol.classes.integrity.verification.IntegrityVerificationResult;
import dev.raskol.classes.integrity.verification.StartupIntegrityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactIntegrityIntegrationTest {

    private static final String MANIFEST_PATH =
            "META-INF/raskol/integrity/manifest.rips";

    private static final String SIGNATURE_PATH =
            "META-INF/raskol/integrity/manifest.sig";

    private static final String CONFIG_PATH = "config.yml";

    private static final String PLUGIN_DESCRIPTOR_PATH = "plugin.yml";

    private static final String UNEXPECTED_PATH = "unexpected.txt";

    @TempDir
    Path temporaryDirectory;

    private KeyPair keyPair;
    private BuildIdentity buildIdentity;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        keyPair = Ed25519SignatureService.generateKeyPair();

        buildIdentity = new BuildIdentity(
                "RaskolClasses",
                "1.15.8",
                "RIPS-INTEGRATION-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.parse("2026-01-01T00:00:00Z"),
                "https://github.com/hayferdahmer/RaskolClasses",
                BuildType.OFFICIAL
        );
    }

    @Test
    void shouldVerifySignedArtifactWithBothVerificationLayers()
            throws Exception {

        Map<String, byte[]> protectedFiles = standardProtectedFiles();

        Path archive = createSignedArchive(
                "valid-artifact.jar",
                protectedFiles,
                Map.of(),
                false
        );

        IntegrityManifest manifest = createManifest(protectedFiles);

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(archive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult startupResult =
                startupVerifier.verify();

        JarArchiveIntegrityVerificationResult archiveResult =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(IntegrityStatus.VERIFIED, startupResult.status());
        assertTrue(startupResult.isVerified());

        assertEquals(ArchiveIntegrityStatus.VERIFIED, archiveResult.status());
        assertTrue(archiveResult.isVerified());

        assertEquals(protectedFiles.size(), startupResult.checkedFiles());
        assertEquals(protectedFiles.size(), archiveResult.checkedFiles());
    }

    @Test
    void shouldRejectModifiedProtectedFile()
            throws Exception {

        Map<String, byte[]> originalFiles = standardProtectedFiles();

        Map<String, byte[]> modifiedFiles = new LinkedHashMap<>(originalFiles);
        modifiedFiles.put(
                CONFIG_PATH,
                bytes("modified configuration")
        );

        Path archive = createSignedArchive(
                "modified-artifact.jar",
                originalFiles,
                modifiedFiles,
                false
        );

        IntegrityManifest originalManifest = createManifest(originalFiles);

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(archive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult startupResult =
                startupVerifier.verify();

        JarArchiveIntegrityVerificationResult archiveResult =
                new JarArchiveIntegrityVerifier(archive)
                        .verify(originalManifest);

        assertEquals(
                IntegrityStatus.PROTECTED_FILE_SIZE_MISMATCH,
                startupResult.status()
        );

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                archiveResult.status()
        );

        assertFalse(startupResult.isVerified());
        assertFalse(archiveResult.isVerified());
    }

    @Test
    void shouldRejectModifiedProtectedFileWithSameSize()
            throws Exception {

        Map<String, byte[]> originalFiles = standardProtectedFiles();

        Map<String, byte[]> modifiedFiles = new LinkedHashMap<>(originalFiles);
        modifiedFiles.put(
                CONFIG_PATH,
                bytes("debug: true \nlanguage: ru\n")
        );

        assertEquals(
                originalFiles.get(CONFIG_PATH).length,
                modifiedFiles.get(CONFIG_PATH).length,
                "Test data must preserve the original file size."
        );

        Path archive = createSignedArchive(
                "modified-same-size-artifact.jar",
                originalFiles,
                modifiedFiles,
                false
        );

        IntegrityManifest originalManifest = createManifest(originalFiles);

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(archive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult startupResult =
                startupVerifier.verify();

        JarArchiveIntegrityVerificationResult archiveResult =
                new JarArchiveIntegrityVerifier(archive)
                        .verify(originalManifest);

        assertEquals(
                IntegrityStatus.PROTECTED_FILE_HASH_MISMATCH,
                startupResult.status()
        );

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_HASH_MISMATCH,
                archiveResult.status()
        );

        assertFalse(startupResult.isVerified());
        assertFalse(archiveResult.isVerified());
    }

    @Test
    void shouldRejectInvalidManifestSignature()
            throws Exception {

        Map<String, byte[]> protectedFiles = standardProtectedFiles();

        Path archive = createSignedArchive(
                "invalid-signature.jar",
                protectedFiles,
                Map.of(),
                true
        );

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(archive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult result = startupVerifier.verify();

        assertEquals(IntegrityStatus.INVALID_SIGNATURE, result.status());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldDetectUnexpectedArchiveEntry()
            throws Exception {

        Map<String, byte[]> protectedFiles = standardProtectedFiles();

        Map<String, byte[]> unexpectedFiles = Map.of(
                UNEXPECTED_PATH,
                bytes("not declared in manifest")
        );

        Path archive = createSignedArchive(
                "unexpected-entry.jar",
                protectedFiles,
                unexpectedFiles,
                false
        );

        IntegrityManifest manifest = createManifest(protectedFiles);

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(archive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult startupResult =
                startupVerifier.verify();

        JarArchiveIntegrityVerificationResult archiveResult =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        /*
         * StartupIntegrityVerifier checks every declared protected file.
         * It does not enumerate the complete archive.
         *
         * JarArchiveIntegrityVerifier performs the complete archive check
         * and therefore must reject the additional entry.
         */
        assertEquals(IntegrityStatus.VERIFIED, startupResult.status());
        assertTrue(startupResult.isVerified());

        assertEquals(
                ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_ENTRY,
                archiveResult.status()
        );

        assertFalse(archiveResult.isVerified());
        assertTrue(archiveResult.failedFiles().contains(UNEXPECTED_PATH));
    }

    @Test
    void shouldRejectArtifactWithMissingSignature()
            throws Exception {

        Map<String, byte[]> protectedFiles = standardProtectedFiles();

        Path archive = createSignedArchiveWithoutSignature(
                "missing-signature.jar",
                protectedFiles
        );

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(archive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult result = startupVerifier.verify();

        assertEquals(IntegrityStatus.SIGNATURE_MISSING, result.status());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldVerifyArtifactLocatedThroughArtifactLocator()
            throws Exception {

        Map<String, byte[]> protectedFiles = standardProtectedFiles();

        Path archive = createSignedArchive(
                "located-artifact.jar",
                protectedFiles,
                Map.of(),
                false
        );

        Path locatedArchive = ArtifactLocator.locate(archive.toUri());

        assertEquals(
                archive.toRealPath(),
                locatedArchive,
                "ArtifactLocator must return the canonical artifact path."
        );

        StartupIntegrityVerifier startupVerifier =
                new StartupIntegrityVerifier(
                        new JarFileIntegrityResourceProvider(locatedArchive),
                        buildIdentity,
                        keyPair.getPublic()
                );

        IntegrityVerificationResult startupResult =
                startupVerifier.verify();

        JarArchiveIntegrityVerificationResult archiveResult =
                new JarArchiveIntegrityVerifier(locatedArchive)
                        .verify(createManifest(protectedFiles));

        assertEquals(IntegrityStatus.VERIFIED, startupResult.status());
        assertTrue(startupResult.isVerified());

        assertEquals(ArchiveIntegrityStatus.VERIFIED, archiveResult.status());
        assertTrue(archiveResult.isVerified());
    }

    private Path createSignedArchive(
            String fileName,
            Map<String, byte[]> signedFiles,
            Map<String, byte[]> additionalFiles,
            boolean corruptSignature
    ) throws Exception {

        IntegrityManifest manifest = createManifest(signedFiles);

        SignedManifest signedManifest = ManifestSignatureService.sign(
                manifest,
                keyPair.getPrivate()
        );

        byte[] signatureBytes = ManifestSignatureService
                .encodeSignature(signedManifest.signature())
                .getBytes(StandardCharsets.US_ASCII);

        if (corruptSignature) {
            signatureBytes = corruptSignature(signatureBytes);
        }

        Map<String, byte[]> archiveEntries = new LinkedHashMap<>();

        archiveEntries.putAll(signedFiles);
        archiveEntries.putAll(additionalFiles);

        archiveEntries.put(MANIFEST_PATH, signedManifest.manifestBytes());
        archiveEntries.put(SIGNATURE_PATH, signatureBytes);

        return writeJar(fileName, archiveEntries);
    }

    private Path createSignedArchiveWithoutSignature(
            String fileName,
            Map<String, byte[]> protectedFiles
    ) throws Exception {

        IntegrityManifest manifest = createManifest(protectedFiles);

        SignedManifest signedManifest = ManifestSignatureService.sign(
                manifest,
                keyPair.getPrivate()
        );

        Map<String, byte[]> archiveEntries = new LinkedHashMap<>();

        archiveEntries.putAll(protectedFiles);
        archiveEntries.put(MANIFEST_PATH, signedManifest.manifestBytes());

        return writeJar(fileName, archiveEntries);
    }

    private IntegrityManifest createManifest(
            Map<String, byte[]> protectedFiles
    ) {
        List<ManifestFileEntry> entries = protectedFiles.entrySet()
                .stream()
                .map(entry -> new ManifestFileEntry(
                        entry.getKey(),
                        entry.getValue().length,
                        Sha256HashService.hash(entry.getValue())
                ))
                .toList();

        return new IntegrityManifest(buildIdentity, entries);
    }

    private Map<String, byte[]> standardProtectedFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();

        files.put(
                CONFIG_PATH,
                bytes("debug: false\nlanguage: ru\n")
        );

        files.put(
                PLUGIN_DESCRIPTOR_PATH,
                bytes("name: RaskolClasses\nversion: 1.15.8\n")
        );

        return files;
    }

    private Path writeJar(
            String fileName,
            Map<String, byte[]> entries
    ) throws IOException {

        Path archive = temporaryDirectory.resolve(fileName);

        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(archive)
        )) {
            for (Map.Entry<String, byte[]> archiveEntry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(archiveEntry.getKey()));
                output.write(archiveEntry.getValue());
                output.closeEntry();
            }
        }

        return archive;
    }

    private byte[] corruptSignature(byte[] signatureBytes) {
        byte[] corrupted = signatureBytes.clone();

        corrupted[0] = corrupted[0] == 'A' ? (byte) 'B' : (byte) 'A';

        return corrupted;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
