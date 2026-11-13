// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.attribute.runtime;

/** Порт регистрации listener синхронизации HP. */
public interface HpAttributeSyncRegistration {

    void register();

    void unregister();
}
