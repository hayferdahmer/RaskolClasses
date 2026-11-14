// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Изолирует независимые этапы best-effort выключения плагина. */
public final class ShutdownStepRunner {

    private final Logger logger;
    private final List<String> failedSteps = new ArrayList<>();

    public ShutdownStepRunner(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Выполняет этап выключения, не позволяя RuntimeException прервать последующие этапы.
     *
     * @param name диагностическое имя этапа
     * @param action действие очистки
     */
    public void run(String name, Runnable action) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");

        try {
            action.run();
        } catch (RuntimeException exception) {
            failedSteps.add(name);
            logger.log(Level.SEVERE, "Не удалось выполнить этап выключения: " + name, exception);
        }
    }

    /** Возвращает число этапов, завершившихся с ошибкой. */
    public int failureCount() {
        return failedSteps.size();
    }

    /** Пишет сводку после попытки выполнить все этапы выключения. */
    public void logSummary() {
        if (failedSteps.isEmpty()) {
            logger.info("Выключение плагина завершено: все этапы очистки выполнены");
            return;
        }

        logger.severe(
                "Выключение плагина завершено с ошибками: "
                        + failedSteps.size()
                        + "; этапы: "
                        + String.join(", ", failedSteps));
    }
}
