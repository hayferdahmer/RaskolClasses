// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.channel;

import java.util.UUID;

/** Узкий порт вывода HUD-owned каналов; реализация Paper работает только на main thread. */
public interface HudChannelOutput {

    void sendActionBar(UUID playerId, String payload);

    void clearActionBar(UUID playerId);

    void upsertBossBar(UUID playerId, String widgetId, String payload);

    void removeBossBar(UUID playerId, String widgetId);

    void clearPlayer(UUID playerId);

    void clearAll();
}
