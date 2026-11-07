// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.verification;

/**
 * Possible outcomes of startup integrity verification.
 */
public enum IntegrityStatus {

    /**
     * Official build successfully verified.
     */
    VERIFIED,

    /**
     * Local development build.
     * This status does not establish official authenticity.
     */
    DEVELOPMENT_BUILD,

    TRUSTED_KEY_UNAVAILABLE,

    ARTIFACT_UNAVAILABLE,

    MANIFEST_MISSING,
    MANIFEST_TOO_LARGE,
    SIGNATURE_MISSING,

    INVALID_SIGNATURE,
    INVALID_MANIFEST,
    BUILD_IDENTITY_MISMATCH,

    PROTECTED_FILE_MISSING,
    PROTECTED_FILE_TOO_LARGE,
    PROTECTED_FILE_SIZE_MISMATCH,
    PROTECTED_FILE_HASH_MISMATCH,
    PROTECTED_FILE_READ_ERROR,

    ARCHIVE_INTEGRITY_FAILURE,

    CRYPTOGRAPHIC_ERROR,
    RESOURCE_READ_ERROR
}
