// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.resource.ResourceState;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * Расширение PlaceholderAPI (A2):
 *   %raskolclasses_class%         — отображаемое имя класса (или пусто)
 *   %raskolclasses_resource%      — текущее значение ресурса (целое)
 *   %raskolclasses_resource_max%  — максимум ресурса
 *   %raskolclasses_phys_resist%   — итоговый физрезист, целое % (0 без класса)
 *   %raskolclasses_magic_resist%  — итоговый магрезист, целое %
 *   %raskolclasses_resist_mods%   — список активных модификаторов через запятую
 *                                   (например, «steel_skin, guardian»), или «нет»
 * Регистрируется только при установленном PlaceholderAPI (проверка в onEnable);
 * persist() — переживает /papi reload.
 *
 * 1.6.5: три новых плейсхолдера резистов и модификаторов. Существующие три
 * не тронуты, чтобы не ломать прошитые в TAB/скорборд/холотемы шаблоны.
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
            case "phys_resist" -> String.valueOf(
                    (int) Math.round(plugin.getResists().physicalResist(player.getUniqueId())));
            case "magic_resist" -> String.valueOf(
                    (int) Math.round(plugin.getResists().magicResist(player.getUniqueId())));
            case "resist_mods" -> formatModifiers(plugin.getResists()
                    .breakdown(player.getUniqueId()));
            default -> null; // неизвестный плейсхолдер — PAPI оставит как есть
        };
    }

    /**
     * Список источников активных модификаторов через запятую
     * (например, «steel_skin, guardian»); пусто → «нет».
     */
    private static String formatModifiers(ResistService.Breakdown breakdown) {
        if (breakdown.active().isEmpty()) {
            return "нет";
        }
        return breakdown.active().stream()
                .map(ResistService.Modifier::source)
                .collect(Collectors.joining(", "));
    }
}
