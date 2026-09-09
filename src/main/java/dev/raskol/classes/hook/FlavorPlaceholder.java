// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.resource.ResourceState;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Расширение PlaceholderAPI (A2): %raskolclasses_class%,
 * %raskolclasses_resource%, %raskolclasses_resource_max%.
 * 1.7.0 пакет 1: атрибуты и производные:
 *   %raskolclasses_str% / _agi% / _int%        — итоговые значения атрибутов;
 *   %raskolclasses_hp% / _hp_max%              — текущее HP и максимум по формуле;
 *   %raskolclasses_dodge% / _parry%            — эффективные шансы после DR (целые %);
 *   %raskolclasses_crit_melee% / _crit_spell%  — шансы крита (целые %).
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
        UUID uuid = player.getUniqueId();
        switch (params) {
            case "class": {
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                return pc == null ? "" : pc.getDisplayName();
            }
            case "resource":
                return String.valueOf((int) plugin.getResources().getValue(uuid));
            case "resource_max":
                return String.valueOf((int) ResourceState.MAX_VALUE);
            case "str":
                return String.valueOf((int) plugin.getAttributes().value(uuid, AttributeType.STR));
            case "agi":
                return String.valueOf((int) plugin.getAttributes().value(uuid, AttributeType.AGI));
            case "int":
                return String.valueOf((int) plugin.getAttributes().value(uuid, AttributeType.INT));
            case "hp":
                return String.valueOf((int) player.getHealth());
            case "hp_max":
                return String.valueOf((int) plugin.getAttributes().maxHp(uuid));
            case "dodge":
                return String.valueOf(Math.round(
                        plugin.getAttributes().effectiveAvoidance(uuid)[0]));
            case "parry":
                return String.valueOf(Math.round(
                        plugin.getAttributes().effectiveAvoidance(uuid)[1]));
            case "crit_melee":
                return String.valueOf(Math.round(
                        plugin.getAttributes().critMeleeChance(uuid)));
            case "crit_spell":
                return String.valueOf(Math.round(
                        plugin.getAttributes().critSpellChance(uuid)));
            default:
                return null; // неизвестный плейсхолдер — PAPI оставит как есть
        }
    }
}
