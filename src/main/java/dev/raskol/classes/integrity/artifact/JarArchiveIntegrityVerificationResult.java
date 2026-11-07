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
 * ENTRY_READ_ERROR в верификаторе. Фабрики синхронизированы с enum
 * ArchiveIntegrityStatus (16 констант).
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

    /* ------------------------------ универсальная фабрика ------------------------------ */

    /**
     * Универсальная фабрика для провального результата с произвольным статусом.
     * Все специализированные фабрики ниже делегируют сюда.
     */
    public static JarArchiveIntegrityVerificationResult failed(
            ArchiveIntegrityStatus status,
            String message,
            int archiveEntries,
            int checkedFiles,
            List<String> failedFiles,
            String expectedSha256,
            String actualSha256,
            long archiveSizeBytes
    ) {
        if (status == ArchiveIntegrityStatus.VERIFIED) {
            throw new IllegalArgumentException(
                    "failed() cannot be used with VERIFIED status; use ok() instead"
            );
        }
        return new JarArchiveIntegrityVerificationResult(
                status, message, archiveEntries, checkedFiles,
                failedFiles == null ? List.of() : failedFiles,
                expectedSha256, actualSha256, archiveSizeBytes
        );
    }

    /* ------------------------------ успех ------------------------------ */

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

    /* ------------------------------ ошибки чтения архива ------------------------------ */

    /**
     * Фабрика: архив не удалось открыть/прочитать.
     * ArtifactBoundIntegrityVerifier маппит этот статус в IntegrityStatus.ARTIFACT_UNAVAILABLE.
     */
    public static JarArchiveIntegrityVerificationResult archiveReadError(String message) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.ARCHIVE_READ_ERROR,
                message, 0, 0, List.of(), "", "", 0L
        );
    }

    /**
     * Фабрика: конкретная запись архива не читается (битый поток/недопустимая кодировка).
     * ArtifactBoundIntegrityVerifier маппит этот статус в IntegrityStatus.ARTIFACT_UNAVAILABLE.
     */
    public static JarArchiveIntegrityVerificationResult entryReadError(
            String message, List<String> failedFiles
    ) {
        return new JarArchiveIntegrityVerificationResult(
                ArchiveIntegrityStatus.ENTRY_READ_ERROR,
                message, 0, 0,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /* ------------------------------ лимиты архива ------------------------------ */

    /** Фабрика: превышен лимит числа записей в архиве (защита от zip-bomb). */
    public static JarArchiveIntegrityVerificationResult entryCountLimitExceeded(
            String message, int archiveEntries
    ) {
        return failed(
                ArchiveIntegrityStatus.ARCHIVE_ENTRY_COUNT_LIMIT_EXCEEDED,
                message, archiveEntries, 0, List.of(), "", "", 0L
        );
    }

    /** Фабрика: превышен общий размер архива (защита от zip-bomb). */
    public static JarArchiveIntegrityVerificationResult totalSizeLimitExceeded(
            String message, long archiveSizeBytes
    ) {
        return failed(
                ArchiveIntegrityStatus.ARCHIVE_TOTAL_SIZE_LIMIT_EXCEEDED,
                message, 0, 0, List.of(), "", "", archiveSizeBytes
        );
    }

    /** Фабрика: отдельная запись превышает лимит размера. */
    public static JarArchiveIntegrityVerificationResult entryTooLarge(
            String message, List<String> failedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.ENTRY_TOO_LARGE,
                message, 0, 0,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /* ------------------------------ структура архива ------------------------------ */

    /** Фабрика: дубликат записей архива (один путь встречается дважды). */
    public static JarArchiveIntegrityVerificationResult duplicateEntry(
            String message, List<String> failedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.DUPLICATE_ARCHIVE_ENTRY,
                message, 0, 0,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /** Фабрика: конфликт путей в архиве (например, одновременно файл и каталог). */
    public static JarArchiveIntegrityVerificationResult pathCollision(
            String message, List<String> failedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.ARCHIVE_PATH_COLLISION,
                message, 0, 0,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /** Фабрика: путь записи невалидный (абсолютный, содержит ../, пустой и т.п.). */
    public static JarArchiveIntegrityVerificationResult invalidEntryPath(
            String message, List<String> failedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.INVALID_ARCHIVE_ENTRY_PATH,
                message, 0, 0,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /** Фабрика: неожиданная запись архива (не указана в манифесте, не является META-INF/служебной). */
    public static JarArchiveIntegrityVerificationResult unexpectedEntry(
            String message, List<String> failedFiles, int archiveEntries, int checkedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_ENTRY,
                message, archiveEntries, checkedFiles,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /** Фабрика: неожиданный каталог в архиве (не разрешён манифестом). */
    public static JarArchiveIntegrityVerificationResult unexpectedDirectory(
            String message, List<String> failedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.UNEXPECTED_ARCHIVE_DIRECTORY,
                message, 0, 0,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /* ------------------------------ расхождения с манифестом ------------------------------ */

    /** Фабрика: запись манифеста не найдена в архиве (файл исчез). */
    public static JarArchiveIntegrityVerificationResult manifestEntryMissing(
            String message, List<String> failedFiles, int checkedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.MANIFEST_ENTRY_MISSING,
                message, 0, checkedFiles,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /** Фабрика: размер записи в архиве не совпадает с манифестом. */
    public static JarArchiveIntegrityVerificationResult sizeMismatch(
            String message, List<String> failedFiles, int archiveEntries, int checkedFiles
    ) {
        return failed(
                ArchiveIntegrityStatus.ENTRY_SIZE_MISMATCH,
                message, archiveEntries, checkedFiles,
                failedFiles == null ? List.of() : failedFiles,
                "", "", 0L
        );
    }

    /** Фабрика: SHA-256 запись архива не совпадает с ожидаемым дайджестом из манифеста. */
    public static JarArchiveIntegrityVerificationResult hashMismatch(
            String message,
            List<String> failedFiles,
            int archiveEntries,
            int checkedFiles,
            String expectedSha256,
            String actualSha256,
            long archiveSizeBytes
    ) {
        return failed(
                ArchiveIntegrityStatus.ENTRY_HASH_MISMATCH,
                message, archiveEntries, checkedFiles,
                failedFiles == null ? List.of() : failedFiles,
                expectedSha256, actualSha256, archiveSizeBytes
        );
    }

    /* ------------------------------ манифест архива ------------------------------ */

    /** Фабрика: манифест внутри архива пустой. */
    public static JarArchiveIntegrityVerificationResult manifestEmpty(String message) {
        return failed(
                ArchiveIntegrityStatus.MANIFEST_EMPTY,
                message, 0, 0, List.of(), "", "", 0L
        );
    }

    /** Фабрика: манифест внутри архива невалидный (некорректный формат). */
    public static JarArchiveIntegrityVerificationResult invalidManifest(String message) {
        return failed(
                ArchiveIntegrityStatus.INVALID_MANIFEST,
                message, 0, 0, List.of(), "", "", 0L
        );
    }
}
