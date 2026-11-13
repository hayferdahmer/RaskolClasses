// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

/** Состояния жизненного цикла отдельного модуля плагина. */
public enum ModuleState {
    NEW,
    INITIALIZED,
    REGISTERED,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED,
    CLOSED
}
