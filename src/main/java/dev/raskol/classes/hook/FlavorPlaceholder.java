// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Расширение PlaceholderAPI для королевских вкусов (1.4.0):
 *   %raskolcrown_crown%   — имя короны (Рассвет / Вальрадис);
 *   %raskolcrown_title%   — титул класса по короне (пусто, если класса нет);
 *   %raskolcrown_faction% — фракция игрока (хук корон/Towny, пусто если нет).
 * Регистрируется только при установленном PlaceholderAPI (проверка в onEnable);
 * persist() — переживает /papi reload.
 *
 * 1.14.14 (фаза 2, HE-фикс): явные equals()/hashCode() на базе identifier —
 * PlaceholderExpansion наследует equals() от родителя, но не hashCode(); SpotBugs
 * HE_INHERITS_EQUALS_USE_HASHCODE. Согласование по identifier — канонический
 * контракт для расширений PlaceholderAPI (два расширения с одним id логически
 * тождественны, поэтому equals и hashCode должны совпадать).
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

    /**
     * 1.14.14 (фаза 2, HE-фикс): equals на основе identifier — два расширения
     * PlaceholderAPI с одним id логически тождественны.
     */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaceholderExpansion that)) return false;
        return getIdentifier().equals(that.getIdentifier());
    }

    /** 1.14.14 (фаза 2, HE-фикс): hashCode согласован с equals по identifier. */
    @Override
    public int hashCode() {
        return getIdentifier().hashCode();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        switch (params) {
            case "crown":
                return plugin.getFlavorService().crownDisplayName(player.getUniqueId());
            case "title": {
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                return pc == null
                        ? ""
                        : plugin.getFlavorService().titleOf(player.getUniqueId(), pc);
            }
            case "faction":
                return plugin.getFactionHook().factionOf(player.getUniqueId());
            default:
                return null; // неизвестный плейсхолдер — PAPI оставит как есть
        }
    }
}
