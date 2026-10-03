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
 * classes.<CLASS>.treeAbilities.<id>.*; кастеры регистрируются в AbilityRegistry
 * отдельно от базовых слотов 1–5 (selftest-чеки китов не меняются).
 * Гейт доступа — внутри методов кита: Spec2Service.hasUnlocked(uuid, id).
 *
 * Долг 1: Воин (9). Долг 2: Охотник (14). Долг 3: Разбойник (16). Долг 4: Маг (11).
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
            case HUNTER -> List.of(
                    "aimed_shot", "silencing_shot", "chimera_shot", "true_shot",
                    "poison_shot", "explosive_trap", "black_arrow", "wyvern_sting",
                    "readiness", "serpent_sting", "intimidation", "pet_wolf",
                    "beast_ferocity", "bestial_wrath");
            case ROGUE -> List.of(
                    "poison_burst", "cold_blood", "vendetta", "envenom", "deathmark",
                    "pistol_shot", "blade_flurry", "adrenaline_rush", "killing_spree",
                    "between_the_eyes", "backstab", "shadowstep", "preparation",
                    "cloak_of_shadows", "hemorrhage", "shadow_blades");
            case MAGE -> List.of(
                    "arcane_missiles", "counterspell", "presence_of_mind",
                    "scorch", "flamestrike", "combustion", "pyroblast",
                    "frostbolt", "blizzard", "ice_barrier", "ice_lance_shatter");
            default -> List.of(); // Жрец/Чернокнижник — следующие долги
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

    /** Регистрация кастеров древесных способностей (Воин/Охотник/Разбойник/Маг). */
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

        HunterAbilities hunter = new HunterAbilities(plugin);
        registry.registerTreeCaster("aimed_shot", hunter::aimedShot);
        registry.registerTreeCaster("silencing_shot", hunter::silencingShot);
        registry.registerTreeCaster("chimera_shot", hunter::chimeraShot);
        registry.registerTreeCaster("true_shot", hunter::trueShot);
        registry.registerTreeCaster("poison_shot", hunter::poisonShot);
        registry.registerTreeCaster("explosive_trap", hunter::explosiveTrap);
        registry.registerTreeCaster("black_arrow", hunter::blackArrow);
        registry.registerTreeCaster("wyvern_sting", hunter::wyvernSting);
        registry.registerTreeCaster("readiness", hunter::readiness);
        registry.registerTreeCaster("serpent_sting", hunter::serpentSting);
        registry.registerTreeCaster("intimidation", hunter::intimidation);
        registry.registerTreeCaster("pet_wolf", hunter::petWolf);
        registry.registerTreeCaster("beast_ferocity", hunter::beastFerocity);
        registry.registerTreeCaster("bestial_wrath", hunter::bestialWrath);

        RogueAbilities rogue = new RogueAbilities(plugin);
        registry.registerTreeCaster("poison_burst", rogue::poisonBurst);
        registry.registerTreeCaster("cold_blood", rogue::coldBlood);
        registry.registerTreeCaster("vendetta", rogue::vendetta);
        registry.registerTreeCaster("envenom", rogue::envenom);
        registry.registerTreeCaster("deathmark", rogue::deathmark);
        registry.registerTreeCaster("pistol_shot", rogue::pistolShot);
        registry.registerTreeCaster("blade_flurry", rogue::bladeFlurry);
        registry.registerTreeCaster("adrenaline_rush", rogue::adrenalineRush);
        registry.registerTreeCaster("killing_spree", rogue::killingSpree);
        registry.registerTreeCaster("between_the_eyes", rogue::betweenTheEyes);
        registry.registerTreeCaster("backstab", rogue::backstab);
        registry.registerTreeCaster("shadowstep", rogue::shadowstep);
        registry.registerTreeCaster("preparation", rogue::preparation);
        registry.registerTreeCaster("cloak_of_shadows", rogue::cloakOfShadows);
        registry.registerTreeCaster("hemorrhage", rogue::hemorrhage);
        registry.registerTreeCaster("shadow_blades", rogue::shadowBlades);

        MageAbilities mage = new MageAbilities(plugin);
        registry.registerTreeCaster("arcane_missiles", mage::arcaneMissiles);
        registry.registerTreeCaster("counterspell", mage::counterspell);
        registry.registerTreeCaster("presence_of_mind", mage::presenceOfMind);
        registry.registerTreeCaster("scorch", mage::scorch);
        registry.registerTreeCaster("flamestrike", mage::flamestrike);
        registry.registerTreeCaster("combustion", mage::combustion);
        registry.registerTreeCaster("pyroblast", mage::pyroblast);
        registry.registerTreeCaster("frostbolt", mage::frostbolt);
        registry.registerTreeCaster("blizzard", mage::blizzard);
        registry.registerTreeCaster("ice_barrier", mage::iceBarrier);
        registry.registerTreeCaster("ice_lance_shatter", mage::iceLanceShatter);
    }

    public static String displayName(RaskolClasses plugin, PlayerClass pc, String id) {
        AbilityDef def = defById(plugin, pc, id);
        return def != null ? def.displayName() : id;
    }

    public static double radiusOf(RaskolClasses plugin, PlayerClass pc, String id, double def) {
        double v = plugin.getConfig().getDouble(
                "classes." + pc.name() + ".treeAbilities." + id + ".radius", def);
        return Double.isFinite(v) && v > 0.0 ? v : def;
    }

    public static int durationOf(RaskolClasses plugin, PlayerClass pc, String id, int def) {
        int v = plugin.getConfig().getInt(
                "classes." + pc.name() + ".treeAbilities." + id + ".duration", def);
        return v > 0 ? v : def;
    }

    public static String pcName(PlayerClass pc) {
        return pc.name().toLowerCase(Locale.ROOT);
    }
}
