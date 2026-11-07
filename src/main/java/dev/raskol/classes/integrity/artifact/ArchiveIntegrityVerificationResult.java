// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of complete JAR archive integrity verification.
 *
 * @param status verification status
 * @param message human-readable result description
 * @param archiveEntries number of archive entries inspected
 * @param checkedFiles number of files successfully verified
 * @param failedFiles files associated with verification failure
 */
public record ArchiveIntegrityVerificationResult(
        ArchiveIntegrityStatus status,
        String message,
        int archiveEntries,
        int checkedFiles,
        List<String> failedFiles
) {

    public ArchiveIntegrityVerificationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(failedFiles, "failedFiles");

        if (archiveEntries < 0) {
            throw new IllegalArgumentException("archiveEntries cannot be negative");
        }

        if (checkedFiles < 0) {
            throw new IllegalArgumentException("checkedFiles cannot be negative");
        }

        failedFiles = List.copyOf(failedFiles);
    }

    public boolean isVerified() {
        return status == ArchiveIntegrityStatus.VERIFIED;
    }
}
