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
 *  %raskolclasses_class%         — отображаемое имя класса
 *  %raskolclasses_class_id%      — WARRIOR/HUNTER/PRIEST/MAGE/ROGUE
 *  %raskolclasses_level%         — уровень персонажа (топ-N скиллов, кап level-cap)
 *  %raskolclasses_char_level%    — алиас level
 *  %raskolclasses_skill_level%   — профильный скилл класса (AuraSkills)
 *  %raskolclasses_resource%      — текущий ресурс класса (0–100)
 *  %raskolclasses_resource_max%  — 100
 *  %raskolclasses_hp% / %raskolclasses_hp_max%
 *  %raskolclasses_phys_resist% / %raskolclasses_magic_resist%
 *  %raskolclasses_dodge% / %raskolclasses_parry%
 *  %raskolclasses_crit_melee% / %raskolclasses_crit_spell%
 *  %raskolclasses_spec% / %raskolclasses_spec_id%
 *  %raskolclasses_talent_points% — доступные очки талантов (1.9.0; 0 без спеки)
 *  %raskolclasses_talents%       — «потрачено/заработано» (1.9.0; «0/0» без спеки)
 *
 * Оффлайн-игрок → пустая строка; неизвестный ключ → null (плейсхолдер остаётся как есть).
 * 1.9.0-fix: onRequest — switch-EXPRESSION (return switch + yield), иначе
 * строковые тела кейсов не компилируются («not a statement»).
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

        return switch (key) {
            case "class" -> {
                PlayerClass pc = pcOf(p);
                yield pc == null ? "" : pc.getDisplayName();
            }
            case "class_id" -> {
                PlayerClass pc = pcOf(p);
                yield pc == null ? "" : pc.name();
            }
            case "level", "char_level" ->
                    String.valueOf(plugin.getCharacterLevels().characterLevel(uuid));
            case "skill_level" -> {
                PlayerClass pc = pcOf(p);
                if (pc == null) {
                    yield "";
                }
                int lv = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
                yield lv == SkillLevelProvider.NO_SKILL_SYSTEM ? "" : String.valueOf(lv);
            }
            case "resource" -> String.valueOf((int) plugin.getResources().getValue(uuid));
            case "resource_max" -> "100";
            case "hp" -> String.valueOf((int) p.getHealth());
            case "hp_max" -> String.valueOf((int) plugin.getAttributes().maxHp(uuid));
            case "phys_resist" -> String.valueOf((int) plugin.getResists().physicalResist(uuid));
            case "magic_resist" -> String.valueOf((int) plugin.getResists().magicResist(uuid));
            case "dodge" -> fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[0]);
            case "parry" -> fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[1]);
            case "crit_melee" -> fmt1(plugin.getAttributes().critMeleeChance(uuid));
            case "crit_spell" -> fmt1(plugin.getAttributes().critSpellChance(uuid));
            case "spec" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                yield s == null ? "" : s.displayName();
            }
            case "spec_id" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                yield s == null ? "" : s.id();
            }
            case "talent_points" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                yield s == null ? "0"
                        : String.valueOf(plugin.getTalentService().availablePoints(uuid, s.id()));
            }
            case "talents" -> {
                Spec s = plugin.getSpecService().getSpec(uuid);
                if (s == null) {
                    yield "0/0";
                }
                int spent = plugin.getTalentService().spentPoints(uuid, s.id());
                int earned = plugin.getTalentService().earnedPoints(uuid);
                yield spent + "/" + earned;
            }
            default -> null;
        };
    }

    private PlayerClass pcOf(Player p) {
        return plugin.getClassProvider().getClassOf(p);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
