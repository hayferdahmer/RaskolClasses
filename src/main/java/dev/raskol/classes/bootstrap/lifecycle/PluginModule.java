// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.Set;

/**
 * Контракт модуля плагина.
 *
 * <p>Методы вызываются координатором в порядке initialize → register → start
 * и в обратном порядке stop → close. Реализация должна освобождать только
 * ресурсы, которыми владеет данный модуль.</p>
 */
public interface PluginModule {

    /** Стабильный уникальный идентификатор модуля. */
    String id();

    /** Идентификаторы модулей, которые должны быть запущены раньше этого. */
    default Set<String> dependencies() {
        return Set.of();
    }

    /** Подготавливает зависимости и внутреннее состояние без запуска задач. */
    void initialize(ModuleContext context) throws Exception;

    /** Регистрирует listeners и внешние точки интеграции. */
    default void register() throws Exception {}

    /** Запускает периодические задачи и принимает рабочие операции. */
    void start() throws Exception;

    /** Прекращает рабочие операции и сохраняет принадлежащее модулю состояние. */
    void stop() throws Exception;

    /** Освобождает ресурсы после остановки. */
    void close() throws Exception;
}
