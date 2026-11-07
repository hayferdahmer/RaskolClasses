// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.util.List;
import java.util.Objects;

/**
 * Базовый результат верификации целостности архива в RIPS.
 *
 * 1.15.8: контракт восстановлен по вызовам в ArtifactBoundIntegrityVerifier —
 * методы isVerified(), status(), checkedFiles(), failedFiles() требуются всем
 * верификаторам архивов (JarArchiveIntegrityVerificationResult и др.).
 */
public class ArchiveIntegrityVerificationResult {

    public enum Status {
        /** Все хэши/подписи валидны. */
        VERIFIED,
        /** Обнаружены расхождения или невалидные подписи. */
        FAILED,
        /** Архив отсутствует или недоступен. */
        MISSING,
        /** Манифест не найден или повреждён. */
        CORRUPT_MANIFEST,
        /** Неизвестный статус (ошибка верификатора). */
        UNKNOWN
    }

    private final boolean isVerified;
    private final Status status;
    private final List<String> checkedFiles;
    private final List<String> failedFiles;
    private final String message;

    public ArchiveIntegrityVerificationResult(
            boolean isVerified,
            Status status,
            List<String> checkedFiles,
            List<String> failedFiles,
            String message
    ) {
        this.isVerified = isVerified;
        this.status = status != null ? status : Status.UNKNOWN;
        this.checkedFiles = checkedFiles == null ? List.of() : List.copyOf(checkedFiles);
        this.failedFiles = failedFiles == null ? List.of() : List.copyOf(failedFiles);
        this.message = message == null ? "" : message;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public Status status() {
        return status;
    }

    public List<String> checkedFiles() {
        return checkedFiles;
    }

    public List<String> failedFiles() {
        return failedFiles;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{verified=" + isVerified
                + ", status=" + status
                + ", checked=" + checkedFiles.size()
                + ", failed=" + failedFiles.size()
                + ", message='" + message + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArchiveIntegrityVerificationResult that)) return false;
        return isVerified == that.isVerified
                && status == that.status
                && Objects.equals(checkedFiles, that.checkedFiles)
                && Objects.equals(failedFiles, that.failedFiles)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isVerified, status, checkedFiles, failedFiles, message);
    }
}
