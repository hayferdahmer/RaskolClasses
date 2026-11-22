// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.flavor.runtime;

/** Граница регистрации listener сервиса королевских вкусов. */
public interface FlavorRuntimeRegistration {

    /** Регистрирует listener CrownFlavorService. */
    void register();

    /** Снимает регистрацию listener; повторный вызов безопасен. */
    void unregister();
}
