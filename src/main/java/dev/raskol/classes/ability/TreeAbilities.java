// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.School;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1.14.0 (контент-долги): способности, открываемые узлами деревьев путей
 * (type=unlock_ability/ultimate). Def'ы читаются из конфига
 * classes.<CLASS>.treeAbilities.<id>.* (name/description/cost/cooldown/power/
 * base/coeff/school/radius); кастеры регистрируются в AbilityRegistry отдельно
 * от базовых слотов 1–5, поэтому selftest-чеки китов (44/64/65) не меняются.
 *
 * Гейт доступа — внутри методов кита: Spec2Service.hasUnlocked(uuid, id)
 * (ранг ≥1 у узла с effect unlock_ability target=<id>).
 * Слоты в Книге: 6+ (ряд TREE_ABILITY_SLOTS), свитки работают как у базовых.
 */
public final class TreeAbilities {

    private TreeAbilities() {
    }

    /** Id древесных способностей класса (порядок = порядок слотов витрины). */
    public static List<String> idsFor(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> List.of(
                    "whirlwind_slash", "mortal_strike", "bloodthirst", "rampage",
                    "concussive_blow", "shield_bash", "taunt", "bladestorm", "last_stand");
            default -> List.of(); // остальные классы — следующие контент-батчи
        };
    }

    /** Def древесной способности из конфига; null если секции нет. */
    public static AbilityDef defFor(RaskolClasses plugin, PlayerClass pc, String id, int slot) {
        String base = "classes." + pc.name() + ".treeAbilities." + id + ".";
        if (!plugin.getConfig().isConfigurationSection(
                "classes." + pc.name() + ".treeAbilities." + id)) {
            return null;
        }
        String name = plugin.getConfig().getString(base + "name", id);
        int cost = plugin.getConfig().getInt(base + "cost", 20);
        int cd = plugin.getConfig().getInt(base + "cooldown", 20);
        int unlock = plugin.getConfig().getInt(base + "unlock", 15);
        return new AbilityDef(id, name, slot, unlock, cost, cd * 1000L, readSchool(plugin, base));
    }

    /** Все def'ы класса для витрины (только присутствующие в конфиге). */
    public static List<AbilityDef> defsFor(RaskolClasses plugin, PlayerClass pc) {
        List<AbilityDef> out = new ArrayList<>();
        List<String> ids = idsFor(pc);
        for (int i = 0; i < ids.size(); i++) {
            AbilityDef def = defFor(plugin, pc, ids.get(i), 6 + i);
            if (def != null) {
                out.add(def);
            }
        }
        return out;
    }

    public static AbilityDef defById(RaskolClasses plugin, PlayerClass pc, String id) {
        List<String> ids = idsFor(pc);
        int idx = ids.indexOf(id);
        return idx < 0 ? null : defFor(plugin, pc, id, 6 + idx);
    }

    private static School readSchool(RaskolClasses plugin, String base) {
        School s = School.fromId(plugin.getConfig().getString(base + "school", ""));
        return s != null ? s : School.PHYSICAL;
    }

    /** Регистрация кастеров древесных способностей Воина (1.14.0, контент-долг 1). */
    public static void registerCasters(RaskolClasses plugin, AbilityRegistry registry) {
        WarriorAbilities warrior = new WarriorAbilities(plugin);
        registry.registerTreeCaster("whirlwind_slash", warrior::whirlwindSlash);
        registry.registerTreeCaster("mortal_strike", warrior::mortalStrike);
        registry.registerTreeCaster("bloodthirst", warrior::bloodthirst);
        registry.registerTreeCaster("rampage", warrior::rampage);
        registry.registerTreeCaster("concussive_blow", warrior::concussiveBlow);
        registry.registerTreeCaster("shield_bash", warrior::shieldBash);
        registry.registerTreeCaster("taunt", warrior::taunt);
        registry.registerTreeCaster("bladestorm", warrior::bladestorm);
        registry.registerTreeCaster("last_stand", warrior::lastStand);
    }

    /** Человекочитаемое имя для сообщений гейта. */
    public static String displayName(RaskolClasses plugin, PlayerClass pc, String id) {
        AbilityDef def = defById(plugin, pc, id);
        return def != null ? def.displayName() : id;
    }

    /** Ключ конфига радиуса (дефолт 4.0). */
    public static double radiusOf(RaskolClasses plugin, PlayerClass pc, String id, double def) {
        double v = plugin.getConfig().getDouble(
                "classes." + pc.name() + ".treeAbilities." + id + ".radius", def);
        return Double.isFinite(v) && v > 0.0 ? v : def;
    }

    /** Ключ конфига длительности в секундах (дефолт def). */
    public static int durationOf(RaskolClasses plugin, PlayerClass pc, String id, int def) {
        int v = plugin.getConfig().getInt(
                "classes." + pc.name() + ".treeAbilities." + id + ".duration", def);
        return v > 0 ? v : def;
    }

    /** Утиль: имя класса в нижнем регистре для сообщений. */
    public static String pcName(PlayerClass pc) {
        return pc.name().toLowerCase(Locale.ROOT);
    }
}
