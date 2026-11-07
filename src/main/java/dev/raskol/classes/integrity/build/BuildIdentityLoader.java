package dev.raskol.classes.integrity.build;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * Loads build identity metadata from the plugin resources.
 *
 * <p>Expected resource:
 * META-INF/raskol/build.properties</p>
 *
 * <p>If the resource does not exist, a local development
 * identity is returned.</p>
 */
public final class BuildIdentityLoader {

    private static final String RESOURCE_PATH =
            "/META-INF/raskol/build.properties";

    private static final String DEFAULT_VERSION = "UNKNOWN";

    private static final String DEFAULT_PRODUCT_ID = "RaskolClasses";

    private static final String DEFAULT_REPOSITORY =
            "https://github.com/hayferdahmer/RaskolClasses";

    private BuildIdentityLoader() {
        throw new UnsupportedOperationException(
                "Utility class"
        );
    }

    /**
     * Loads build identity from the classpath.
     *
     * @return loaded build identity
     * @throws IllegalStateException if the resource exists but is invalid
     */
    public static BuildIdentity load() {
        try (InputStream inputStream =
                     BuildIdentityLoader.class.getResourceAsStream(
                             RESOURCE_PATH
                     )) {

            if (inputStream == null) {
                return BuildIdentity.development(DEFAULT_VERSION);
            }

            Properties properties = new Properties();
            properties.load(inputStream);

            return parse(properties);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read RaskolClasses build identity",
                    exception
            );
        }
    }

    private static BuildIdentity parse(Properties properties) {
        String productId = get(
                properties,
                "product.id",
                DEFAULT_PRODUCT_ID
        );

        String version = get(
                properties,
                "plugin.version",
                DEFAULT_VERSION
        );

        String buildId = get(
                properties,
                "build.id",
                "LOCAL-DEV"
        );

        String commitSha = get(
                properties,
                "git.commit",
                "UNKNOWN"
        );

        String repository = get(
                properties,
                "source.repository",
                DEFAULT_REPOSITORY
        );

        String typeValue = getRequired(
                properties,
                "build.type"
        );

        BuildType buildType;

        try {
            buildType = BuildType.valueOf(
                    typeValue.toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid RaskolClasses build type: " + typeValue,
                    exception
            );
        }

        String timestampValue = properties.getProperty(
                "build.timestamp"
        );

        Instant timestamp;

        if (timestampValue == null || timestampValue.isBlank()) {
            if (buildType == BuildType.OFFICIAL) {
                throw new IllegalStateException(
                        "Official build is missing build.timestamp"
                );
            }

            timestamp = Instant.EPOCH;
        } else {
            try {
                timestamp = Instant.parse(timestampValue.trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalStateException(
                        "Invalid RaskolClasses build timestamp",
                        exception
                );
            }
        }

        try {
            return new BuildIdentity(
                    productId,
                    version,
                    buildId,
                    commitSha,
                    timestamp,
                    repository,
                    buildType
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid RaskolClasses build identity",
                    exception
            );
        }
    }

    private static String get(
            Properties properties,
            String key,
            String defaultValue
    ) {
        return properties.getProperty(key, defaultValue).trim();
    }

    private static String getRequired(
            Properties properties,
            String key
    ) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required build property: " + key
            );
        }

        return value.trim();
    }
}
