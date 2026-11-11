// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import java.util.Locale;

import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI: %raskolclasses_*%.
 *
 * <p>Callback не вычисляет значения и не обращается к Bukkit-сервисам:
 * он только получает UUID игрока и читает опубликованный снимок.</p>
 */
public final class RaskolPlaceholder extends PlaceholderExpansion {

    private final RaskolClasses plugin;
    private final PlaceholderSnapshotService snapshots;

    public RaskolPlaceholder(RaskolClasses plugin, PlaceholderSnapshotService snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
    }

    @Override
    public String getIdentifier() {
        return "raskolclasses";
    }

    @Override
    public String getAuthor() {
        return "hayferdahmer";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return null;
        }

        String normalized = params.toLowerCase(Locale.ROOT);
        String value = snapshots.raskolValue(player.getUniqueId(), normalized);
        if (value != null) {
            return value;
        }

        return switch (normalized) {
            case "class", "level", "hp", "hp_max", "resource", "phys_resist", "magic_resist",
                    "dodge", "parry", "crit_melee", "crit_spell", "spec", "talent_points",
                    "talents", "plus" -> "";
            default -> null;
        };
    }
}
