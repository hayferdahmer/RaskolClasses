// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Неизменяемый технический контекст одного модуля.
 *
 * <p>Контекст намеренно не предоставляет глобальный доступ к игровым сервисам.
 * Межмодульные зависимости передаются через конструкторы и узкие контракты.
 * Для логирования используется узкий фасад, не раскрывающий JUL Logger.</p>
 */
public final class ModuleContext {

    private final String pluginId;
    private final ModuleLogger logger;
    private final Path dataDirectory;
    private final TaskScope taskScope;

    public ModuleContext(String pluginId, String loggerName, Path dataDirectory, TaskScope taskScope) {
        this.pluginId = requireText(pluginId, "pluginId");
        this.logger = new ModuleLogger(requireText(loggerName, "loggerName"));
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.taskScope = Objects.requireNonNull(taskScope, "taskScope");
    }

    public String pluginId() {
        return pluginId;
    }

    public ModuleLogger logger() {
        return logger;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public TaskScope taskScope() {
        return taskScope;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " не может быть пустым");
        }
        return value;
    }
}
