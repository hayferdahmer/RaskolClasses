// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.resource.ResourceState;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Расширение PlaceholderAPI (A2): %raskolclasses_class%,
 * %raskolclasses_resource%, %raskolclasses_resource_max%.
 * Регистрируется только при установленном PlaceholderAPI (проверка в onEnable);
 * persist() — переживает /papi reload.
 */
public final class RaskolPlaceholder extends PlaceholderExpansion {

    private final RaskolClasses plugin;

    public RaskolPlaceholder(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "raskolclasses";
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
        return switch (params) {
            case "class" -> {
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                yield pc == null ? "" : pc.getDisplayName();
            }
            case "resource" -> String.valueOf((int) plugin.getResources()
                    .getValue(player.getUniqueId()));
            case "resource_max" -> String.valueOf((int) ResourceState.MAX_VALUE);
            default -> null; // неизвестный плейсхолдер — PAPI оставит как есть
        };
    }
}
