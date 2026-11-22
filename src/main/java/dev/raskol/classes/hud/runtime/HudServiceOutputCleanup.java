// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import dev.raskol.classes.hud.HudService;
import dev.raskol.classes.hud.application.HudCoordinator;
import dev.raskol.classes.hud.channel.HudChannelCoordinator;

/** Адаптер очистки per-player состояния legacy ActionBar HUD. */
public final class HudServiceOutputCleanup implements HudOutputCleanup {

    private final HudService hudService;
    private final HudChannelCoordinator channelCoordinator;
    private final Supplier<HudCoordinator> snapshotCoordinator;

    public HudServiceOutputCleanup(HudService hudService) {
        this(hudService, null, () -> null);
    }

    public HudServiceOutputCleanup(HudService hudService, HudChannelCoordinator channelCoordinator) {
        this(hudService, channelCoordinator, () -> null);
    }

    public HudServiceOutputCleanup(
            HudService hudService,
            HudChannelCoordinator channelCoordinator,
            Supplier<HudCoordinator> snapshotCoordinator) {
        this.hudService = Objects.requireNonNull(hudService, "hudService");
        this.channelCoordinator = channelCoordinator;
        this.snapshotCoordinator = Objects.requireNonNull(snapshotCoordinator, "snapshotCoordinator");
    }

    @Override
    public void clearPlayer(UUID playerId) {
        RuntimeException failure = null;
        try {
            hudService.clearPlayer(playerId);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        HudCoordinator coordinator = snapshotCoordinator.get();
        if (coordinator != null) {
            try {
                coordinator.onPlayerQuit(playerId);
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        } else if (channelCoordinator != null) {
            try {
                channelCoordinator.clearPlayer(playerId);
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void clearAll() {
        RuntimeException failure = null;
        try {
            hudService.clearAll();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        if (channelCoordinator != null) {
            try {
                channelCoordinator.clearAll();
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException merge(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
