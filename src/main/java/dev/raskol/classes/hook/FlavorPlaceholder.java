// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.raskol.classes.RaskolClasses;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * Расширение PlaceholderAPI для королевских вкусов.
 *
 * <p>Асинхронный callback читает только неизменяемый снимок игрока.</p>
 */
public final class FlavorPlaceholder extends PlaceholderExpansion {

    private final RaskolClasses plugin;
    private final PlaceholderSnapshotService snapshots;

    public FlavorPlaceholder(RaskolClasses plugin, PlaceholderSnapshotService snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "raskolcrown";
    }

    @Override
    public @NotNull String getAuthor() {
        return "hayferdahmer";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String normalized = params.toLowerCase(Locale.ROOT);
        String value = snapshots.crownValue(player.getUniqueId(), normalized);
        if (value != null) {
            return value;
        }

        return switch (normalized) {
            case "crown", "title", "faction" -> "";
            default -> null;
        };
    }
}
