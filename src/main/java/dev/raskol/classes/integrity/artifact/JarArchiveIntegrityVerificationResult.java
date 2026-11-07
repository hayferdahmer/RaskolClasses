// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.integrity.artifact;

import java.util.List;

/**
 * Результат верификации целостности jar-архива в рамках RIPS-пайплайна.
 *
 * @param valid true если все проверки целостности прошли (хэши, подпись манифеста,
 *              binding к публичному ключу)
 * @param message человеко-читаемое описание результата (для логов и /rc debug)
 * @param expectedSha256 ожидаемый SHA-256 дайджест архива (из манифеста или registry)
 * @param actualSha256 фактический SHA-256 дайджест прочитанного архива
 * @param mismatchedEntries список путей внутри jar, чьи хэши не совпали (пусто при valid=true)
 *
 * 1.15.8: record создан для поддержки ArtifactBoundIntegrityVerifier (класс ссылался
 * на него, но файл отсутствовал в репо).
 */
public record JarArchiveIntegrityVerificationResult(
        boolean valid,
        String message,
        String expectedSha256,
        String actualSha256,
        List<String> mismatchedEntries
) {

    /** Фабрика успешного результата. */
    public static JarArchiveIntegrityVerificationResult ok(
            String expectedSha256, String actualSha256) {
        return new JarArchiveIntegrityVerificationResult(
                true, "jar integrity verified",
                expectedSha256, actualSha256, List.of());
    }

    /** Фабрика провала с одной причиной. */
    public static JarArchiveIntegrityVerificationResult failed(
            String message, String expectedSha256, String actualSha256) {
        return new JarArchiveIntegrityVerificationResult(
                false, message, expectedSha256, actualSha256, List.of());
    }

    /** Фабрика провала со списком расходящихся путей. */
    public static JarArchiveIntegrityVerificationResult mismatched(
            String expectedSha256, String actualSha256, List<String> entries) {
        return new JarArchiveIntegrityVerificationResult(
                false,
                entries.size() + " entries mismatched",
                expectedSha256, actualSha256,
                entries == null ? List.of() : List.copyOf(entries));
    }
}
