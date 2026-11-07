// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.build;

/**
 * Defines the trust category of a RaskolClasses build.
 *
 * <p>The build type describes how the artifact was produced.
 * It does not independently establish authenticity.</p>
 */
public enum BuildType {

    /**
     * Official build produced by a trusted release pipeline.
     */
    OFFICIAL,

    /**
     * Local or development build.
     */
    DEVELOPMENT
}
