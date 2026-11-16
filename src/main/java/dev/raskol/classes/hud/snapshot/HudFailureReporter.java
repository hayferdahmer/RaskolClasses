// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.UUID;

/** Узкий диагностический порт для изоляции ошибок snapshot/widget/publisher. */
@FunctionalInterface
public interface HudFailureReporter {
    void report(UUID playerId, String stage, RuntimeException failure);
}
