// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook.runtime;

/** Граница регистрации и снятия PlaceholderAPI expansions. */
public interface PlaceholderRuntimeRegistration {

    /** Регистрирует все принадлежащие плагину expansions. */
    void register();

    /** Снимает все expansions; повторный вызов должен быть безопасен. */
    void unregister();
}
