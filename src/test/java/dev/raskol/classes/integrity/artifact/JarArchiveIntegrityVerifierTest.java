// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;
import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.Sha256HashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarArchiveIntegrityVerifierTest {

    private static final String MANIFEST_PATH =
            "META-INF/raskol/integrity/manifest.rips";

    private static final String SIGNATURE_PATH =
            "META-INF/raskol/integrity/manifest.sig";

    private static final String PUBLIC_KEY_PATH =
            "META-INF/raskol/integrity/public-key.txt";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldVerifyCompleteArchive() throws Exception {
        byte[] classContent = bytes("protected-class-content");
        byte[] configContent = bytes("protected-config-content");

        Path archive = createArchive(
                "plugin.jar",
                new ArchiveEntry("dev/raskol/Test.class", classContent),
                new ArchiveEntry("config.yml", configContent),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("dev/raskol/Test.class", classContent),
                fileEntry("config.yml", configContent)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(4, result.archiveEntries());
        assertEquals(2, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldAllowPublicKeyAsIntegrityMetadata() throws Exception {
        byte[] classContent = bytes("protected-class-content");
        byte[] publicKey = bytes("trusted-public-key");

        Path archive = createArchive(
                "with-public-key.jar",
                new ArchiveEntry("dev/raskol/Test.class", classContent),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2}),
                new ArchiveEntry(PUBLIC_KEY_PATH, publicKey)
        );

        IntegrityManifest manifest = manifest(
                fileEntry("dev/raskol/Test.class", classContent)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
        assertEquals(4, result.archiveEntries());
        assertEquals(1, result.checkedFiles());
        assertTrue(result.failedFiles().isEmpty());
    }

    @Test
    void shouldRejectPublicKeyInsideManifest() throws Exception {
        byte[] publicKey = bytes("trusted-public-key");

        Path archive = createArchive(
                "public-key-in-manifest.jar",
                new ArchiveEntry(PUBLIC_KEY_PATH, publicKey),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry(PUBLIC_KEY_PATH, publicKey)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.INVALID_MANIFEST,
                result.status()
        );
        assertFalse(result.isVerified());
    }

    @Test
    void shouldRejectUnexpectedArchiveEntry() throws Exception {
        byte[] expectedContent = bytes("expected");
        byte[] unexpectedContent = bytes("unexpected");

        Path archive = createArchive(
                "unexpected-entry.jar",
                new ArchiveEntry("config.yml", expectedContent),
                new ArchiveEntry("unexpected.txt", unexpectedContent),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", expectedContent)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_ENTRY,
                result.status()
        );
        assertFalse(result.isVerified());
        assertTrue(result.failedFiles().contains("unexpected.txt"));
    }

    @Test
    void shouldRejectMissingManifestEntry() throws Exception {
        byte[] expectedContent = bytes("expected");

        Path archive = createArchive(
                "missing-entry.jar",
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", expectedContent)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.MANIFEST_ENTRY_MISSING,
                result.status()
        );
        assertTrue(result.failedFiles().contains("config.yml"));
    }

    @Test
    void shouldRejectEntrySizeMismatch() throws Exception {
        byte[] actualContent = bytes("short");
        byte[] expectedContent = bytes("much longer expected content");

        Path archive = createArchive(
                "size-mismatch.jar",
                new ArchiveEntry("config.yml", actualContent),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", expectedContent)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                result.status()
        );
        assertTrue(result.failedFiles().contains("config.yml"));
    }

    @Test
    void shouldRejectEntryHashMismatch() throws Exception {
        byte[] actualContent = bytes("actual-content");
        byte[] expectedContent = bytes("sample-content");

        Path archive = createArchive(
                "hash-mismatch.jar",
                new ArchiveEntry("config.yml", actualContent),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", expectedContent)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ENTRY_HASH_MISMATCH,
                result.status()
        );
        assertTrue(result.failedFiles().contains("config.yml"));
    }

    @Test
    void shouldRejectInvalidArchiveEntryPath() throws Exception {
        Path archive = createArchive(
                "invalid-path.jar",
                new ArchiveEntry("../outside.txt", bytes("unsafe")),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", bytes("expected"))
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.INVALID_ARCHIVE_ENTRY_PATH,
                result.status()
        );
    }

    @Test
    void shouldRejectEmptyManifest() throws Exception {
        Path archive = createArchive(
                "empty-manifest.jar",
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest();

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.MANIFEST_EMPTY, result.status());
    }

    @Test
    void shouldRejectIntegrityMetadataInsideManifest() throws Exception {
        byte[] metadata = bytes("metadata");

        Path archive = createArchive(
                "metadata-in-manifest.jar",
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry(MANIFEST_PATH, metadata)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.INVALID_MANIFEST, result.status());
    }

    @Test
    void shouldRejectEntryExceedingConfiguredSizeLimit() throws Exception {
        byte[] content = bytes("0123456789");

        Path archive = createArchive(
                "entry-too-large.jar",
                new ArchiveEntry("config.yml", content),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", content)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(
                        archive,
                        5,
                        100,
                        100
                ).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.ENTRY_TOO_LARGE, result.status());
    }

    @Test
    void shouldRejectArchiveExceedingTotalSizeLimit() throws Exception {
        byte[] first = bytes("123456");
        byte[] second = bytes("abcdef");

        Path archive = createArchive(
                "total-too-large.jar",
                new ArchiveEntry("first.txt", first),
                new ArchiveEntry("second.txt", second),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("first.txt", first),
                fileEntry("second.txt", second)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(
                        archive,
                        100,
                        8,
                        100
                ).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                result.status()
        );
    }

    @Test
    void shouldRejectArchiveExceedingEntryCountLimit() throws Exception {
        Path archive = createArchive(
                "too-many-entries.jar",
                new ArchiveEntry("first.txt", bytes("first")),
                new ArchiveEntry("second.txt", bytes("second")),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("first.txt", bytes("first")),
                fileEntry("second.txt", bytes("second"))
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(
                        archive,
                        100,
                        100,
                        2
                ).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ARCHIVE_ENTRY_COUNT_LIMIT_EXCEEDED,
                result.status()
        );
    }

    @Test
    void shouldRejectInvalidJarArchive() throws Exception {
        Path archive = temporaryDirectory.resolve("invalid.jar");
        Files.writeString(archive, "this is not a JAR archive");

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", bytes("expected"))
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.ARCHIVE_READ_ERROR, result.status());
    }

    @Test
    void shouldRejectCaseInsensitivePathCollision() throws Exception {
        byte[] first = bytes("first");
        byte[] second = bytes("second");

        Path archive = createArchive(
                "case-collision.jar",
                new ArchiveEntry("Config.yml", first),
                new ArchiveEntry("config.yml", second),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("Config.yml", first)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.ARCHIVE_PATH_COLLISION,
                result.status()
        );
    }

    @Test
    void shouldRejectUnexpectedArchiveDirectory() throws Exception {
        byte[] content = bytes("config");

        Path archive = createArchive(
                "unexpected-directory.jar",
                new ArchiveEntry("unexpected/", new byte[0]),
                new ArchiveEntry("config.yml", content),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2})
        );

        IntegrityManifest manifest = manifest(
                fileEntry("config.yml", content)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(
                ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_DIRECTORY,
                result.status()
        );
        assertTrue(result.failedFiles().contains("unexpected/"));
    }

    @Test
    void shouldAllowIntegrityMetadataDirectory() throws Exception {
        byte[] content = bytes("protected-content");

        Path archive = createArchive(
                "integrity-directory.jar",
                new ArchiveEntry("dev/raskol/Test.class", content),
                new ArchiveEntry(
                        "META-INF/",
                        new byte[0]
                ),
                new ArchiveEntry(
                        "META-INF/raskol/",
                        new byte[0]
                ),
                new ArchiveEntry(
                        "META-INF/raskol/integrity/",
                        new byte[0]
                ),
                new ArchiveEntry(MANIFEST_PATH, new byte[]{1}),
                new ArchiveEntry(SIGNATURE_PATH, new byte[]{2}),
                new ArchiveEntry(PUBLIC_KEY_PATH, bytes("public-key"))
        );

        IntegrityManifest manifest = manifest(
                fileEntry("dev/raskol/Test.class", content)
        );

        JarArchiveIntegrityVerificationResult result =
                new JarArchiveIntegrityVerifier(archive).verify(manifest);

        assertEquals(ArchiveIntegrityStatus.VERIFIED, result.status());
        assertTrue(result.isVerified());
    }

    @Test
    void shouldExposeConfiguredLimitsAndArchivePath() throws Exception {
        Path archive = temporaryDirectory.resolve("limits.jar");

        JarArchiveIntegrityVerifier verifier =
                new JarArchiveIntegrityVerifier(
                        archive,
                        123,
                        456,
                        789
                );

        assertEquals(archive.toAbsolutePath().normalize(), verifier.getArchivePath());
        assertEquals(123, verifier.getMaxEntryBytes());
        assertEquals(456, verifier.getMaxTotalBytes());
        assertEquals(789, verifier.getMaxArchiveEntries());
    }

    @Test
    void shouldRejectNonPositiveLimits() {
        Path archive = temporaryDirectory.resolve("limits.jar");

        assertThrowsIllegalArgument(
                () -> new JarArchiveIntegrityVerifier(archive, 0, 100, 100)
        );

        assertThrowsIllegalArgument(
                () -> new JarArchiveIntegrityVerifier(archive, 100, 0, 100)
        );

        assertThrowsIllegalArgument(
                () -> new JarArchiveIntegrityVerifier(archive, 100, 100, 0)
        );
    }

    private Path createArchive(
            String fileName,
            ArchiveEntry... entries
    ) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);

        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(archive)
        )) {
            for (ArchiveEntry archiveEntry : entries) {
                JarEntry entry = new JarEntry(archiveEntry.name());

                output.putNextEntry(entry);

                if (!entry.isDirectory()) {
                    output.write(archiveEntry.content());
                }

                output.closeEntry();
            }
        }

        return archive;
    }

    private IntegrityManifest manifest(ManifestFileEntry... entries) {
        BuildIdentity identity = new BuildIdentity(
                "test-build",
                "1.0.0",
                "0123456789abcdef",
                "test-repository",
                Instant.parse("2026-01-01T00:00:00Z"),
                "test",
                BuildType.OFFICIAL
        );

        return new IntegrityManifest(identity, List.of(entries));
    }

    private ManifestFileEntry fileEntry(String path, byte[] content) {
        return new ManifestFileEntry(
                path,
                content.length,
                Sha256HashService.hash(content)
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private void assertThrowsIllegalArgument(Runnable action) {
        boolean thrown = false;

        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            thrown = true;
        }

        assertTrue(thrown, "Expected IllegalArgumentException");
    }

    private record ArchiveEntry(String name, byte[] content) {
        private ArchiveEntry {
            assertNotNull(name);
            assertNotNull(content);
        }
    }
}
