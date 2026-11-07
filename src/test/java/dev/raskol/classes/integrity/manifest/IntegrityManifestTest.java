// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import dev.raskol.classes.integrity.build.BuildIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrityManifestTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static final BuildIdentity IDENTITY =
            BuildIdentity.development("1.15.8-SNAPSHOT");

    @Test
    void shouldSortFilesByPath() {
        IntegrityManifest manifest = new IntegrityManifest(
                IDENTITY,
                List.of(
                        new ManifestFileEntry("z/Test.class", 20, HASH_B),
                        new ManifestFileEntry("a/Test.class", 10, HASH_A)
                )
        );

        assertEquals(
                "a/Test.class",
                manifest.files().get(0).path()
        );

        assertEquals(
                "z/Test.class",
                manifest.files().get(1).path()
        );
    }

    @Test
    void shouldProduceSameBytesRegardlessOfInputOrder() {
        ManifestFileEntry first =
                new ManifestFileEntry("a/Test.class", 10, HASH_A);

        ManifestFileEntry second =
                new ManifestFileEntry("b/Test.class", 20, HASH_B);

        IntegrityManifest firstManifest = new IntegrityManifest(
                IDENTITY,
                List.of(first, second)
        );

        IntegrityManifest secondManifest = new IntegrityManifest(
                IDENTITY,
                List.of(second, first)
        );

        assertArrayEquals(
                firstManifest.toCanonicalBytes(),
                secondManifest.toCanonicalBytes()
        );
    }

    @Test
    void shouldIncludeBuildIdentityMetadata() {
        IntegrityManifest manifest = new IntegrityManifest(
                IDENTITY,
                List.of()
        );

        String canonical = new String(
                manifest.toCanonicalBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(canonical.startsWith(
                "RASKOL-INTEGRITY-MANIFEST\t1\n"
        ));

        assertTrue(canonical.contains("PRODUCT\t"));
        assertTrue(canonical.contains("VERSION\t"));
        assertTrue(canonical.contains("BUILD_ID\t"));
        assertTrue(canonical.contains("COMMIT\t"));
        assertTrue(canonical.contains("TIMESTAMP\t"));
        assertTrue(canonical.contains("REPOSITORY\t"));
        assertTrue(canonical.contains("BUILD_TYPE\t"));
    }

    @Test
    void shouldIncludeFileSizeAndHash() {
        IntegrityManifest manifest = new IntegrityManifest(
                IDENTITY,
                List.of(
                        new ManifestFileEntry(
                                "Test.class",
                                123,
                                HASH_A
                        )
                )
        );

        String canonical = new String(
                manifest.toCanonicalBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(canonical.contains("\t123\t" + HASH_A + "\n"));
    }

    @Test
    void shouldRejectDuplicatePaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrityManifest(
                        IDENTITY,
                        List.of(
                                new ManifestFileEntry(
                                        "Test.class", 10, HASH_A
                                ),
                                new ManifestFileEntry(
                                        "Test.class", 20, HASH_B
                                )
                        )
                )
        );
    }

    @Test
    void shouldRejectNullFileList() {
        assertThrows(
                NullPointerException.class,
                () -> new IntegrityManifest(IDENTITY, null)
        );
    }

    @Test
    void shouldRejectNullFileEntry() {
        assertThrows(
                NullPointerException.class,
                () -> new IntegrityManifest(
                        IDENTITY,
                        java.util.Arrays.asList(
                                (ManifestFileEntry) null
                        )
                )
        );
    }

    @Test
    void shouldReturnDefensiveCanonicalByteArray() {
        IntegrityManifest manifest = new IntegrityManifest(
                IDENTITY,
                List.of()
        );

        byte[] first = manifest.toCanonicalBytes();
        first[0] = 0;

        byte[] second = manifest.toCanonicalBytes();

        assertFalse(first[0] == second[0]);
    }
}
