package dev.raskol.classes.integrity.build;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BuildIdentityTest {

    @Test
    void shouldCreateDevelopmentIdentity() {
        BuildIdentity identity =
                BuildIdentity.development("1.16.0-SNAPSHOT");

        assertEquals("RaskolClasses", identity.productId());
        assertEquals("1.16.0-SNAPSHOT", identity.pluginVersion());
        assertEquals("LOCAL-DEV", identity.buildId());
        assertEquals("UNKNOWN", identity.commitSha());

        assertEquals(BuildType.DEVELOPMENT, identity.buildType());

        assertTrue(identity.isDevelopment());
        assertFalse(identity.isOfficial());
    }

    @Test
    void shouldCreateOfficialIdentity() {
        BuildIdentity identity = new BuildIdentity(
                "RaskolClasses",
                "1.16.0",
                "RC-1.16.0-A1B2C3D4",
                "a1b2c3d4e5f6789012345678901234567890abcd",
                Instant.parse("2026-11-07T12:00:00Z"),
                "https://github.com/hayferdahmer/RaskolClasses",
                BuildType.OFFICIAL
        );

        assertTrue(identity.isOfficial());
        assertFalse(identity.isDevelopment());

        assertEquals(
                "RC-1.16.0-A1B2C3D4",
                identity.buildId()
        );
    }

    @Test
    void shouldRejectBlankProductId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BuildIdentity(
                        " ",
                        "1.16.0",
                        "RC-TEST",
                        "a1b2c3d4",
                        Instant.now(),
                        "https://github.com/hayferdahmer/RaskolClasses",
                        BuildType.DEVELOPMENT
                )
        );
    }

    @Test
    void shouldRejectOfficialBuildWithoutCommit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BuildIdentity(
                        "RaskolClasses",
                        "1.16.0",
                        "RC-TEST",
                        "UNKNOWN",
                        Instant.now(),
                        "https://github.com/hayferdahmer/RaskolClasses",
                        BuildType.OFFICIAL
                )
        );
    }

    @Test
    void shouldRejectOfficialBuildWithDevelopmentId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BuildIdentity(
                        "RaskolClasses",
                        "1.16.0",
                        "LOCAL-DEV",
                        "a1b2c3d4",
                        Instant.now(),
                        "https://github.com/hayferdahmer/RaskolClasses",
                        BuildType.OFFICIAL
                )
        );
    }

    @Test
    void shouldRejectOfficialBuildWithEpochTimestamp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BuildIdentity(
                        "RaskolClasses",
                        "1.16.0",
                        "RC-TEST",
                        "a1b2c3d4",
                        Instant.EPOCH,
                        "https://github.com/hayferdahmer/RaskolClasses",
                        BuildType.OFFICIAL
                )
        );
    }

    @Test
    void shouldRejectNullBuildType() {
        assertThrows(
                NullPointerException.class,
                () -> new BuildIdentity(
                        "RaskolClasses",
                        "1.16.0",
                        "RC-TEST",
                        "a1b2c3d4",
                        Instant.now(),
                        "https://github.com/hayferdahmer/RaskolClasses",
                        null
                )
        );
    }
}
