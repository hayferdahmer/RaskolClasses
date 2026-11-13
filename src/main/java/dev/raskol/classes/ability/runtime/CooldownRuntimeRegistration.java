// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.ability.runtime;

/** Граница регистрации и снятия listener кулдаунов. */
public interface CooldownRuntimeRegistration {

    /** Регистрирует listener кулдаунов. */
    void register();

    /** Снимает listener кулдаунов; повторный вызов должен быть безопасен. */
    void unregister();
}
