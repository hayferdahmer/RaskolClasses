// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.policy;

/**
 * Action to take after startup integrity verification.
 */
public enum IntegrityFailureDecision {

    /**
     * Integrity verification permits plugin startup.
     */
    CONTINUE,

    /**
     * Integrity verification does not permit plugin startup.
     */
    DISABLE_PLUGIN
}
