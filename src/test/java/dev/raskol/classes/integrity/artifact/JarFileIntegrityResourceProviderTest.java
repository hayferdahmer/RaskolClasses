
package dev.raskol.classes.integrity.artifact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarFileIntegrityResourceProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadResourceFromJar() throws IOException {
        byte[] expected = "RASKOL-RIPS-RESOURCE".getBytes(StandardCharsets.UTF_8);
        Path jarPath = createJar("META-INF/raskol/test.txt", expected);

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        try (InputStream input = provider.openResource("/META-INF/raskol/test.txt")) {
            assertNotNull(input);
            assertArrayEquals(expected, input.readAllBytes());
        }
    }

    @Test
    void shouldReadResourceWithoutLeadingSlash() throws IOException {
        byte[] expected = "resource-content".getBytes(StandardCharsets.UTF_8);
        Path jarPath = createJar("config.yml", expected);

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        try (InputStream input = provider.openResource("config.yml")) {
            assertNotNull(input);
            assertArrayEquals(expected, input.readAllBytes());
        }
    }

    @Test
    void shouldReturnNullWhenResourceDoesNotExist() throws IOException {
        Path jarPath = createJar("existing.txt", new byte[]{1, 2, 3});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        assertNull(provider.openResource("missing.txt"));
    }

    @Test
    void shouldRejectPathTraversal() throws IOException {
        Path jarPath = createJar("safe.txt", new byte[]{1});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        assertThrows(
                IOException.class,
                () -> provider.openResource("../outside.txt")
        );

        assertThrows(
                IOException.class,
                () -> provider.openResource("META-INF/../../outside.txt")
        );
    }

    @Test
    void shouldRejectBackslashes() throws IOException {
        Path jarPath = createJar("safe.txt", new byte[]{1});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        assertThrows(
                IOException.class,
                () -> provider.openResource("META-INF\\raskol\\test.txt")
        );
    }

    @Test
    void shouldRejectDoubleLeadingSlash() throws IOException {
        Path jarPath = createJar("safe.txt", new byte[]{1});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        assertThrows(
                IOException.class,
                () -> provider.openResource("//safe.txt")
        );
    }

    @Test
    void shouldRejectBlankResourcePath() throws IOException {
        Path jarPath = createJar("safe.txt", new byte[]{1});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        assertThrows(IOException.class, () -> provider.openResource(""));
        assertThrows(IOException.class, () -> provider.openResource("   "));
    }

    @Test
    void shouldRejectDirectoryEntry() throws IOException {
        Path jarPath = tempDir.resolve("directory-entry.jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry("META-INF/"));
            output.closeEntry();
        }

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        assertThrows(
                IOException.class,
                () -> provider.openResource("META-INF")
        );
    }

    @Test
    void shouldRejectResourceLargerThanConfiguredLimit() throws IOException {
        byte[] content = "TOO-LARGE".getBytes(StandardCharsets.UTF_8);
        Path jarPath = createJar("large.txt", content);

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath, 4);

        assertThrows(
                IOException.class,
                () -> provider.openResource("large.txt")
        );
    }

    @Test
    void shouldEnforceLimitWhileReadingResource() throws IOException {
        byte[] content = "123456789".getBytes(StandardCharsets.UTF_8);
        Path jarPath = createJar("resource.txt", content);

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath, 5);

        try (InputStream input = provider.openResource("resource.txt")) {
            assertNotNull(input);

            assertThrows(
                    IOException.class,
                    input::readAllBytes
            );
        }
    }

    @Test
    void shouldAllowResourceExactlyAtConfiguredLimit() throws IOException {
        byte[] content = "12345".getBytes(StandardCharsets.UTF_8);
        Path jarPath = createJar("resource.txt", content);

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath, 5);

        try (InputStream input = provider.openResource("resource.txt")) {
            assertNotNull(input);
            assertArrayEquals(content, input.readAllBytes());
        }
    }

    @Test
    void shouldCloseResourceStreamAndRejectFurtherReads() throws IOException {
        Path jarPath = createJar(
                "resource.txt",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        InputStream input = provider.openResource("resource.txt");

        assertNotNull(input);

        input.close();

        assertThrows(IOException.class, input::read);
    }

    @Test
    void shouldAllowRepeatedClose() throws IOException {
        Path jarPath = createJar("resource.txt", new byte[]{1, 2, 3});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath);

        InputStream input = provider.openResource("resource.txt");

        assertNotNull(input);

        input.close();
        input.close();
    }

    @Test
    void shouldRejectDirectoryAsArtifact() {
        assertThrows(
                IOException.class,
                () -> new JarFileIntegrityResourceProvider(tempDir)
        );
    }

    @Test
    void shouldRejectNonPositiveResourceLimit() {
        Path jarPath = tempDir.resolve("unused.jar");

        assertThrows(
                IllegalArgumentException.class,
                () -> new JarFileIntegrityResourceProvider(jarPath, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new JarFileIntegrityResourceProvider(jarPath, -1)
        );
    }

    @Test
    void shouldExposeCanonicalArtifactPathAndConfiguredLimit() throws IOException {
        Path jarPath = createJar("resource.txt", new byte[]{1, 2, 3});

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(jarPath, 1024);

        assertEquals(jarPath.toRealPath(), provider.getArtifactPath());
        assertEquals(1024, provider.getMaxResourceBytes());
    }

    @Test
    void shouldRejectInvalidJarArchiveWhenOpeningResource() throws IOException {
        Path invalidJar = Files.createFile(tempDir.resolve("invalid.jar"));

        JarFileIntegrityResourceProvider provider =
                new JarFileIntegrityResourceProvider(invalidJar);

        assertThrows(
                IOException.class,
                () -> provider.openResource("resource.txt")
        );
    }

    private Path createJar(String entryName, byte[] content) throws IOException {
        Path jarPath = tempDir.resolve("test-" + System.nanoTime() + ".jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(content);
            output.closeEntry();
        }

        return jarPath;
    }
}
