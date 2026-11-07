// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.Sha256HashService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarArchiveIntegrityVerifierTest {

    private static final String MANIFEST_PATH =
            "META-INF/raskol/integrity/manifest.rips";

    private static final String SIGNATURE_PATH =
            "META-INF/raskol/integrity/manifest.sig";

    @TempDir
    Path tempDir;

    @Test
    void shouldVerifyCompleteArchive() throws IOException {
        byte[] classContent = "class-content".getBytes(StandardCharsets.UTF_8);
        byte[] configContent = "config-content".getBytes(StandardCharsets.UTF_8);

        Path jarPath = createJar(Map.of(
                "dev/raskol/classes/Main.class", classContent,
                "config.yml", configContent,
                MANIFEST_PATH, new byte[]{1, 2, 3},
                SIGNATURE_PATH, new byte[]{4, 5, 6}
        ));

        IntegrityManifest manifest = createManifest(Map.of(
                "dev/raskol/classes/Main.class", classContent,
                "config.yml", configContent
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(ArchiveIntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(4, result.archiveEntries());
        assertEquals(2, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldRejectUnexpectedArchiveEntry() throws IOException {
        byte[] expectedContent = bytes("expected");
        byte[] unexpectedContent = bytes("injected");

        Path jarPath = createJar(Map.of(
                "config.yml", expectedContent,
                "injected.class", unexpectedContent
        ));

        IntegrityManifest manifest = createManifest(Map.of(
                "config.yml", expectedContent
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_ENTRY,
                result.status()
        );
        assertEquals(List.of("injected.class"), result.failedFiles());
        assertFalse(result.isVerified());
    }

    @Test
    void shouldRejectMissingManifestEntry() throws IOException {
        byte[] content = bytes("expected");

        Path jarPath = createJar(Map.of(
                "config.yml", content
        ));

        IntegrityManifest manifest = createManifest(Map.of(
                "config.yml", content,
                "missing.yml", bytes("missing")
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.MANIFEST_ENTRY_MISSING,
                result.status()
        );
        assertEquals(List.of("missing.yml"), result.failedFiles());
    }

    @Test
    void shouldRejectEntrySizeMismatch() throws IOException {
        byte[] actualContent = bytes("actual-content");

        Path jarPath = createJar(Map.of(
                "config.yml", actualContent
        ));

        IntegrityManifest manifest = createManifestWithEntry(
                "config.yml",
                actualContent.length + 10,
                Sha256HashService.hash(actualContent)
        );

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                result.status()
        );
        assertEquals(List.of("config.yml"), result.failedFiles());
    }

    @Test
    void shouldRejectEntryHashMismatch() throws IOException {
        byte[] actualContent = bytes("actual-content");
        byte[] differentContent = bytes("different-content");

        Path jarPath = createJar(Map.of(
                "config.yml", actualContent
        ));

        IntegrityManifest manifest = createManifestWithEntry(
                "config.yml",
                actualContent.length,
                Sha256HashService.hash(differentContent)
        );

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_HASH_MISMATCH,
                result.status()
        );
        assertEquals(List.of("config.yml"), result.failedFiles());
    }

    @Test
    void shouldRejectInvalidArchiveEntryPath() throws IOException {
        Path jarPath = createJar("../outside.txt", bytes("malicious"));

        IntegrityManifest manifest = createManifest(Map.of(
                "safe.txt", bytes("safe")
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.INVALID_ARCHIVE_ENTRY_PATH,
                result.status()
        );
        assertEquals(List.of("../outside.txt"), result.failedFiles());
    }

    @Test
    void shouldRejectEmptyManifest() throws IOException {
        Path jarPath = createJar("config.yml", bytes("content"));

        IntegrityManifest manifest = new IntegrityManifest(
                createBuildIdentity(),
                List.of()
        );

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.MANIFEST_EMPTY,
                result.status()
        );
    }

    @Test
    void shouldRejectIntegrityMetadataInManifest() throws IOException {
        byte[] content = bytes("manifest-content");

        Path jarPath = createJar(
                MANIFEST_PATH,
                content
        );

        IntegrityManifest manifest = createManifest(Map.of(
                MANIFEST_PATH, content
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(jarPath);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.INVALID_MANIFEST,
                result.status()
        );
        assertEquals(List.of(MANIFEST_PATH), result.failedFiles());
    }

    @Test
    void shouldRejectEntryExceedingConfiguredLimit() throws IOException {
        byte[] content = bytes("content-too-large");

        Path jarPath = createJar("large.txt", content);

        IntegrityManifest manifest = createManifest(Map.of(
                "large.txt", content
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(
                        jarPath,
                        4,
                        1024,
                        100
                );

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_TOO_LARGE,
                result.status()
        );
    }

    @Test
    void shouldRejectArchiveExceedingTotalSizeLimit() throws IOException {
        byte[] first = bytes("123");
        byte[] second = bytes("456");

        Path jarPath = createJar(Map.of(
                "first.txt", first,
                "second.txt", second
        ));

        IntegrityManifest manifest = createManifest(Map.of(
                "first.txt", first,
                "second.txt", second
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(
                        jarPath,
                        100,
                        5,
                        100
                );

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                result.status()
        );
    }

    @Test
    void shouldRejectArchiveExceedingEntryCountLimit() throws IOException {
        Path jarPath = createJar(Map.of(
                "first.txt", bytes("first"),
                "second.txt", bytes("second")
        ));

        IntegrityManifest manifest = createManifest(Map.of(
                "first.txt", bytes("first"),
                "second.txt", bytes("second")
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(
                        jarPath,
                        1024,
                        4096,
                        1
                );

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ARCHIVE_ENTRY_COUNT_LIMIT_EXCEEDED,
                result.status()
        );
    }

    @Test
    void shouldRejectInvalidJarArchive() throws IOException {
        Path invalidJar = Files.createFile(tempDir.resolve("invalid.jar"));

        IntegrityManifest manifest = createManifest(Map.of(
                "config.yml", bytes("content")
        ));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(invalidJar);

        ArchiveIntegrityVerificationResult result = verifier.verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ARCHIVE_READ_ERROR,
                result.status()
        );
    }

    @Test
    void shouldExposeConfiguredLimitsAndArtifactPath() throws IOException {
        Path jarPath = createJar("config.yml", bytes("content"));

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(
                        jarPath,
                        2048,
                        8192,
                        50
                );

        assertEquals(jarPath.toRealPath(), verifier.getArtifactPath());
        assertEquals(2048, verifier.getMaxEntryBytes());
        assertEquals(8192, verifier.getMaxTotalBytes());
        assertEquals(50, verifier.getMaxArchiveEntries());
    }

    private IntegrityManifest createManifest(Map<String, byte[]> files) {
        List<ManifestFileEntry> entries = new ArrayList<>();

        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            entries.add(new ManifestFileEntry(
                    file.getKey(),
                    file.getValue().length,
                    Sha256HashService.hash(file.getValue())
            ));
        }

        return new IntegrityManifest(createBuildIdentity(), entries);
    }

    private IntegrityManifest createManifestWithEntry(
            String path,
            long size,
            String sha256
    ) {
        return new IntegrityManifest(
                createBuildIdentity(),
                List.of(new ManifestFileEntry(path, size, sha256))
        );
    }

    private BuildIdentity createBuildIdentity() {
        return new BuildIdentity(
                "RaskolClasses",
                "1.15.8",
                "BUILD-ARCHIVE-TEST",
                "0123456789abcdef",
                Instant.parse("2026-01-01T00:00:00Z"),
                "https://github.com/hayferdahmer/RaskolClasses",
                BuildType.OFFICIAL
        );
    }

    private Path createJar(String entryName, byte[] content) throws IOException {
        Path jarPath = tempDir.resolve("archive-" + System.nanoTime() + ".jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(content);
            output.closeEntry();
        }

        return jarPath;
    }

    private Path createJar(Map<String, byte[]> entries) throws IOException {
        Path jarPath = tempDir.resolve("archive-" + System.nanoTime() + ".jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }

        return jarPath;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
