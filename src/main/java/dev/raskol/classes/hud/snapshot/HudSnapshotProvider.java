// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.snapshot;

import java.util.UUID;

/** Синхронно собирает read-only snapshot; Paper-адаптер обязан вызывать его на main thread. */
@FunctionalInterface
public interface HudSnapshotProvider {
    PlayerHudSnapshot capture(UUID playerId, long revision);
}
