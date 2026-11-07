// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.build;

/**
 * Controls explicit development-mode authorization for RIPS.
 *
 * <p>A build declaring itself as DEVELOPMENT is not sufficient
 * to bypass official integrity verification. The server operator
 * must explicitly enable development mode through a JVM property.</p>
 */
public final class DevelopmentMode {

    public static final String PROPERTY_NAME =
            "raskol.rips.development";

    private DevelopmentMode() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks whether development mode was explicitly enabled.
     *
     * <p>Only the literal value "true" enables this mode.
     * If system properties cannot be read, the method fails closed.</p>
     *
     * @return true when development mode is explicitly enabled
     */
    public static boolean isEnabled() {
        try {
            return "true".equalsIgnoreCase(
                    System.getProperty(PROPERTY_NAME)
            );
        } catch (SecurityException exception) {
            return false;
        }
    }
}
