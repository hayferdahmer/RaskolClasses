// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Плейсхолдеры короны (1.4.0, Пакет 4), отдельный идентификатор raskolcrown:
 *  %raskolcrown_crown%   — «Рассвет» / «Вальрадис» / «Вне короны»
 *  %raskolcrown_title%   — титул класса под короной («Рассветный клинок»)
 *  %raskolcrown_faction% — сырой id фракции (rassvet/valradis/"")
 * Для TAB/чата: добавь %raskolcrown_title% в формат TAB — титул станет виден всем.
 */
public final class FlavorPlaceholder extends PlaceholderExpansion {

    private final RaskolClasses plugin;

    public FlavorPlaceholder(RaskolClasses plugin) {
        this.plugin = plugin;
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
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!(player instanceof Player online) || !online.isOnline()) {
            return "";
        }
        switch (params.toLowerCase(Locale.ROOT)) {
            case "crown":
                return plugin.getFlavorService().crownDisplayName(online.getUniqueId());
            case "title": {
                PlayerClass pc = plugin.getClassProvider().getClassOf(online);
                return plugin.getFlavorService().titleOf(online.getUniqueId(), pc);
            }
            case "faction":
                return plugin.getFlavorService().factionOf(online.getUniqueId());
            default:
                return null;
        }
    }
}
