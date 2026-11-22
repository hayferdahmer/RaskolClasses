// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

/** Отменяемая задача, зарегистрированная в области владения модуля. */
public interface TaskHandle {

    /** Отменяет задачу; реализация должна поддерживать повторный вызов. */
    void cancel();

    /** Возвращает true, если задача уже отменена или завершена. */
    boolean isCancelled();
}
