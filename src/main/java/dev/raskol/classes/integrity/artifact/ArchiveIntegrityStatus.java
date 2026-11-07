// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

/**
 * Result statuses for complete JAR archive integrity verification.
 */
public enum ArchiveIntegrityStatus {

    VERIFIED,

    MANIFEST_EMPTY,
    INVALID_MANIFEST,

    ARCHIVE_READ_ERROR,
    ARCHIVE_ENTRY_COUNT_LIMIT_EXCEEDED,
    ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,

    DUPLICATE_ARCHIVE_ENTRY,
    INVALID_ARCHIVE_ENTRY_PATH,
    UNEXPECTED_ARCHIVE_ENTRY,
    MANIFEST_ENTRY_MISSING,

    ENTRY_TOO_LARGE,
    ENTRY_SIZE_MISMATCH,
    ENTRY_HASH_MISMATCH,
    ENTRY_READ_ERROR
}
