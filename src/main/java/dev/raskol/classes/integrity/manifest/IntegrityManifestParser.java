// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import dev.raskol.classes.integrity.build.BuildIdentity;
import dev.raskol.classes.integrity.build.BuildType;

/**
 * Strict parser for canonical RaskolClasses integrity manifests.
 *
 * <p>The parser accepts only the exact canonical format emitted by
 * {@link IntegrityManifest#toCanonicalBytes()}.</p>
 *
 * <p>Callers must verify the detached signature before parsing
 * untrusted manifest bytes.</p>
 */
public final class IntegrityManifestParser {

    private static final String FORMAT_HEADER = "RASKOL-INTEGRITY-MANIFEST\t1";

    private static final int METADATA_LINE_COUNT = 7;

    private static final int HEADER_LINE_COUNT = METADATA_LINE_COUNT + 1;

    private IntegrityManifestParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses exact canonical manifest bytes.
     *
     * @param manifestBytes exact manifest bytes
     * @return parsed immutable manifest
     * @throws IllegalArgumentException if the input is invalid
     */
    public static IntegrityManifest parse(byte[] manifestBytes) {
        Objects.requireNonNull(manifestBytes, "manifestBytes cannot be null");

        String content = decodeUtf8(manifestBytes);

        if (content.isEmpty()) {
            throw new IllegalArgumentException("Manifest cannot be empty");
        }

        if (!content.endsWith("\n")) {
            throw new IllegalArgumentException("Manifest must end with LF");
        }

        String[] lines = content.split("\n", -1);

        if (lines.length < HEADER_LINE_COUNT + 1) {
            throw new IllegalArgumentException("Manifest is incomplete");
        }

        if (!FORMAT_HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported manifest header or version");
        }

        BuildIdentity identity = parseIdentity(lines);

        List<ManifestFileEntry> files = parseFiles(lines);

        IntegrityManifest manifest = new IntegrityManifest(identity, files);

        if (!java.util.Arrays.equals(manifest.toCanonicalBytes(), manifestBytes)) {
            throw new IllegalArgumentException("Manifest is not in canonical form");
        }

        return manifest;
    }

    private static BuildIdentity parseIdentity(String[] lines) {
        String product = parseMetadata(lines[1], "PRODUCT");
        String version = parseMetadata(lines[2], "VERSION");
        String buildId = parseMetadata(lines[3], "BUILD_ID");
        String commit = parseMetadata(lines[4], "COMMIT");
        String timestamp = parseMetadata(lines[5], "TIMESTAMP");
        String repository = parseMetadata(lines[6], "REPOSITORY");
        String buildTypeValue = parseMetadata(lines[7], "BUILD_TYPE");

        final Instant buildTimestamp;

        try {
            buildTimestamp = Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid build timestamp", exception);
        }

        final BuildType buildType;

        try {
            buildType = BuildType.valueOf(buildTypeValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported build type", exception);
        }

        try {
            return new BuildIdentity(product, version, buildId, commit, buildTimestamp, repository, buildType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid build identity in manifest", exception);
        }
    }

    private static List<ManifestFileEntry> parseFiles(String[] lines) {
        List<ManifestFileEntry> files = new ArrayList<>();

        for (int index = HEADER_LINE_COUNT; index < lines.length - 1; index++) {
            String line = lines[index];

            if (line.isEmpty()) {
                throw new IllegalArgumentException("Unexpected empty line in manifest");
            }

            String[] fields = line.split("\t", -1);

            if (fields.length != 4 || !"FILE".equals(fields[0])) {
                throw new IllegalArgumentException("Invalid file entry at line " + (index + 1));
            }

            String path = decode(fields[1]);
            long size = parseSize(fields[2]);
            String sha256 = fields[3];

            try {
                files.add(new ManifestFileEntry(path, size, sha256));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid file entry at line " + (index + 1), exception);
            }
        }

        return files;
    }

    private static String parseMetadata(String line, String expectedKey) {
        String[] fields = line.split("\t", -1);

        if (fields.length != 2 || !expectedKey.equals(fields[0])) {
            throw new IllegalArgumentException("Expected metadata field: " + expectedKey);
        }

        return decode(fields[1]);
    }

    private static String decode(String encoded) {
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded manifest value cannot be empty");
        }

        final byte[] decoded;

        try {
            decoded = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Base64 URL value in manifest", exception);
        }

        String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);

        if (!canonical.equals(encoded)) {
            throw new IllegalArgumentException("Non-canonical Base64 URL value in manifest");
        }

        return decodeUtf8(decoded);
    }

    private static long parseSize(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("File size cannot be empty");
        }

        final long size;

        try {
            size = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid file size in manifest", exception);
        }

        if (size < 0 || !Long.toString(size).equals(value)) {
            throw new IllegalArgumentException("File size is not canonical");
        }

        return size;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Manifest contains invalid UTF-8", exception);
        }
    }
}
