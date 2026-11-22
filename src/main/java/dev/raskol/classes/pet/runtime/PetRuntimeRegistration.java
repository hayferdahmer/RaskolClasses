// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet.runtime;

/** Граница регистрации и снятия listener, принадлежащего runtime петов. */
public interface PetRuntimeRegistration {

    /** Регистрирует listener петов. */
    void register();

    /** Снимает listener петов; повторный вызов должен быть безопасен. */
    void unregister();
}
