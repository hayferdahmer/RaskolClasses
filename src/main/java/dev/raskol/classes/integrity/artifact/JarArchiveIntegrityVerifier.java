// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;
import dev.raskol.classes.integrity.manifest.Sha256HashService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Verifies all protected file entries in a JAR archive against an integrity
 * manifest.
 *
 * The manifest and its signature are excluded from the protected file list
 * to avoid circular integrity dependencies.
 */
public final class JarArchiveIntegrityVerifier {

    public static final long DEFAULT_MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 512L * 1024L * 1024L;
    public static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 100_000;

    private static final int BUFFER_SIZE = 8192;

    private static final String MANIFEST_PATH =
            "META-INF/raskol/integrity/manifest.rips";

    private static final String SIGNATURE_PATH =
            "META-INF/raskol/integrity/manifest.sig";

    private final Path archivePath;
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final int maxArchiveEntries;

    public JarArchiveIntegrityVerifier(Path archivePath) {
        this(
                archivePath,
                DEFAULT_MAX_ENTRY_BYTES,
                DEFAULT_MAX_TOTAL_BYTES,
                DEFAULT_MAX_ARCHIVE_ENTRIES
        );
    }

    public JarArchiveIntegrityVerifier(
            Path archivePath,
            long maxEntryBytes,
            long maxTotalBytes,
            int maxArchiveEntries
    ) {
        this.archivePath = Objects.requireNonNull(archivePath, "archivePath")
                .toAbsolutePath()
                .normalize();

        if (maxEntryBytes <= 0) {
            throw new IllegalArgumentException("maxEntryBytes must be positive");
        }

        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }

        if (maxArchiveEntries <= 0) {
            throw new IllegalArgumentException("maxArchiveEntries must be positive");
        }

        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxArchiveEntries = maxArchiveEntries;
    }

    /**
     * Verifies the complete archive against the supplied manifest.
     *
     * @param manifest expected integrity manifest
     * @return verification result
     */
    public ArchiveIntegrityVerificationResult verify(IntegrityManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");

        if (manifest.files().isEmpty()) {
            return result(
                    ArchiveIntegrityStatus.MANIFEST_EMPTY,
                    "Integrity manifest contains no protected files.",
                    0,
                    0,
                    Set.of()
            );
        }

        Map<String, ManifestFileEntry> expectedFiles = new HashMap<>();
        Set<String> expectedComparisonKeys = new HashSet<>();

        for (ManifestFileEntry file : manifest.files()) {
            if (file == null) {
                return result(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Integrity manifest contains a null file entry.",
                        0,
                        0,
                        Set.of()
                );
            }

            final String validatedPath;

            try {
                validatedPath = ArchiveEntryPathValidator.validate(
                        file.path(),
                        false
                );
            } catch (IllegalArgumentException exception) {
                return result(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Integrity manifest contains an invalid file path.",
                        0,
                        0,
                        Set.of(file.path())
                );
            }

            if (MANIFEST_PATH.equals(validatedPath)
                    || SIGNATURE_PATH.equals(validatedPath)) {
                return result(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Integrity metadata must not be included in its own manifest.",
                        0,
                        0,
                        Set.of(validatedPath)
                );
            }

            if (expectedFiles.putIfAbsent(validatedPath, file) != null) {
                return result(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Integrity manifest contains duplicate file paths.",
                        0,
                        0,
                        Set.of(validatedPath)
                );
            }

            String comparisonKey =
                    ArchiveEntryPathValidator.comparisonKey(validatedPath);

            if (!expectedComparisonKeys.add(comparisonKey)) {
                return result(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Integrity manifest contains case-insensitive path collisions.",
                        0,
                        0,
                        Set.of(validatedPath)
                );
            }
        }

        try (JarFile jarFile = new JarFile(archivePath.toFile(), false)) {
            return verifyArchiveEntries(jarFile, expectedFiles);
        } catch (IOException | SecurityException exception) {
            return result(
                    ArchiveIntegrityStatus.ARCHIVE_READ_ERROR,
                    "Unable to read the JAR archive.",
                    0,
                    0,
                    Set.of()
            );
        }
    }

    private ArchiveIntegrityVerificationResult verifyArchiveEntries(
            JarFile jarFile,
            Map<String, ManifestFileEntry> expectedFiles
    ) {
        Set<String> seenEntries = new HashSet<>();
        Set<String> comparisonKeys = new HashSet<>();
        Set<String> verifiedFiles = new HashSet<>();
        Set<String> failedFiles = new HashSet<>();

        int archiveEntries = 0;
        int checkedFiles = 0;
        long totalBytesRead = 0;

        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            archiveEntries++;

            if (archiveEntries > maxArchiveEntries) {
                return result(
                        ArchiveIntegrityStatus.ARCHIVE_ENTRY_COUNT_LIMIT_EXCEEDED,
                        "JAR archive contains too many entries.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            String entryName = entry.getName();

            final String validatedPath;

            try {
                validatedPath = ArchiveEntryPathValidator.validate(
                        entryName,
                        entry.isDirectory()
                );
            } catch (IllegalArgumentException exception) {
                failedFiles.add(entryName);

                return result(
                        ArchiveIntegrityStatus.INVALID_ARCHIVE_ENTRY_PATH,
                        "JAR archive contains an invalid entry path.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            if (!seenEntries.add(entryName)) {
                failedFiles.add(entryName);

                return result(
                        ArchiveIntegrityStatus.DUPLICATE_ARCHIVE_ENTRY,
                        "JAR archive contains duplicate entry names.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            String comparisonKey =
                    ArchiveEntryPathValidator.comparisonKey(validatedPath);

            if (!comparisonKeys.add(comparisonKey)) {
                failedFiles.add(entryName);

                return result(
                        ArchiveIntegrityStatus.ARCHIVE_PATH_COLLISION,
                        "JAR archive contains case-insensitive path collisions.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            if (entry.isDirectory()) {
                if (!isExpectedDirectory(validatedPath, expectedFiles.keySet())) {
                    failedFiles.add(entryName);

                    return result(
                            ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_DIRECTORY,
                            "JAR archive contains an unexpected directory.",
                            archiveEntries,
                            checkedFiles,
                            failedFiles
                    );
                }

                continue;
            }

            if (MANIFEST_PATH.equals(validatedPath)
                    || SIGNATURE_PATH.equals(validatedPath)) {
                continue;
            }

            ManifestFileEntry expected = expectedFiles.get(validatedPath);

            if (expected == null) {
                failedFiles.add(validatedPath);

                return result(
                        ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_ENTRY,
                        "JAR archive contains an entry not declared in the integrity manifest.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            if (entry.getSize() > maxEntryBytes) {
                failedFiles.add(validatedPath);

                return result(
                        ArchiveIntegrityStatus.ENTRY_TOO_LARGE,
                        "JAR entry exceeds the configured size limit.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            if (entry.getSize() >= 0
                    && entry.getSize() > maxTotalBytes - totalBytesRead) {
                failedFiles.add(validatedPath);

                return result(
                        ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                        "JAR archive exceeds the configured total size limit.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            EntryVerification entryResult = verifyEntry(
                    jarFile,
                    entry,
                    expected,
                    totalBytesRead
            );

            totalBytesRead += entryResult.bytesRead();
            checkedFiles++;

            if (entryResult.status() != ArchiveIntegrityStatus.VERIFIED) {
                failedFiles.add(validatedPath);

                return result(
                        entryResult.status(),
                        entryResult.message(),
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }

            verifiedFiles.add(validatedPath);
        }

        for (String expectedPath : expectedFiles.keySet()) {
            if (!verifiedFiles.contains(expectedPath)) {
                failedFiles.add(expectedPath);

                return result(
                        ArchiveIntegrityStatus.MANIFEST_ENTRY_MISSING,
                        "A protected file declared in the manifest is missing from the JAR archive.",
                        archiveEntries,
                        checkedFiles,
                        failedFiles
                );
            }
        }

        return result(
                ArchiveIntegrityStatus.VERIFIED,
                "JAR archive integrity verification completed successfully.",
                archiveEntries,
                checkedFiles,
                Set.of()
        );
    }

    private EntryVerification verifyEntry(
            JarFile jarFile,
            JarEntry entry,
            ManifestFileEntry expected,
            long totalBytesRead
    ) {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            return new EntryVerification(
                    ArchiveIntegrityStatus.ENTRY_READ_ERROR,
                    "SHA-256 algorithm is unavailable.",
                    0
            );
        }

        long entryBytesRead = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                entryBytesRead += read;

                if (entryBytesRead > maxEntryBytes) {
                    return new EntryVerification(
                            ArchiveIntegrityStatus.ENTRY_TOO_LARGE,
                            "JAR entry exceeds the configured decompressed size limit.",
                            entryBytesRead
                    );
                }

                if (read > maxTotalBytes - totalBytesRead - (entryBytesRead - read)) {
                    return new EntryVerification(
                            ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                            "JAR archive exceeds the configured total decompressed size limit.",
                            entryBytesRead
                    );
                }

                digest.update(buffer, 0, read);
            }
        } catch (IOException | SecurityException exception) {
            return new EntryVerification(
                    ArchiveIntegrityStatus.ENTRY_READ_ERROR,
                    "Unable to read a JAR archive entry.",
                    entryBytesRead
            );
        }

        if (entryBytesRead != expected.size()) {
            return new EntryVerification(
                    ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                    "JAR entry size does not match the integrity manifest.",
                    entryBytesRead
            );
        }

        byte[] actualHash = digest.digest();
        byte[] expectedHash;

        try {
            expectedHash = Sha256HashService.decode(expected.sha256());
        } catch (IllegalArgumentException exception) {
            return new EntryVerification(
                    ArchiveIntegrityStatus.INVALID_MANIFEST,
                    "Integrity manifest contains an invalid SHA-256 hash.",
                    entryBytesRead
            );
        }

        if (!MessageDigest.isEqual(actualHash, expectedHash)) {
            return new EntryVerification(
                    ArchiveIntegrityStatus.ENTRY_HASH_MISMATCH,
                    "JAR entry hash does not match the integrity manifest.",
                    entryBytesRead
            );
        }

        return new EntryVerification(
                ArchiveIntegrityStatus.VERIFIED,
                "Entry verified.",
                entryBytesRead
        );
    }

    private boolean isExpectedDirectory(
            String directoryPath,
            Set<String> expectedFiles
    ) {
        String prefix = directoryPath + "/";

        for (String expectedPath : expectedFiles) {
            if (expectedPath.startsWith(prefix)) {
                return true;
            }
        }

        return MANIFEST_PATH.startsWith(prefix)
                || SIGNATURE_PATH.startsWith(prefix);
    }

    private ArchiveIntegrityVerificationResult result(
            ArchiveIntegrityStatus status,
            String message,
            int archiveEntries,
            int checkedFiles,
            Set<String> failedFiles
    ) {
        return new ArchiveIntegrityVerificationResult(
                status,
                message,
                archiveEntries,
                checkedFiles,
                failedFiles.stream().sorted().toList()
        );
    }

    public Path getArchivePath() {
        return archivePath;
    }

    public long getMaxEntryBytes() {
        return maxEntryBytes;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    public int getMaxArchiveEntries() {
        return maxArchiveEntries;
    }

    private record EntryVerification(
            ArchiveIntegrityStatus status,
            String message,
            long bytesRead
    ) {
    }
}
