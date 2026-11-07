// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;

class IntegrityManifestParserTest {

    private static final String HASH = "a".repeat(64);

    private static final BuildIdentity IDENTITY = new BuildIdentity(
            "RaskolClasses",
            "1.15.8",
            "BUILD-TEST-001",
            "0123456789abcdef",
            Instant.parse("2026-11-07T10:00:00Z"),
            "https://github.com/hayferdahmer/RaskolClasses",
            BuildType.OFFICIAL);

    @Test
    void shouldParseCanonicalManifest() {
        IntegrityManifest original = new IntegrityManifest(
                IDENTITY, List.of(new ManifestFileEntry("dev/raskol/classes/Example.class", 128, HASH)));

        IntegrityManifest parsed = IntegrityManifestParser.parse(original.toCanonicalBytes());

        assertEquals(IDENTITY, parsed.buildIdentity());
        assertEquals(original.files(), parsed.files());

        assertArrayEquals(original.toCanonicalBytes(), parsed.toCanonicalBytes());
    }

    @Test
    void shouldParseManifestWithoutFiles() {
        IntegrityManifest original = new IntegrityManifest(IDENTITY, List.of());

        IntegrityManifest parsed = IntegrityManifestParser.parse(original.toCanonicalBytes());

        assertTrue(parsed.files().isEmpty());
        assertEquals(IDENTITY, parsed.buildIdentity());
    }

    @Test
    void shouldRejectEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> IntegrityManifestParser.parse(new byte[0]));
    }

    @Test
    void shouldRejectMissingFinalLineFeed() {
        byte[] canonical = new IntegrityManifest(IDENTITY, List.of()).toCanonicalBytes();

        byte[] modified = new byte[canonical.length - 1];

        System.arraycopy(canonical, 0, modified, 0, modified.length);

        assertThrows(IllegalArgumentException.class, () -> IntegrityManifestParser.parse(modified));
    }

    @Test
    void shouldRejectUnsupportedHeader() {
        byte[] canonical = new IntegrityManifest(IDENTITY, List.of()).toCanonicalBytes();

        String content = new String(canonical, StandardCharsets.UTF_8);

        byte[] modified = content.replace("RASKOL-INTEGRITY-MANIFEST\t1", "RASKOL-INTEGRITY-MANIFEST\t2")
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> IntegrityManifestParser.parse(modified));
    }

    @Test
    void shouldRejectReorderedMetadata() {
        byte[] canonical = new IntegrityManifest(IDENTITY, List.of()).toCanonicalBytes();

        String content = new String(canonical, StandardCharsets.UTF_8);

        String modified = content.replaceFirst("PRODUCT\\t", "VERSION\t");

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityManifestParser.parse(modified.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectInvalidUtf8() {
        byte[] invalid = {(byte) 0xC3, (byte) 0x28};

        assertThrows(IllegalArgumentException.class, () -> IntegrityManifestParser.parse(invalid));
    }

    @Test
    void shouldRejectWindowsLineEndings() {
        byte[] canonical = new IntegrityManifest(IDENTITY, List.of()).toCanonicalBytes();

        String content = new String(canonical, StandardCharsets.UTF_8);

        byte[] modified = content.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> IntegrityManifestParser.parse(modified));
    }

    @Test
    void shouldRejectNonCanonicalFileSize() {
        IntegrityManifest original =
                new IntegrityManifest(IDENTITY, List.of(new ManifestFileEntry("example.class", 10, HASH)));

        String content = new String(original.toCanonicalBytes(), StandardCharsets.UTF_8);

        String modified = content.replace("\t10\t", "\t010\t");

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityManifestParser.parse(modified.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectInvalidFileHash() {
        IntegrityManifest original =
                new IntegrityManifest(IDENTITY, List.of(new ManifestFileEntry("example.class", 10, HASH)));

        String content = new String(original.toCanonicalBytes(), StandardCharsets.UTF_8);

        String modified = content.replace(HASH, "INVALID");

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityManifestParser.parse(modified.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectDuplicateFileEntries() {
        IntegrityManifest original =
                new IntegrityManifest(IDENTITY, List.of(new ManifestFileEntry("example.class", 10, HASH)));

        String content = new String(original.toCanonicalBytes(), StandardCharsets.UTF_8);

        String fileLine = content.substring(content.indexOf("FILE\t"));

        String modified = content + fileLine;

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityManifestParser.parse(modified.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectUnknownBuildType() {
        byte[] canonical = new IntegrityManifest(IDENTITY, List.of()).toCanonicalBytes();

        String content = new String(canonical, StandardCharsets.UTF_8);

        String encodedOfficial = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("UNKNOWN".getBytes(StandardCharsets.UTF_8));

        String modified = content.replace(
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("OFFICIAL".getBytes(StandardCharsets.UTF_8)),
                encodedOfficial);

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityManifestParser.parse(modified.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectNonCanonicalBase64() {
        byte[] canonical = new IntegrityManifest(IDENTITY, List.of()).toCanonicalBytes();

        String content = new String(canonical, StandardCharsets.UTF_8);

        String modified = content.replaceFirst("PRODUCT\t", "PRODUCT\t=");

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityManifestParser.parse(modified.getBytes(StandardCharsets.UTF_8)));
    }
}
