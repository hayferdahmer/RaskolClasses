// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.util.List;

/**
 * Результат верификации целостности JAR-архива в RIPS.
 *
 * 1.15.8: подкласс ArchiveIntegrityVerificationResult — наследует контракт
 * isVerified/status/checkedFiles/failedFiles; добавляет jar-специфичные поля
 * (expectedSha256/actualSha256) для диагностических отчётов.
 */
public class JarArchiveIntegrityVerificationResult extends ArchiveIntegrityVerificationResult {

    private final String expectedSha256;
    private final String actualSha256;

    public JarArchiveIntegrityVerificationResult(
            boolean isVerified,
            Status status,
            List<String> checkedFiles,
            List<String> failedFiles,
            String message,
            String expectedSha256,
            String actualSha256
    ) {
        super(isVerified, status, checkedFiles, failedFiles, message);
        this.expectedSha256 = expectedSha256 == null ? "" : expectedSha256;
        this.actualSha256 = actualSha256 == null ? "" : actualSha256;
    }

    public String expectedSha256() {
        return expectedSha256;
    }

    public String actualSha256() {
        return actualSha256;
    }

    /** Фабрика: целостность подтверждена. */
    public static JarArchiveIntegrityVerificationResult ok(
            List<String> checkedFiles,
            String expectedSha256,
            String actualSha256
    ) {
        return new JarArchiveIntegrityVerificationResult(
                true, Status.VERIFIED, checkedFiles, List.of(),
                "jar integrity verified", expectedSha256, actualSha256);
    }

    /** Фабрика: обнаружены расхождения. */
    public static JarArchiveIntegrityVerificationResult failed(
            List<String> checkedFiles,
            List<String> failedFiles,
            String message,
            String expectedSha256,
            String actualSha256
    ) {
        return new JarArchiveIntegrityVerificationResult(
                false, Status.FAILED, checkedFiles, failedFiles,
                message, expectedSha256, actualSha256);
    }

    /** Фабрика: манифест повреждён или отсутствует. */
    public static JarArchiveIntegrityVerificationResult corrupt(String message) {
        return new JarArchiveIntegrityVerificationResult(
                false, Status.CORRUPT_MANIFEST, List.of(), List.of(),
                message, "", "");
    }

    /** Фабрика: архив недоступен. */
    public static JarArchiveIntegrityVerificationResult missing(String message) {
        return new JarArchiveIntegrityVerificationResult(
                false, Status.MISSING, List.of(), List.of(),
                message, "", "");
    }
}
