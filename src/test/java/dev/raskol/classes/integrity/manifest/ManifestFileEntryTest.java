// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManifestFileEntryTest {

    private static final String VALID_HASH = "a".repeat(64);

    @Test
    void shouldAcceptValidEntry() {
        assertDoesNotThrow(() -> new ManifestFileEntry("dev/raskol/Test.class", 1024, VALID_HASH));
    }

    @Test
    void shouldRejectNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestFileEntry("Test.class", -1, VALID_HASH));
    }

    @Test
    void shouldRejectInvalidHash() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestFileEntry("Test.class", 10, "invalid"));
    }

    @Test
    void shouldRejectAbsolutePath() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestFileEntry("/etc/passwd", 10, VALID_HASH));
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(
                IllegalArgumentException.class, () -> new ManifestFileEntry("classes/../secret.class", 10, VALID_HASH));
    }

    @Test
    void shouldRejectWindowsPathSeparator() {
        assertThrows(
                IllegalArgumentException.class, () -> new ManifestFileEntry("classes\\Test.class", 10, VALID_HASH));
    }

    @Test
    void shouldRejectEmptyPathSegments() {
        assertThrows(
                IllegalArgumentException.class, () -> new ManifestFileEntry("classes//Test.class", 10, VALID_HASH));
    }
}
