// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Узкий фасад журналирования для lifecycle-модулей.
 *
 * <p>Фасад хранит только имя логгера и самостоятельно разрешает JUL Logger,
 * поэтому наружу не передаётся изменяемый объект Logger.</p>
 */
public final class ModuleLogger {

    private final String loggerName;

    public ModuleLogger(String loggerName) {
        Objects.requireNonNull(loggerName, "loggerName");
        if (loggerName.isBlank()) {
            throw new IllegalArgumentException("loggerName не может быть пустым");
        }
        this.loggerName = loggerName;
    }

    public void log(Level level, String message) {
        logger().log(Objects.requireNonNull(level, "level"), message);
    }

    public void log(Level level, String message, Throwable cause) {
        logger().log(Objects.requireNonNull(level, "level"), message, cause);
    }

    public void info(String message) {
        logger().info(message);
    }

    public void warning(String message) {
        logger().warning(message);
    }

    public void severe(String message) {
        logger().severe(message);
    }

    private Logger logger() {
        return Logger.getLogger(loggerName);
    }
}
