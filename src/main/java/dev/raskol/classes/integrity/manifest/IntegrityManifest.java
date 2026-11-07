// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.manifest;

import dev.raskol.classes.integrity.build.BuildIdentity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, deterministic integrity manifest.
 *
 * <p>The canonical representation is UTF-8 text with LF line endings.
 * File entries are sorted by relative path.</p>
 *
 * <p>The canonical bytes are intended to be signed directly using
 * the Ed25519 signature service.</p>
 */
public final class IntegrityManifest {

    private static final String FORMAT_HEADER =
            "RASKOL-INTEGRITY-MANIFEST\t1";

    private static final String NEW_LINE = "\n";

    private final BuildIdentity buildIdentity;

    private final List<ManifestFileEntry> files;

    public IntegrityManifest(
            BuildIdentity buildIdentity,
            List<ManifestFileEntry> files
    ) {
        this.buildIdentity = Objects.requireNonNull(
                buildIdentity,
                "buildIdentity cannot be null"
        );

        Objects.requireNonNull(files, "files cannot be null");

        List<ManifestFileEntry> sortedFiles =
                new ArrayList<>(files.size());

        for (ManifestFileEntry file : files) {
            sortedFiles.add(
                    Objects.requireNonNull(
                            file,
                            "Manifest cannot contain null file entries"
                    )
            );
        }

        sortedFiles.sort(
                Comparator.comparing(ManifestFileEntry::path)
        );

        validateUniquePaths(sortedFiles);

        this.files = List.copyOf(sortedFiles);
    }

    public BuildIdentity buildIdentity() {
        return buildIdentity;
    }

    public List<ManifestFileEntry> files() {
        return files;
    }

    /**
     * Returns the canonical UTF-8 representation of this manifest.
     *
     * @return a new byte array containing canonical manifest data
     */
    public byte[] toCanonicalBytes() {
        StringBuilder builder = new StringBuilder();

        builder.append(FORMAT_HEADER).append(NEW_LINE);

        appendMetadata(builder, "PRODUCT", buildIdentity.productId());
        appendMetadata(builder, "VERSION", buildIdentity.pluginVersion());
        appendMetadata(builder, "BUILD_ID", buildIdentity.buildId());
        appendMetadata(builder, "COMMIT", buildIdentity.commitSha());

        appendMetadata(
                builder,
                "TIMESTAMP",
                buildIdentity.buildTimestamp().toString()
        );

        appendMetadata(
                builder,
                "REPOSITORY",
                buildIdentity.sourceRepository()
        );

        appendMetadata(
                builder,
                "BUILD_TYPE",
                buildIdentity.buildType().name()
        );

        for (ManifestFileEntry file : files) {
            builder.append("FILE")
                    .append('\t')
                    .append(encode(file.path()))
                    .append('\t')
                    .append(file.size())
                    .append('\t')
                    .append(file.sha256())
                    .append(NEW_LINE);
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendMetadata(
            StringBuilder builder,
            String key,
            String value
    ) {
        builder.append(key)
                .append('\t')
                .append(encode(value))
                .append(NEW_LINE);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)
                );
    }

    private static void validateUniquePaths(
            List<ManifestFileEntry> files
    ) {
        Set<String> paths = new HashSet<>();

        for (ManifestFileEntry file : files) {
            if (!paths.add(file.path())) {
                throw new IllegalArgumentException(
                        "Duplicate manifest file path: " + file.path()
                );
            }
        }
    }
}
