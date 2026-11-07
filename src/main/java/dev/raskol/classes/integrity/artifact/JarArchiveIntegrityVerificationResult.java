// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.util.List;
import java.util.Objects;

/**
 * Иммутабельный результат полной верификации целостности JAR-архива.
 *
 * <p>Расширяет контракт {@link ArchiveIntegrityVerificationResult} дополнительными
 * полями, специфичными для jar: ожидаемым/фактическим SHA-256 дайджестом архива
 * и размером архива. Эти поля используются в диагностических отчётах и при
 * составлении сообщений об ошибках RIPS-пайплайна.</p>
 *
 * <p>Record не наследует базовый (record'ы в Java не поддерживают наследование),
 * но дублирует его поля и метод {@link #isVerified()} для совместимости с
 * {@link ArtifactBoundIntegrityVerifier}, который работает с результатом
 * через accessor'ы record'а (status/message/checkedFiles/failedFiles).</p>
 *
 * @param status статус верификации (из ArchiveIntegrityStatus)
 * @param message человеко-читаемое описание результата
 * @param archiveEntries количество записей архива, подвергшихся проверке
 * @param checkedFiles число файлов, успешно проверенных
 * @param failedFiles список путей файлов, связанных с ошибкой верификации
 * @param expectedSha256 ожидаемый SHA-256 дайджест архива (из манифеста)
 * @param actualSha256 фактический SHA-256 дайджест прочитанного архива
 * @param archiveSizeBytes размер архива в байтах (для диагностики)
 *
 * 1.15.8: record создан для поддержки ArtifactBoundIntegrityVerifier; контракт
 * восстановлен по вызовам archiveResult.isVerified()/status()/message()/
 * checkedFiles()/failedFiles() и по switch по статусу ARCHIVE_READ_ERROR /
 * ENTRY_READ_ERROR в верификаторе.
 */
public record JarArchiveIntegrityVerificationResult(
        ArchiveIntegrityStatus status,
        String message,
        int archiveEntries,
        int checkedFiles,
        List<String> failedFiles,
        String expectedSha256,
        String actualSha256,
        long archiveSizeBytes
) {

    /** Компактный конструктор с валидацией (по аналогии с базовым record). */
    public JarArchiveIntegrityVerificationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(failedFiles, "failedFiles");

        if (archiveEntries < 0) {
            throw new IllegalArgumentException("archiveEntries cannot be negative");
        }

        if (checkedFiles < 0) {
            throw new IllegalArgumentException("checkedFiles cannot be negative");
        }

        if (archiveSizeBytes < 0) {
            throw new IllegalArgumentException("archiveSizeBytes cannot be negative");
        }

        failedFiles = List.copyOf(failedFiles);
        if (expectedSha256 == null) {
            expectedSha256 = "";
        }
        if (actualSha256 == null) {
            actualSha256 = "";
        }
    }

    /**
     * Возвращает true, если целостность архива подтверждена
     * (статус VERIFIED). Совпадает по контракту с аналогичным методом
     * базового ArchiveIntegrityVerificationResult.
     */
    public boolean isVerified() {
        return status == ArchiveIntegrityStatus.VERIFIED;
    }

    /* ------------------------------ фабрики результатов ------------------------------ */

    /** Фабрика успешного результата верификации. */
    public static JarArchiveIntegrityVerificationResult ok(
            int archiveEntries,
            int checkedFiles,
            String expectedSha256,
            String actualSha256,
            long archiveSizeBytes
    ) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.VERIFIED,
                "JAR archive integrity verified",
                archiveEntries,
                checkedFiles,
                List.of(),
                expectedSha256,
                actualSha256,
                archiveSizeBytes
        );
    }

    /** Фабрика результата с ошибкой чтения архива (архив недоступен/повреждён). */
    public static JarArchiveIntegrityVerificationResult archiveReadError(String message) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.ARCHIVE_READ_ERROR,
                message,
                0,
                0,
                List.of(),
                "",
                "",
                0L
        );
    }

    /** Фабрика результата с ошибкой чтения конкретной записи архива. */
    public static JarArchiveIntegrityVerificationResult entryReadError(
            String message,
            List<String> failedFiles
    ) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.ENTRY_READ_ERROR,
                message,
                0,
                0,
                failedFiles == null ? List.of() : failedFiles,
                "",
                "",
                0L
        );
    }

    /** Фабрика результата с расхождением хэшей (целостность нарушена). */
    public static JarArchiveIntegrityVerificationResult integrityFailure(
            String message,
            int archiveEntries,
            int checkedFiles,
            List<String> failedFiles,
            String expectedSha256,
            String actualSha256,
            long archiveSizeBytes
    ) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.INTEGRITY_FAILURE,
                message,
                archiveEntries,
                checkedFiles,
                failedFiles == null ? List.of() : failedFiles,
                expectedSha256,
                actualSha256,
                archiveSizeBytes
        );
    }

    /** Фабрика результата с невалидным/отсутствующим манифестом внутри архива. */
    public static JarArchiveIntegrityVerificationResult invalidManifest(String message) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.INVALID_MANIFEST,
                message,
                0,
                0,
                List.of(),
                "",
                "",
                0L
        );
    }
}
