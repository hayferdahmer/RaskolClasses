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
 * 1.14.14 (фаза 2, HE-фикс → откат): переопределение equals()/hashCode() НЕВОЗМОЖНО —
 * в PlaceholderAPI 2.11.6 equals(Object) объявлен final в PlaceholderExpansion
 * (compile error "overridden method is final"). Находка
 * HE_INHERITS_EQUALS_USE_HASHCODE — ограничение апстрим-библиотеки (final equals
 * без hashCode), а не дефект плагина: реестр расширений PAPI ведётся по строковому
 * identifier, объект-ключом нигде не используется. Закрыта точечным exclude в
 * spotbugs-exclude.xml с этим обоснованием.
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
