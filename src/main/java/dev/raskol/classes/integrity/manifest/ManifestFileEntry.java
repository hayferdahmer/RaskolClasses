// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable description of a protected file included in an integrity manifest.
 *
 * @param path relative normalized file path
 * @param size file size in bytes
 * @param sha256 lowercase hexadecimal SHA-256 digest
 */
public record ManifestFileEntry(String path, long size, String sha256) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");

    public ManifestFileEntry {
        path = validatePath(path);

        if (size < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }

        Objects.requireNonNull(sha256, "sha256 cannot be null");

        if (!SHA256_PATTERN.matcher(sha256).matches()) {
            throw new IllegalArgumentException("SHA-256 must contain exactly 64 lowercase hexadecimal characters");
        }
    }

    private static String validatePath(String value) {
        Objects.requireNonNull(value, "path cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("File path cannot be blank");
        }

        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("File path cannot contain surrounding whitespace");
        }

        if (value.startsWith("/") || value.startsWith("\\")) {
            throw new IllegalArgumentException("Absolute file paths are not allowed");
        }

        if (value.contains("\\")) {
            throw new IllegalArgumentException("File paths must use forward slashes");
        }

        if (value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Drive-qualified paths are not allowed");
        }

        if (value.contains("\t") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("File path contains forbidden control characters");
        }

        String[] segments = value.split("/", -1);

        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("File path contains an invalid segment");
            }
        }

        return value;
    }
}
