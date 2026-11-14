// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

/** Порт регистрации lifecycle-событий HpBarService. */
public interface HpBarRuntimeRegistration {
    void register();

    void unregister();
}
