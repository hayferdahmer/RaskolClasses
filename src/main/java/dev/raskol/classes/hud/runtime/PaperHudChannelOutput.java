// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import dev.raskol.classes.hud.channel.HudChannelOutput;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Paper adapter для ActionBar и BossBar, созданных исключительно RaskolHUD. */
public final class PaperHudChannelOutput implements HudChannelOutput {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Map<UUID, Map<String, BossBar>> bossBars = new HashMap<>();
    private final Set<UUID> actionBarPlayers = new HashSet<>();

    @Override
    public void sendActionBar(UUID playerId, String payload) {
        ensurePrimaryThread();
        Player player = requireOnlinePlayer(playerId);
        player.sendActionBar(LEGACY.deserialize(payload));
        actionBarPlayers.add(playerId);
    }

    @Override
    public void clearActionBar(UUID playerId) {
        ensurePrimaryThread();
        if (!actionBarPlayers.contains(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            actionBarPlayers.remove(playerId);
            return;
        }
        player.sendActionBar(Component.empty());
        actionBarPlayers.remove(playerId);
    }

    @Override
    public void upsertBossBar(UUID playerId, String widgetId, String payload) {
        ensurePrimaryThread();
        Player player = requireOnlinePlayer(playerId);
        Map<String, BossBar> playerBars = bossBars.computeIfAbsent(playerId, ignored -> new HashMap<>());
        BossBar bar = playerBars.get(widgetId);
        if (bar == null) {
            bar = Bukkit.createBossBar(payload, BarColor.WHITE, BarStyle.SOLID);
            try {
                bar.addPlayer(player);
            } catch (RuntimeException failure) {
                bar.removeAll();
                if (playerBars.isEmpty()) {
                    bossBars.remove(playerId);
                }
                throw failure;
            }
            playerBars.put(widgetId, bar);
            return;
        }
        bar.setTitle(payload);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    @Override
    public void removeBossBar(UUID playerId, String widgetId) {
        ensurePrimaryThread();
        Map<String, BossBar> playerBars = bossBars.get(playerId);
        if (playerBars == null) {
            return;
        }
        BossBar bar = playerBars.get(widgetId);
        if (bar == null) {
            return;
        }
        bar.removeAll();
        playerBars.remove(widgetId);
        if (playerBars.isEmpty()) {
            bossBars.remove(playerId);
        }
    }

    @Override
    public void clearPlayer(UUID playerId) {
        ensurePrimaryThread();
        RuntimeException failure = null;
        try {
            clearActionBar(playerId);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        Map<String, BossBar> playerBars = bossBars.get(playerId);
        if (playerBars != null) {
            for (String widgetId : List.copyOf(playerBars.keySet())) {
                try {
                    removeBossBar(playerId, widgetId);
                } catch (RuntimeException exception) {
                    failure = merge(failure, exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void clearAll() {
        ensurePrimaryThread();
        Set<UUID> playerIds = new HashSet<>(actionBarPlayers);
        playerIds.addAll(bossBars.keySet());
        RuntimeException failure = null;
        for (UUID playerId : playerIds) {
            try {
                clearPlayer(playerId);
            } catch (RuntimeException exception) {
                failure = merge(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static Player requireOnlinePlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("HUD output недоступен: игрок не в сети " + playerId);
        }
        return player;
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper HUD output должен выполняться на основном потоке");
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
