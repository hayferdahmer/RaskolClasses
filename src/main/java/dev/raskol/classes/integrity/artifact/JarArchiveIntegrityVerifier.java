package dev.raskol.classes.integrity.artifact;

import dev.raskol.classes.integrity.manifest.IntegrityManifest;
import dev.raskol.classes.integrity.manifest.ManifestFileEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * Verifies the complete contents of a physical JAR archive against
 * a signed integrity manifest.
 *
 * <p>Every non-directory archive entry must be declared in the manifest,
 * except for the manifest and its detached signature.</p>
 *
 * <p>All declared files are verified using their actual decompressed
 * size and SHA-256 digest.</p>
 */
public final class JarArchiveIntegrityVerifier {

    private static final String MANIFEST_RESOURCE =
            "META-INF/raskol/integrity/manifest.rips";

    private static final String SIGNATURE_RESOURCE =
            "META-INF/raskol/integrity/manifest.sig";

    private static final long DEFAULT_MAX_ENTRY_BYTES =
            64L * 1024L * 1024L;

    private static final long DEFAULT_MAX_TOTAL_BYTES =
            512L * 1024L * 1024L;

    private static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 100_000;

    private static final int BUFFER_SIZE = 8192;

    private final Path artifactPath;
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final int maxArchiveEntries;

    /**
     * Creates a verifier with default archive limits.
     *
     * @param artifactPath physical JAR artifact
     * @throws IOException if the artifact path is invalid
     */
    public JarArchiveIntegrityVerifier(Path artifactPath) throws IOException {
        this(
                artifactPath,
                DEFAULT_MAX_ENTRY_BYTES,
                DEFAULT_MAX_TOTAL_BYTES,
                DEFAULT_MAX_ARCHIVE_ENTRIES
        );
    }

    /**
     * Creates a verifier with configurable archive limits.
     *
     * @param artifactPath physical JAR artifact
     * @param maxEntryBytes maximum decompressed size of one file
     * @param maxTotalBytes maximum total decompressed size of verified files
     * @param maxArchiveEntries maximum number of archive entries
     * @throws IOException if the artifact path is invalid
     */
    public JarArchiveIntegrityVerifier(
            Path artifactPath,
            long maxEntryBytes,
            long maxTotalBytes,
            int maxArchiveEntries
    ) throws IOException {
        Objects.requireNonNull(artifactPath, "artifactPath");

        if (maxEntryBytes <= 0) {
            throw new IllegalArgumentException("maxEntryBytes must be positive");
        }

        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }

        if (maxArchiveEntries <= 0) {
            throw new IllegalArgumentException("maxArchiveEntries must be positive");
        }

        Path realPath = artifactPath.toRealPath();

        if (!Files.isRegularFile(realPath)) {
            throw new IOException("Artifact path is not a regular file");
        }

        if (!Files.isReadable(realPath)) {
            throw new IOException("Artifact file is not readable");
        }

        this.artifactPath = realPath;
        this.maxEntryBytes = maxEntryBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxArchiveEntries = maxArchiveEntries;
    }

    /**
     * Verifies every relevant entry in the JAR against the manifest.
     *
     * @param manifest integrity manifest
     * @return archive verification result
     */
    public ArchiveIntegrityVerificationResult verify(IntegrityManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");

        List<ManifestFileEntry> manifestFiles = manifest.files();

        if (manifestFiles.isEmpty()) {
            return failure(
                    ArchiveIntegrityStatus.MANIFEST_EMPTY,
                    "Integrity manifest contains no protected files",
                    0,
                    0,
                    List.of()
            );
        }

        Map<String, ManifestFileEntry> expectedFiles = new HashMap<>();

        for (ManifestFileEntry file : manifestFiles) {
            if (isIntegrityMetadata(file.path())) {
                return failure(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Integrity metadata must not be included in the protected file list",
                        0,
                        0,
                        List.of(file.path())
                );
            }

            if (expectedFiles.putIfAbsent(file.path(), file) != null) {
                return failure(
                        ArchiveIntegrityStatus.INVALID_MANIFEST,
                        "Manifest contains duplicate file paths",
                        0,
                        0,
                        List.of(file.path())
                );
            }
        }

        try (JarFile jarFile = new JarFile(
                artifactPath.toFile(),
                false,
                ZipFile.OPEN_READ,
                JarFile.baseVersion()
        )) {
            return verifyArchiveEntries(jarFile, expectedFiles);

        } catch (IOException | SecurityException exception) {
            return failure(
                    ArchiveIntegrityStatus.ARCHIVE_READ_ERROR,
                    "Unable to read or inspect the JAR archive",
                    0,
                    0,
                    List.of()
            );
        }
    }

    private ArchiveIntegrityVerificationResult verifyArchiveEntries(
            JarFile jarFile,
            Map<String, ManifestFileEntry> expectedFiles
    ) {
        Map<String, JarEntry> actualFiles = new HashMap<>();
        Set<String> encounteredNames = new HashSet<>();

        int archiveEntries = 0;

        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            archiveEntries++;

            if (archiveEntries > maxArchiveEntries) {
                return failure(
                        ArchiveIntegrityStatus.ARCHIVE_ENTRY_COUNT_LIMIT_EXCEEDED,
                        "Archive contains too many entries",
                        archiveEntries,
                        0,
                        List.of(entry.getName())
                );
            }

            String entryName = entry.getName();

            if (!isValidArchiveEntryName(entryName, entry.isDirectory())) {
                return failure(
                        ArchiveIntegrityStatus.INVALID_ARCHIVE_ENTRY_PATH,
                        "Archive contains an invalid entry path",
                        archiveEntries,
                        0,
                        List.of(entryName)
                );
            }

            if (!encounteredNames.add(entryName)) {
                return failure(
                        ArchiveIntegrityStatus.DUPLICATE_ARCHIVE_ENTRY,
                        "Archive contains duplicate entry names",
                        archiveEntries,
                        0,
                        List.of(entryName)
                );
            }

            if (entry.isDirectory()) {
                continue;
            }

            if (isIntegrityMetadata(entryName)) {
                continue;
            }

            if (!expectedFiles.containsKey(entryName)) {
                return failure(
                        ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_ENTRY,
                        "Archive contains a file not declared in the integrity manifest",
                        archiveEntries,
                        0,
                        List.of(entryName)
                );
            }

            actualFiles.put(entryName, entry);
        }

        for (String expectedPath : expectedFiles.keySet()) {
            if (!actualFiles.containsKey(expectedPath)) {
                return failure(
                        ArchiveIntegrityStatus.MANIFEST_ENTRY_MISSING,
                        "A manifest file is missing from the archive",
                        archiveEntries,
                        0,
                        List.of(expectedPath)
                );
            }
        }

        return verifyFileContents(
                jarFile,
                expectedFiles,
                actualFiles,
                archiveEntries
        );
    }

    private ArchiveIntegrityVerificationResult verifyFileContents(
            JarFile jarFile,
            Map<String, ManifestFileEntry> expectedFiles,
            Map<String, JarEntry> actualFiles,
            int archiveEntries
    ) {
        long totalBytesRead = 0;
        int checkedFiles = 0;

        for (ManifestFileEntry expected : expectedFiles.values()) {
            String path = expected.path();
            JarEntry actual = actualFiles.get(path);

            if (expected.size() > maxEntryBytes
                    || actual.getSize() > maxEntryBytes) {
                return failure(
                        ArchiveIntegrityStatus.ENTRY_TOO_LARGE,
                        "Archive entry exceeds the configured size limit",
                        archiveEntries,
                        checkedFiles,
                        List.of(path)
                );
            }

            if (actual.getSize() >= 0 && actual.getSize() != expected.size()) {
                return failure(
                        ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                        "Archive entry size does not match the integrity manifest",
                        archiveEntries,
                        checkedFiles,
                        List.of(path)
                );
            }

            if (expected.size() > maxTotalBytes - totalBytesRead) {
                return failure(
                        ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                        "Total protected file size exceeds the configured limit",
                        archiveEntries,
                        checkedFiles,
                        List.of(path)
                );
            }

            try (InputStream input = jarFile.getInputStream(actual)) {
                MessageDigest digest = newSha256Digest();

                long fileBytesRead = 0;
                byte[] buffer = new byte[BUFFER_SIZE];

                int count;

                while ((count = input.read(buffer)) != -1) {
                    fileBytesRead += count;
                    totalBytesRead += count;

                    if (fileBytesRead > maxEntryBytes) {
                        return failure(
                                ArchiveIntegrityStatus.ENTRY_TOO_LARGE,
                                "Decompressed archive entry exceeds the configured size limit",
                                archiveEntries,
                                checkedFiles,
                                List.of(path)
                        );
                    }

                    if (totalBytesRead > maxTotalBytes) {
                        return failure(
                                ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                                "Total decompressed archive size exceeds the configured limit",
                                archiveEntries,
                                checkedFiles,
                                List.of(path)
                        );
                    }

                    digest.update(buffer, 0, count);
                }

                if (fileBytesRead != expected.size()) {
                    return failure(
                            ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                            "Actual file size does not match the integrity manifest",
                            archiveEntries,
                            checkedFiles,
                            List.of(path)
                    );
                }

                String actualHash = HexFormat.of().formatHex(digest.digest());

                if (!actualHash.equals(expected.sha256())) {
                    return failure(
                            ArchiveIntegrityStatus.ENTRY_HASH_MISMATCH,
                            "Actual file hash does not match the integrity manifest",
                            archiveEntries,
                            checkedFiles,
                            List.of(path)
                    );
                }

                checkedFiles++;

            } catch (IOException exception) {
                return failure(
                        ArchiveIntegrityStatus.ENTRY_READ_ERROR,
                        "Unable to read an archive entry",
                        archiveEntries,
                        checkedFiles,
                        List.of(path)
                );
            }
        }

        return new ArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.VERIFIED,
                "All archive entries match the integrity manifest",
                archiveEntries,
                checkedFiles,
                List.of()
        );
    }

    private static boolean isValidArchiveEntryName(
            String entryName,
            boolean directory
    ) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }

        if (entryName.startsWith("/")
                || entryName.startsWith("\\")
                || entryName.contains("\\")
                || entryName.indexOf('\0') >= 0) {
            return false;
        }

        for (int i = 0; i < entryName.length(); i++) {
            if (Character.isISOControl(entryName.charAt(i))) {
                return false;
            }
        }

        String normalized = entryName;

        if (directory) {
            if (!normalized.endsWith("/")) {
                return false;
            }

            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("/")) {
            return false;
        }

        if (normalized.isEmpty() || normalized.matches("^[A-Za-z]:.*")) {
            return false;
        }

        String[] segments = normalized.split("/", -1);

        for (String segment : segments) {
            if (segment.isEmpty()
                    || ".".equals(segment)
                    || "..".equals(segment)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isIntegrityMetadata(String path) {
        return MANIFEST_RESOURCE.equals(path)
                || SIGNATURE_RESOURCE.equals(path);
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private static ArchiveIntegrityVerificationResult failure(
            ArchiveIntegrityStatus status,
            String message,
            int archiveEntries,
            int checkedFiles,
            List<String> failedFiles
    ) {
        return new ArchiveIntegrityVerificationResult(
                status,
                message,
                archiveEntries,
                checkedFiles,
                failedFiles
        );
    }

    public Path getArtifactPath() {
        return artifactPath;
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
}
