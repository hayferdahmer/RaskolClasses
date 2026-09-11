// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI-хук RaskolClasses (1.6.5 + 1.8.1).
 * 1.8.1 (S5): добавлен %raskolclasses_level% — СВОНДНЫЙ уровень персонажа
 * (топ-N скиллов, кап level-cap): прогрессия видна в TAB/табах/скорбордах.
 *
 * Полный список плейсхолдеров:
 *  %raskolclasses_class%        — отображаемое имя класса;
 *  %raskolclasses_class_id%     — WARRIOR/HUNTER/PRIEST/MAGE/ROGUE;
 *  %raskolclasses_level%        — сводный уровень персонажа (1.8.0/1.8.1);
 *  %raskolclasses_char_level%   — алиас level;
 *  %raskolclasses_skill_level%  — профильный скилл класса (AuraSkills);
 *  %raskolclasses_resource%     — текущий ресурс класса (0–100);
 *  %raskolclasses_resource_max% — 100;
 *  %raskolclasses_hp% / %raskolclasses_hp_max% — текущее/макс HP (онлайн);
 *  %raskolclasses_phys_resist% / %raskolclasses_magic_resist% — резисты (1.6.5);
 *  %raskolclasses_resist_mods%  — число активных модификаторов резиста (1.6.5);
 *  %raskolclasses_dodge% / %raskolclasses_parry% — эффективные avoidance-шансы.
 * Оффлайн-игрок: uuid-зависимые (level/skill_level) работают, остальные — пустая строка.
 */
public final class RaskolPlaceholder extends PlaceholderExpansion {

    private final RaskolClasses plugin;

    public RaskolPlaceholder(RaskolClasses plugin) {
        this.plugin = plugin;
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
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        UUID uuid = player.getUniqueId();
        String p = params == null ? "" : params.toLowerCase(Locale.ROOT);
        Player online = player.getPlayer();

        switch (p) {
            case "level":
            case "char_level":
                return String.valueOf(plugin.getCharacterLevels().characterLevel(uuid));

            case "class":
            case "class_id": {
                if (online == null) {
                    return "";
                }
                PlayerClass pc = plugin.getClassProvider().getClassOf(online);
                if (pc == null) {
                    return "";
                }
                return p.equals("class") ? pc.getDisplayName() : pc.name();
            }

            case "skill_level": {
                if (online == null) {
                    return "";
                }
                PlayerClass pc = plugin.getClassProvider().getClassOf(online);
                if (pc == null) {
                    return "";
                }
                int lv = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
                return lv == SkillLevelProvider.NO_SKILL_SYSTEM ? "0" : String.valueOf(lv);
            }

            case "resource":
                return online == null ? "" : String.valueOf((int) plugin.getResources().getValue(uuid));

            case "resource_max":
                return "100";

            case "hp":
                return online == null ? "" : String.valueOf((int) online.getHealth());

            case "hp_max":
                return online == null ? "" : String.valueOf((int) plugin.getAttributes().maxHp(uuid));

            case "phys_resist":
                return online == null ? "" : String.valueOf((int) plugin.getResists().physicalResist(uuid));

            case "magic_resist":
                return online == null ? "" : String.valueOf((int) plugin.getResists().magicResist(uuid));

            case "resist_mods":
                return online == null ? "" : String.valueOf(plugin.getResists().breakdown(uuid).active().size());

            case "dodge":
                return online == null ? "" : fmt1(plugin.getAttributes().dodgeChance(uuid));

            case "parry":
                return online == null ? "" : fmt1(plugin.getAttributes().parryChance(uuid));

            default:
                return null;
        }
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
