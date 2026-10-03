// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI: %raskolclasses_*% (README, раздел VII).
 * 1.14.0 (Б8): spec/talent_points/talents читаются из spec2-слоя;
 * legacy SpecService/TalentService не используются.
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
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) {
            return null;
        }
        var uuid = player.getUniqueId();
        switch (params.toLowerCase(Locale.ROOT)) {
            case "class": {
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                return pc == null ? "" : pc.getDisplayName();
            }
            case "level":
                return String.valueOf(plugin.getCharacterLevels().characterLevel(uuid));
            case "hp":
                return String.valueOf((int) plugin.getHpBarService().currentFormulaHp(player));
            case "hp_max":
                return String.valueOf((int) plugin.getHpBarService().formulaMaxHp(uuid));
            case "resource":
                return String.valueOf((int) plugin.getResources().getValue(uuid));
            case "phys_resist":
                return String.valueOf((int) plugin.getResists().physicalResist(uuid));
            case "magic_resist":
                return String.valueOf((int) plugin.getResists().magicResist(uuid));
            case "dodge":
                return fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[0]);
            case "parry":
                return fmt1(plugin.getAttributes().effectiveAvoidance(uuid)[1]);
            case "crit_melee":
                return fmt1(plugin.getAttributes().critMeleeChance(uuid));
            case "crit_spell":
                return fmt1(plugin.getAttributes().critSpellChance(uuid));
            case "spec": {
                String main = plugin.getSpec2Service().mainSpec(uuid);
                Spec spec = Spec.fromId(main);
                return spec == null ? "" : spec.displayName();
            }
            case "talent_points":
                return String.valueOf(plugin.getSpec2Service().availablePoints(uuid));
            case "talents":
                return plugin.getSpec2Service().spentGlobal(uuid) + "/"
                        + plugin.getSpec2Service().earnedPoints(uuid);
            default:
                return null;
        }
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
