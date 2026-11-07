// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.runtime;

/**
 * Current state of the runtime integrity monitor.
 */
public enum RuntimeIntegrityState {

    /**
     * Monitoring has not been started yet.
     */
    NOT_STARTED,

    /**
     * An integrity check is currently running.
     */
    RUNNING,

    /**
     * The latest integrity check succeeded.
     */
    VERIFIED,

    /**
     * The monitored build is a development build.
     */
    DEVELOPMENT_BUILD,

    /**
     * The latest integrity check failed.
     */
    FAILED,

    /**
     * Monitoring has been stopped.
     */
    STOPPED
}
