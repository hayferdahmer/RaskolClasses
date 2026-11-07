// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.crypto;

/**
 * Lifecycle status of a source-pinned RIPS public key.
 */
public enum TrustedKeyStatus {

    /**
     * The key is trusted for verification and authorized for signing
     * new official releases by the external release pipeline.
     */
    ACTIVE,

    /**
     * The key remains trusted for verification of existing releases,
     * but must not be used to sign new releases.
     */
    VERIFY_ONLY,

    /**
     * The key is no longer trusted for verification or signing.
     */
    REVOKED
}
