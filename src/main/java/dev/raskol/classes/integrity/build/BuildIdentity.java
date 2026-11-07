// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.build;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable identity information for a RaskolClasses build.
 *
 * <p>Build identity is metadata, not proof of authenticity.
 * Authenticity must be established separately through
 * cryptographic signature verification.</p>
 *
 * @param productId        product identifier
 * @param pluginVersion    plugin version
 * @param buildId          unique build identifier
 * @param commitSha        source Git commit SHA
 * @param buildTimestamp   build creation timestamp
 * @param sourceRepository source repository
 * @param buildType        build classification
 */
public record BuildIdentity(
        String productId,
        String pluginVersion,
        String buildId,
        String commitSha,
        Instant buildTimestamp,
        String sourceRepository,
        BuildType buildType) {

    private static final String DEFAULT_PRODUCT_ID = "RaskolClasses";

    private static final String DEFAULT_REPOSITORY = "https://github.com/hayferdahmer/RaskolClasses";

    public BuildIdentity {
        productId = requireText(productId, "productId");
        pluginVersion = requireText(pluginVersion, "pluginVersion");
        buildId = requireText(buildId, "buildId");
        commitSha = requireText(commitSha, "commitSha");

        buildTimestamp = Objects.requireNonNull(buildTimestamp, "buildTimestamp cannot be null");

        sourceRepository = requireText(sourceRepository, "sourceRepository");

        buildType = Objects.requireNonNull(buildType, "buildType cannot be null");

        if (buildType == BuildType.OFFICIAL) {
            validateOfficialIdentity(buildId, commitSha, buildTimestamp);
        }
    }

    /**
     * Creates a local development identity.
     *
     * <p>This identity must never be treated as an official
     * commercial release.</p>
     *
     * @param pluginVersion plugin version
     * @return development build identity
     */
    public static BuildIdentity development(String pluginVersion) {
        return new BuildIdentity(
                DEFAULT_PRODUCT_ID,
                pluginVersion,
                "LOCAL-DEV",
                "UNKNOWN",
                Instant.EPOCH,
                DEFAULT_REPOSITORY,
                BuildType.DEVELOPMENT);
    }

    /**
     * Indicates whether this identity declares itself as official.
     *
     * <p>This method does not verify its signature.</p>
     *
     * @return true when the declared build type is OFFICIAL
     */
    public boolean isOfficial() {
        return buildType == BuildType.OFFICIAL;
    }

    /**
     * Indicates whether this identity represents development mode.
     *
     * @return true when the declared build type is DEVELOPMENT
     */
    public boolean isDevelopment() {
        return buildType == BuildType.DEVELOPMENT;
    }

    /**
     * Проверяет обязательные ограничения идентификатора официальной сборки.
     *
     * @param buildId идентификатор сборки
     * @param commitSha идентификатор исходного коммита
     * @param buildTimestamp время создания сборки
     */
    private static void validateOfficialIdentity(String buildId, String commitSha, Instant buildTimestamp) {
        if ("UNKNOWN".equalsIgnoreCase(commitSha)) {
            throw new IllegalArgumentException("Official build must contain a valid commit SHA");
        }

        if ("LOCAL-DEV".equalsIgnoreCase(buildId)) {
            throw new IllegalArgumentException("Official build cannot use a development Build ID");
        }

        if (Instant.EPOCH.equals(buildTimestamp)) {
            throw new IllegalArgumentException("Official build must contain a build timestamp");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }

        return normalized;
    }
}
