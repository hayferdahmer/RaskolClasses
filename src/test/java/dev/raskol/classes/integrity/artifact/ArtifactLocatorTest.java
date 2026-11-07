package dev.raskol.classes.integrity.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactLocatorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLocateExistingRegularFile() throws IOException {
        Path artifact = Files.createFile(tempDir.resolve("RaskolClasses.jar"));

        Path located = ArtifactLocator.locate(artifact.toUri());

        assertEquals(artifact.toRealPath(), located);
    }

    @Test
    void shouldReturnCanonicalPath() throws IOException {
        Path artifact = Files.createFile(tempDir.resolve("plugin.jar"));

        Path located = ArtifactLocator.locate(artifact.toUri());

        assertEquals(artifact.toRealPath(), located);
        assertEquals(located, located.toRealPath());
    }

    @Test
    void shouldRejectDirectory() {
        Path directory = tempDir.resolve("classes");

        assertThrows(
                IOException.class,
                () -> ArtifactLocator.locate(directory.toUri())
        );
    }

    @Test
    void shouldRejectNonExistingFile() {
        Path missingArtifact = tempDir.resolve("missing.jar");

        assertThrows(
                IOException.class,
                () -> ArtifactLocator.locate(missingArtifact.toUri())
        );
    }

    @Test
    void shouldRejectNonFileUri() {
        URI remoteUri = URI.create("https://example.com/RaskolClasses.jar");

        assertThrows(
                IOException.class,
                () -> ArtifactLocator.locate(remoteUri)
        );
    }

    @Test
    void shouldRejectNullUri() {
        assertThrows(
                NullPointerException.class,
                () -> ArtifactLocator.locate((URI) null)
        );
    }

    @Test
    void shouldRejectNullAnchorClass() {
        assertThrows(
                NullPointerException.class,
                () -> ArtifactLocator.locate((Class<?>) null)
        );
    }
}
