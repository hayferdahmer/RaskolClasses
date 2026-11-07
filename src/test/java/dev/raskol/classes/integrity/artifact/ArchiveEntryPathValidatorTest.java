// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ArchiveEntryPathValidatorTest {

    @Test
    void shouldAcceptValidFilePath() throws IOException {
        assertEquals(
                "dev/raskol/classes/Main.class",
                ArchiveEntryPathValidator.validate("dev/raskol/classes/Main.class", false));
    }

    @Test
    void shouldAcceptValidDirectoryPath() throws IOException {
        assertEquals("dev/raskol/classes", ArchiveEntryPathValidator.validate("dev/raskol/classes/", true));
    }

    @Test
    void shouldRejectNullPath() {
        assertThrows(NullPointerException.class, () -> ArchiveEntryPathValidator.validate(null, false));
    }

    @Test
    void shouldRejectEmptyPath() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("", false));
    }

    @Test
    void shouldRejectAbsolutePath() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("/etc/passwd", false));
    }

    @Test
    void shouldRejectBackslash() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("dev\\raskol\\Main.class", false));
    }

    @Test
    void shouldRejectParentTraversal() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("../outside.txt", false));

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("META-INF/../../outside.txt", false));
    }

    @Test
    void shouldRejectCurrentDirectorySegment() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("dev/./Main.class", false));
    }

    @Test
    void shouldRejectEmptySegments() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("dev//Main.class", false));
    }

    @Test
    void shouldRejectDriveQualifiedPath() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("C:/Windows/system.ini", false));
    }

    @Test
    void shouldRejectDirectoryWithoutTrailingSlash() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("META-INF", true));
    }

    @Test
    void shouldRejectFileWithTrailingSlash() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("config.yml/", false));
    }

    @Test
    void shouldRejectMultipleTrailingSlashesForDirectory() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("META-INF//", true));
    }

    @Test
    void shouldRejectWindowsForbiddenCharacters() {
        String[] invalidNames = {
            "file:name.txt",
            "file<name>.txt",
            "file>name.txt",
            "file\"name.txt",
            "file|name.txt",
            "file?name.txt",
            "file*name.txt"
        };

        for (String name : invalidNames) {
            assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(name, false), name);
        }
    }

    @Test
    void shouldRejectTrailingDotOrSpace() {
        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("config.", false));

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate("config ", false));
    }

    @Test
    void shouldRejectReservedWindowsDeviceNames() {
        String[] invalidNames = {"CON", "PRN.txt", "AUX", "NUL.log", "COM1", "LPT9.txt"};

        for (String name : invalidNames) {
            assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(name, false), name);
        }
    }

    @Test
    void shouldRejectUnicodePathNotNormalizedToNfc() {
        String decomposedName = "cafe\u0301.txt";

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(decomposedName, false));
    }

    @Test
    void shouldAcceptUnicodePathAlreadyNormalizedToNfc() throws IOException {
        String normalizedName = "caf\u00e9.txt";

        assertEquals(normalizedName, ArchiveEntryPathValidator.validate(normalizedName, false));
    }

    @Test
    void shouldRejectUnicodeFormattingCharacters() {
        String nameWithBidiOverride = "safe\u202Etxt";

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(nameWithBidiOverride, false));
    }

    @Test
    void shouldRejectControlCharacters() {
        String nameWithControlCharacter = "safe\u0001.txt";

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(nameWithControlCharacter, false));
    }

    @Test
    void shouldRejectOverlongPathSegment() {
        String longSegment = "a".repeat(256) + ".txt";

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(longSegment, false));
    }

    @Test
    void shouldRejectOverlongPath() {
        String longPath = "a".repeat(4097);

        assertThrows(IOException.class, () -> ArchiveEntryPathValidator.validate(longPath, false));
    }

    @Test
    void shouldGenerateCaseInsensitiveComparisonKey() {
        assertEquals(
                "meta-inf/raskol/config.yml", ArchiveEntryPathValidator.comparisonKey("META-INF/Raskol/Config.yml"));
    }
}
