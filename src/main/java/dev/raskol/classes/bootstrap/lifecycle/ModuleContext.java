// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Неизменяемый технический контекст одного модуля.
 *
 * <p>Контекст намеренно не предоставляет глобальный доступ к игровым сервисам.
 * Межмодульные зависимости передаются через конструкторы и узкие контракты.</p>
 */
/**
 * Ссылки на Logger и TaskScope намеренно разделяются с владельцем модуля:
 * это сервисные handles, а не изменяемые данные контекста.
 */
@SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public record ModuleContext(
        String pluginId,
        Logger logger,
        Path dataDirectory,
        TaskScope taskScope) {

    public ModuleContext {
        pluginId = requireText(pluginId, "pluginId");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(taskScope, "taskScope");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " не может быть пустым");
        }
        return value;
    }
}
