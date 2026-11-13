// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

/**
 * Handle регистрации listener или внутренней event subscription.
 *
 * <p>Закрытие должно быть безопасно при повторном вызове.</p>
 */
@FunctionalInterface
public interface HudRegistration extends AutoCloseable {

    @Override
    void close();
}
