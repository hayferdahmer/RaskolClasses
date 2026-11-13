// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.Objects;

/** Потокобезопасная проверка допустимых переходов lifecycle одного модуля. */
/**
 * Причина отказа сохраняется и возвращается как исходный Throwable для
 * диагностики; вызывающий код не получает право менять lifecycle-состояние.
 */
@SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class ModuleLifecycle {

    private ModuleState state = ModuleState.NEW;
    private Throwable failure;

    public synchronized ModuleState state() {
        return state;
    }

    public synchronized Throwable failure() {
        return failure;
    }

    public synchronized void initialized() {
        transition(ModuleState.NEW, ModuleState.INITIALIZED);
    }

    public synchronized void registered() {
        transition(ModuleState.INITIALIZED, ModuleState.REGISTERED);
    }

    public synchronized void started() {
        transition(ModuleState.REGISTERED, ModuleState.RUNNING);
    }

    /**
     * Переводит частично или полностью подготовленный модуль в остановку.
     * Повторный вызов при STOPPING/STOPPED/CLOSED не изменяет состояние.
     */
    public synchronized void beginStopping() {
        if (state == ModuleState.STOPPING
                || state == ModuleState.STOPPED
                || state == ModuleState.CLOSED) {
            return;
        }
        state = ModuleState.STOPPING;
    }

    public synchronized void stopped() {
        transition(ModuleState.STOPPING, ModuleState.STOPPED);
    }

    /** Фиксирует первую причину отказа модуля. */
    public synchronized void failed(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        if (state == ModuleState.CLOSED) {
            throw new IllegalStateException("Закрытый модуль не может перейти в FAILED");
        }
        if (failure == null) {
            failure = cause;
        }
        state = ModuleState.FAILED;
    }

    /**
     * Закрывает модуль после штатной остановки или обработки ошибки.
     * NEW разрешён для модулей, которые не успели начать инициализацию.
     */
    public synchronized void closed() {
        if (state != ModuleState.NEW
                && state != ModuleState.STOPPED
                && state != ModuleState.FAILED) {
            throw new IllegalStateException("Нельзя закрыть модуль из состояния " + state);
        }
        state = ModuleState.CLOSED;
    }

    private void transition(ModuleState expected, ModuleState target) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Недопустимый переход lifecycle: " + state + " -> " + target + ", ожидалось " + expected);
        }
        state = target;
    }
}
