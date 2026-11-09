// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class ActiveInstallationTest {
    @Test
    void locationIsCopiedOnInputAndOutput() {
        Location source = new Location(null, 10.0, 20.0, 30.0);
        ActiveInstallation installation = new ActiveInstallation(
                java.util.UUID.randomUUID(), InstallationType.WAR_BANNER, java.util.UUID.randomUUID(), source, 100L);

        source.setX(99.0);
        assertEquals(10.0, installation.location().getX());

        Location exposed = installation.location();
        exposed.setX(77.0);
        assertEquals(10.0, installation.location().getX());
    }
}
