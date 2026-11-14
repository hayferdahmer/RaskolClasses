// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;
import java.util.UUID;

import dev.raskol.classes.hud.HudService;

/** Адаптер очистки per-player состояния legacy ActionBar HUD. */
public final class HudServiceOutputCleanup implements HudOutputCleanup {

    private final HudService hudService;

    public HudServiceOutputCleanup(HudService hudService) {
        this.hudService = Objects.requireNonNull(hudService, "hudService");
    }

    @Override
    public void clearPlayer(UUID playerId) {
        hudService.clearPlayer(playerId);
    }

    @Override
    public void clearAll() {
        hudService.clearAll();
    }
}
