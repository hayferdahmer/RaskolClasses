// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.School;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1.14.0 (контент-долги 1–6): способности, открываемые узлами деревьев путей
 * (type=unlock_ability/ultimate). Def'ы читаются из конфига
 * classes.<CLASS>.treeAbilities.<id>.*; кастеры регистрируются в AbilityRegistry
 * отдельно от базовых слотов 1–5 (selftest-чеки китов не меняются).
 * Гейт доступа — внутри методов кита: Spec2Service.hasUnlocked(uuid, id).
 *
 * Итог: 69 древесных способностей на 18 деревьев
 * (Воин 9 · Охотник 14 · Разбойник 16 · Маг 11 · Жрец 9 · Чернокнижник 10).
 *
 * 1.14.0 (Б11.1.2-A): универсальные фолбэк-хелперы чтения чисел способности
 * (treeAbilities → abilities → код-дефолт). Кастеры переключают свои приватные
 * base()/coeff()/power()/duration()/radius()/drain() на эти хелперы, чтобы
 * переносимые (slots 4–5) после резки abilities (11.1.2-B/11.1.3) продолжали
 * получать свои числа из treeAbilities. Для обычных способностей фолбэк
 * прозрачен: секции в treeAbilities нет → читается abilities → то же число.
 * Хелперы в том же пакете, что и киты → вызываются без импорта.
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
            case PRIEST -> List.of(
                    "purge", "pain_suppression", "spirit_shell",
                    "flash_heal", "lightwell", "divine_hymn",
                    "withering_touch", "mind_flay", "shadowfiend");
            case WARLOCK -> List.of(
                    "withering", "soul_siphon", "soul_harvest",
                    "immolate", "chaos_bolt", "conflagrate",
                    "dreadfire", "summon_demon", "demonic_pact", "demon_soul");
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

    /* ---------------- 1.14.0 (Б11.1.2-A): фолбэк-хелперы чтения чисел ---------------- */

    /**
     * Double-число способности: treeAbilities.<id>.<key> → abilities.<id>.<key> → def.
     * Drop-in замена приватного cfgD("classes.X.abilities.<id>.<key>", def):
     * сохраняет NaN-семантику (не-число → def) и не клампит (кламп — за вызывающим,
     * как в текущих radius()/drain()/threshold()/execute-mult()/per-purged()/channel()/
     * antiheal()/missing-hp-bonus()/base()/coeff()).
     */
    public static double numberOrKit(RaskolClasses plugin, PlayerClass pc, String id,
                                     String key, double def) {
        double v = plugin.getConfig().getDouble(
                "classes." + pc.name() + ".treeAbilities." + id + "." + key, Double.NaN);
        if (Double.isFinite(v)) {
            return v;
        }
        v = plugin.getConfig().getDouble(
                "classes." + pc.name() + ".abilities." + id + "." + key, Double.NaN);
        return Double.isFinite(v) ? v : def;
    }

    /**
     * Int-число способности: treeAbilities.<id>.<key> → abilities.<id>.<key> → def.
     * Использует isSet-проверку, чтобы отсутствующий ключ в treeAbilities не «съедал»
     * значение из abilities (getInt на unset вернул бы def, а не abilities).
     */
    public static int intOrKit(RaskolClasses plugin, PlayerClass pc, String id,
                               String key, int def) {
        String tPath = "classes." + pc.name() + ".treeAbilities." + id + "." + key;
        if (plugin.getConfig().isSet(tPath)) {
            return plugin.getConfig().getInt(tPath, def);
        }
        String kPath = "classes." + pc.name() + ".abilities." + id + "." + key;
        if (plugin.getConfig().isSet(kPath)) {
            return plugin.getConfig().getInt(kPath, def);
        }
        return def;
    }

    /**
     * String-число способности (напр. power): treeAbilities.<id>.<key> →
     * abilities.<id>.<key> → def. Пустая строка трактуется как отсутствие
     * (соответствует текущему cfgS/getString-поведению с дефолтом).
     */
    public static String stringOrKit(RaskolClasses plugin, PlayerClass pc, String id,
                                     String key, String def) {
        String v = plugin.getConfig().getString(
                "classes." + pc.name() + ".treeAbilities." + id + "." + key, null);
        if (v != null && !v.isEmpty()) {
            return v;
        }
        v = plugin.getConfig().getString(
                "classes." + pc.name() + ".abilities." + id + "." + key, null);
        return (v != null && !v.isEmpty()) ? v : def;
    }

    /**
     * Duration-хелпер с клампом > 0 (drop-in для приватного duration() всех китов):
     * treeAbilities.<id>.duration → abilities.<id>.duration → def; не-положительное → def.
     */
    public static int durationOrKit(RaskolClasses plugin, PlayerClass pc, String id, int def) {
        int v = intOrKit(plugin, pc, id, "duration", def);
        return v > 0 ? v : def;
    }

    /** Регистрация кастеров всех 69 древесных способностей шести классов. */
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

        PriestAbilities priest = new PriestAbilities(plugin);
        registry.registerTreeCaster("purge", priest::purge);
        registry.registerTreeCaster("pain_suppression", priest::painSuppression);
        registry.registerTreeCaster("spirit_shell", priest::spiritShell);
        registry.registerTreeCaster("flash_heal", priest::flashHeal);
        registry.registerTreeCaster("lightwell", priest::lightwell);
        registry.registerTreeCaster("divine_hymn", priest::divineHymn);
        registry.registerTreeCaster("withering_touch", priest::witheringTouch);
        registry.registerTreeCaster("mind_flay", priest::mindFlay);
        registry.registerTreeCaster("shadowfiend", priest::shadowfiend);

        WarlockAbilities warlock = new WarlockAbilities(plugin);
        registry.registerTreeCaster("withering", warlock::withering);
        registry.registerTreeCaster("soul_siphon", warlock::soulSiphon);
        registry.registerTreeCaster("soul_harvest", warlock::soulHarvest);
        registry.registerTreeCaster("immolate", warlock::immolate);
        registry.registerTreeCaster("chaos_bolt", warlock::chaosBolt);
        registry.registerTreeCaster("conflagrate", warlock::conflagrate);
        registry.registerTreeCaster("dreadfire", warlock::dreadfire);
        registry.registerTreeCaster("summon_demon", warlock::summonDemon);
        registry.registerTreeCaster("demonic_pact", warlock::demonicPact);
        registry.registerTreeCaster("demon_soul", warlock::demonSoul);
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
