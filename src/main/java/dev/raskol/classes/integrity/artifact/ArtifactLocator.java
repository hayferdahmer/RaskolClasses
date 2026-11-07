package dev.raskol.classes.integrity.artifact;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Objects;

/**
 * Locates the physical artifact from which a class was loaded.
 *
 * <p>This locator accepts only local file-based artifacts and rejects
 * directories, missing paths and unreadable files.</p>
 *
 * <p>Archive format validation is intentionally delegated to the
 * archive verification layer.</p>
 */
public final class ArtifactLocator {

    private ArtifactLocator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Locates the physical artifact containing the supplied anchor class.
     *
     * @param anchor class loaded from the artifact to locate
     * @return canonical path to the artifact
     * @throws IOException if the artifact cannot be located safely
     */
    public static Path locate(Class<?> anchor) throws IOException {
        Objects.requireNonNull(anchor, "anchor");

        try {
            ProtectionDomain protectionDomain = anchor.getProtectionDomain();

            if (protectionDomain == null) {
                throw new IOException("Class protection domain is unavailable");
            }

            CodeSource codeSource = protectionDomain.getCodeSource();

            if (codeSource == null) {
                throw new IOException("Class code source is unavailable");
            }

            URL location = codeSource.getLocation();

            if (location == null) {
                throw new IOException("Class code source location is unavailable");
            }

            URI locationUri = location.toURI();
            return locate(locationUri);

        } catch (URISyntaxException exception) {
            throw new IOException("Class code source contains an invalid URI", exception);
        } catch (SecurityException exception) {
            throw new IOException("Access to class code source was denied", exception);
        }
    }

    /**
     * Resolves and validates a local artifact URI.
     *
     * <p>Package-private to allow deterministic unit testing without relying
     * on the code source of the test runtime.</p>
     *
     * @param locationUri artifact location URI
     * @return canonical path to the artifact
     * @throws IOException if the URI does not identify a readable regular file
     */
    static Path locate(URI locationUri) throws IOException {
        Objects.requireNonNull(locationUri, "locationUri");

        String scheme = locationUri.getScheme();

        if (scheme == null || !"file".equalsIgnoreCase(scheme)) {
            throw new IOException("Only local file-based artifacts are supported");
        }

        final Path artifactPath;

        try {
            artifactPath = Path.of(locationUri);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Artifact location cannot be converted to a file path", exception);
        }

        final Path realPath;

        try {
            realPath = artifactPath.toRealPath();
        } catch (IOException exception) {
            throw new IOException("Artifact does not exist or cannot be resolved", exception);
        }

        if (!Files.isRegularFile(realPath)) {
            throw new IOException("Artifact location is not a regular file");
        }

        if (!Files.isReadable(realPath)) {
            throw new IOException("Artifact file is not readable");
        }

        return realPath;
    }
}
