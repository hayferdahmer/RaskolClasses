// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import dev.raskol.classes.integrity.verification.IntegrityResourceProvider;

/**
 * Reads integrity resources directly from a physical JAR archive.
 *
 * <p>Each opened resource owns its JarFile instance. Closing the returned
 * stream also closes the underlying archive.</p>
 *
 * <p>Resource paths are validated before archive lookup. Resource reads
 * are limited to prevent unexpectedly large decompressed entries.</p>
 */
public final class JarFileIntegrityResourceProvider implements IntegrityResourceProvider {

    private static final long DEFAULT_MAX_RESOURCE_BYTES = 64L * 1024L * 1024L;

    private final Path artifactPath;
    private final long maxResourceBytes;

    /**
     * Creates a provider with the default 64 MiB resource limit.
     *
     * @param artifactPath path to the physical JAR artifact
     * @throws IOException if the artifact path is invalid
     */
    public JarFileIntegrityResourceProvider(Path artifactPath) throws IOException {
        this(artifactPath, DEFAULT_MAX_RESOURCE_BYTES);
    }

    /**
     * Creates a provider with a configurable resource size limit.
     *
     * @param artifactPath path to the physical JAR artifact
     * @param maxResourceBytes maximum number of decompressed bytes per resource
     * @throws IOException if the artifact path is invalid
     */
    public JarFileIntegrityResourceProvider(Path artifactPath, long maxResourceBytes) throws IOException {
        Objects.requireNonNull(artifactPath, "artifactPath");

        if (maxResourceBytes <= 0) {
            throw new IllegalArgumentException("maxResourceBytes must be positive");
        }

        Path realPath = artifactPath.toRealPath();

        if (!Files.isRegularFile(realPath)) {
            throw new IOException("Artifact path is not a regular file");
        }

        if (!Files.isReadable(realPath)) {
            throw new IOException("Artifact file is not readable");
        }

        this.artifactPath = realPath;
        this.maxResourceBytes = maxResourceBytes;
    }

    /**
     * Opens a resource directly from the JAR archive.
     *
     * @param resourcePath absolute-style or relative resource path
     * @return resource stream, or null if the entry does not exist
     * @throws IOException if the archive or resource cannot be read
     */
    @Override
    public InputStream openResource(String resourcePath) throws IOException {
        String entryName = normalizeResourcePath(resourcePath);

        JarFile jarFile = new JarFile(artifactPath.toFile(), false, ZipFile.OPEN_READ, JarFile.baseVersion());

        boolean ownershipTransferred = false;

        try {
            JarEntry entry = jarFile.getJarEntry(entryName);

            if (entry == null) {
                return null;
            }

            if (entry.isDirectory()) {
                throw new IOException("Requested JAR resource is a directory");
            }

            long declaredSize = entry.getSize();

            if (declaredSize > maxResourceBytes) {
                throw new IOException("JAR resource exceeds the configured size limit");
            }

            InputStream inputStream = jarFile.getInputStream(entry);

            LimitedResourceInputStream limitedStream =
                    new LimitedResourceInputStream(inputStream, jarFile, maxResourceBytes);

            ownershipTransferred = true;
            return limitedStream;

        } finally {
            if (!ownershipTransferred) {
                jarFile.close();
            }
        }
    }

    /**
     * Returns the canonical path of the archive used by this provider.
     *
     * @return canonical artifact path
     */
    public Path getArtifactPath() {
        return artifactPath;
    }

    /**
     * Returns the maximum allowed decompressed resource size.
     *
     * @return maximum resource size in bytes
     */
    public long getMaxResourceBytes() {
        return maxResourceBytes;
    }

    private static String normalizeResourcePath(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IOException("Resource path must not be blank");
        }

        if (resourcePath.indexOf('\\') >= 0 || resourcePath.indexOf('\0') >= 0) {
            throw new IOException("Resource path contains forbidden characters");
        }

        String normalized = resourcePath;

        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.endsWith("/")) {
            throw new IOException("Resource path is not a valid archive entry path");
        }

        String[] segments = normalized.split("/", -1);

        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Resource path contains an invalid path segment");
            }
        }

        return normalized;
    }

    /**
     * Input stream that enforces a maximum decompressed byte count.
     *
     * <p>Closing this stream closes both the resource stream and its
     * owning JAR archive.</p>
     */
    private static final class LimitedResourceInputStream extends FilterInputStream {

        private final JarFile jarFile;
        private final long maxBytes;

        private long bytesRead;
        private boolean closed;

        private LimitedResourceInputStream(InputStream inputStream, JarFile jarFile, long maxBytes) {
            super(inputStream);
            this.jarFile = jarFile;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            ensureOpen();

            if (bytesRead >= maxBytes) {
                int extraByte = super.read();

                if (extraByte == -1) {
                    return -1;
                }

                throw new IOException("JAR resource exceeded the configured size limit");
            }

            int value = super.read();

            if (value != -1) {
                bytesRead++;
            }

            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            ensureOpen();

            if (length == 0) {
                return 0;
            }

            if (bytesRead >= maxBytes) {
                int extraByte = super.read();

                if (extraByte == -1) {
                    return -1;
                }

                throw new IOException("JAR resource exceeded the configured size limit");
            }

            long remaining = maxBytes - bytesRead;
            int allowedLength = (int) Math.min(length, remaining);

            int count = super.read(buffer, offset, allowedLength);

            if (count > 0) {
                bytesRead += count;
            }

            return count;
        }

        @Override
        public long skip(long amount) throws IOException {
            ensureOpen();

            if (amount <= 0) {
                return 0;
            }

            byte[] buffer = new byte[(int) Math.min(8192L, amount)];
            long skipped = 0;

            while (skipped < amount) {
                int requested = (int) Math.min(buffer.length, amount - skipped);
                int count = read(buffer, 0, requested);

                if (count == -1) {
                    break;
                }

                skipped += count;
            }

            return skipped;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }

            closed = true;

            IOException failure = null;

            try {
                super.close();
            } catch (IOException exception) {
                failure = exception;
            }

            try {
                jarFile.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }

            if (failure != null) {
                throw failure;
            }
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Resource stream is closed");
            }
        }
    }
}
