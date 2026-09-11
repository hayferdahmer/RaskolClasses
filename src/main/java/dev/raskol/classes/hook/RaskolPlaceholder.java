// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.spec.Spec;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI-хук: %raskolclasses_*% (1.6.5 + 1.8.0 + 1.9.0).
 *
 * Полный список плейсхолдеров:
 *  %raskolclasses_class%        — отображаемое имя класса
 *  %raskolclasses_class_id%     — WARRIOR/HUNTER/PRIEST/MAGE/ROGUE
 *  %raskolclasses_level%        — уровень персонажа (топ-N скиллов, кап level-cap)
 *  %raskolclasses_char_level%   — алиас level
 *  %raskolclasses_skill_level%  — профильный скилл класса (AuraSkills)
 *  %raskolclasses_resource%     — текущий ресурс класса (0–100)
 *  %raskolclasses_resource_max% — 100
 *  %raskolclasses_hp%           — текущее HP (целое)
 *  %raskolclasses_hp_max%       — макс. HP по формуле (целое)
 *  %raskolclasses_phys_resist%  — физрезист, % (целое)
 *  %raskolclasses_magic_resist% — магрезист, % (целое)
 *  %raskolclasses_dodge%        — эффективное уклонение, % (1 знак)
 *  %raskolclasses_parry%        — эффективное парирование, % (1 знак)
 *  %raskolclasses_crit_melee%   — крит мили, % (1 знак)
 *  %raskolclasses_crit_spell%   — крит магии, % (1 знак)
 *  %raskolclasses_spec%         — отображаемое имя спеки (пусто если нет)
 *  %raskolclasses_spec_id%      — id спеки (guardian/berserker/…, пусто если нет)
 *  %raskolclasses_talent_points%— доступные очки талантов (1.9.0; 0 без спеки)
 *  %raskolclasses_talents%      — «потрачено/заработано» (1.9.0; «0/0» без спеки)
 *
 * Оффлайн-игрок или неизвестный ключ → пустая строка / null (плейсхолдер остаётся как есть).
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
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        Player p = offline != null ? offline.getPlayer() : null;
        if (p == null) {
            return "";
        }
        UUID uuid = p.getUniqueId();
        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "class" -> {
                PlayerClass pc = pcOf(p);
                return pc == null ? "" : pc.getDisplayName();
            }
            case "class_id" -> {
                PlayerClass pc = pcOf(p);
                return pc == null ? "" : pc.name();
            }
            case "level", "char_level" ->
                    String.valueOf(plugin.getCharacterLevels().characterLevel(uuid));
            case "skill_level" -> {
                PlayerClass pc = pcOf(p);
                if (pc == null) {
                    return "";
                }
                int lv = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
                return lv == SkillLevelProvider.NO_SKILL_SYSTEM ? "" : String.valueOf(lv);
            }
            case "resource" ->
                    String.valueOf((int) plugin.getResources().getValue(uuid));
            case "resource_max" -> "100";
            case "hp" -> String.valueOf((int) p.getHealth());
            case "hp_max" -> String.valueOf((int) plugin.getAttributes().maxHp(uuid));
            case "phys_resist" ->
                    String.valueOf((int) plugin.getResists().physicalResist(uuid));
            case "magic_resist" ->
                    String.valueOf((int) plugin.getResists().magicResist(uuid));
            case "dodge" -> fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[0]);
            case "parry" -> fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[1]);
            case "crit_melee" -> fmt1(plugin.getAttributes().critMeleeChance(uuid));
            case "crit_spell" -> fmt1(plugin.getAttributes().critSpellChance(uuid));
            case "spec" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                return s == null ? "" : s.displayName();
            }
            case "spec_id" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                return s == null ? "" : s.id();
            }
            // 1.9.0: таланты (дисплей для TAB/скорборда; управление — только в Книге)
            case "talent_points" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                if (s == null) {
                    return "0";
                }
                return String.valueOf(plugin.getTalentService().availablePoints(uuid, s.id()));
            }
            case "talents" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                if (s == null) {
                    return "0/0";
                }
                int spent = plugin.getTalentService().spentPoints(uuid, s.id());
                int earned = plugin.getTalentService().earnedPoints(uuid);
                return spent + "/" + earned;
            }
            default -> {
                return null; // неизвестный ключ — PAPI оставит плейсхолдер как есть
            }
        }
    }

    private PlayerClass pcOf(Player p) {
        return plugin.getClassProvider().getClassOf(p);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
