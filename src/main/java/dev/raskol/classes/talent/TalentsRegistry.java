// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import dev.raskol.classes.talent.TalentModel.TalentEffect;
import dev.raskol.classes.talent.TalentModel.TalentNode;
import dev.raskol.classes.talent.TalentModel.TalentTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.9.0 → 1.14.0: СТАТИЧЕСКИЙ КАТАЛОГ 18 деревьев + legacy occult.
 * 1.14.0 (Б4): WoW-стиль — все узлы maxRank=5, costPerRank=1, totalCost=45;
 *         эффект масштабируется рангом (TalentEffect.scaledBy).
 */
public final class TalentsRegistry {

    private static final Map<String, TalentTree> TREES = new HashMap<>();
    private static final TalentTree OCCULT_TREE = occult();

    static {
        TREES.put("guardian", guardian());
        TREES.put("berserker", berserker());
        TREES.put("arms", arms());
        TREES.put("marksman", marksman());
        TREES.put("survival", survival());
        TREES.put("beast_master", beastMaster());
        TREES.put("holy", holy());
        TREES.put("shadowweaver", shadowweaver());
        TREES.put("discipline", discipline());
        TREES.put("arcane", arcane());
        TREES.put("frost", frost());
        TREES.put("fire", fire());
        TREES.put("assassin", assassin());
        TREES.put("outlaw", outlaw());
        TREES.put("subtlety", subtlety());
        TREES.put("affliction", affliction());
        TREES.put("destruction", destruction());
        TREES.put("demonology", demonology());
        TREES.put("black_mage", OCCULT_TREE);
        TREES.put("hell_channel", OCCULT_TREE);
    }

    private TalentsRegistry() {
    }

    public static Map<String, TalentTree> all() {
        return Map.copyOf(TREES);
    }

    public static TalentTree treeOf(String specId) {
        return TREES.get(specId);
    }

    /** 1.14.0: фабрика с maxRank/costPerRank. */
    private static TalentNode n(String id, String specId, int tier, String branch,
                                List<String> prereqs, int costPerRank, int maxRank,
                                String name, String lore, TalentEffect effect) {
        return TalentModel.node(id, specId, tier, branch, prereqs, costPerRank, maxRank, name, lore, effect);
    }

    private static TalentTree tree(String specId, TalentNode... nodes) {
        return new TalentTree(specId, List.of(nodes));
    }

    /* =============================== WARRIOR: GUARDIAN =============================== */
    private static TalentTree guardian() {
        final String S = "guardian";
        return tree(S,
                n("g_bulwark", S, 1, "A", List.of(), 1, 5,
                        "Бастионная стойка", "+4% физрезиста постоянно (за ранг)",
                        new TalentEffect("resist", "phys", 4.0, 0.0)),
                n("g_vigil", S, 1, "B", List.of(), 1, 5,
                        "Дозорная выправка", "+4 СИЛЫ постоянно (за ранг)",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("g_shieldwall", S, 2, "A", List.of("g_bulwark"), 1, 5,
                        "Стена щитов", "«Шкура Бальдра»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "balder_skin", 6.0, 0.0)),
                n("g_parry_drill", S, 2, "A", List.of("g_bulwark"), 1, 5,
                        "Школа парирования", "+3% парирования (за ранг)",
                        new TalentEffect("avoid", "parry", 3.0, 0.0)),
                n("g_spear_riposte", S, 2, "B", List.of("g_vigil"), 1, 5,
                        "Контрвыпад", "«Удар Тира»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "tyr_strike", 8.0, 0.0)),
                n("g_endurance", S, 2, "B", List.of("g_vigil"), 1, 5,
                        "Полевая выносливость", "Ярость регенерирует на 1/с быстрее (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("g_bastion", S, 3, "A", List.of("g_shieldwall", "g_parry_drill"), 1, 5,
                        "Бастион", "+6% физ, +3% маг резиста (за ранг)",
                        new TalentEffect("resist", "both", 6.0, 3.0)),
                n("g_juggernaut", S, 3, "B", List.of("g_spear_riposte", "g_endurance"), 1, 5,
                        "Джаггернаут", "«Рагнарёк»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "ragnarok", 0.25, 0.0)),
                n("g_crown_aegis", S, 4, "A", List.of("g_bastion", "g_juggernaut"), 1, 5,
                        "Эгида Короны", "+6 СИЛЫ (за ранг), +4% физ, +4% маг резиста (за ранг)",
                        new TalentEffect("attr", "str", 6.0, 0.0))
        );
    }

    /* =============================== WARRIOR: BERSERKER =============================== */
    private static TalentTree berserker() {
        final String S = "berserker";
        return tree(S,
                n("b_rage_focus", S, 1, "A", List.of(), 1, 5,
                        "Фокус ярости", "Ярость регенерирует на 1/с быстрее (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("b_blood_scent", S, 1, "B", List.of(), 1, 5,
                        "Кровавый нюх", "+4 СИЛЫ постоянно (за ранг)",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("b_frenzy", S, 2, "A", List.of("b_rage_focus"), 1, 5,
                        "Неистовство", "«Берсеркерганг»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "berserkergang", 0.20, 0.0)),
                n("b_reckless", S, 2, "A", List.of("b_rage_focus"), 1, 5,
                        "Безрассудство", "«Удар Тира»: +10 к базе (за ранг)",
                        new TalentEffect("kit_base", "tyr_strike", 10.0, 0.0)),
                n("b_fenrir_howl", S, 2, "B", List.of("b_blood_scent"), 1, 5,
                        "Вой Фенрира", "«Кровь Фенрира»: +10 к базе (за ранг)",
                        new TalentEffect("kit_base", "fenrir_blood", 10.0, 0.0)),
                n("b_wound_fury", S, 2, "B", List.of("b_blood_scent"), 1, 5,
                        "Ярость ран", "Пассив «Казнь»: +4% шанса (за ранг)",
                        new TalentEffect("proc", "execute_passive", 0.04, 0.0)),
                n("b_rage_storm", S, 3, "A", List.of("b_frenzy", "b_reckless"), 1, 5,
                        "Шторм ярости", "«Удар Тира»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "tyr_strike", 0.25, 0.0)),
                n("b_blood_pact", S, 3, "B", List.of("b_fenrir_howl", "b_wound_fury"), 1, 5,
                        "Пакт крови", "+5% физрезиста (за ранг), реген ярости +1/с (за ранг)",
                        new TalentEffect("resist", "phys", 5.0, 0.0)),
                n("b_ragnarok_herald", S, 4, "A", List.of("b_rage_storm", "b_blood_pact"), 1, 5,
                        "Вестник Рагнарёка", "«Рагнарёк»: +30% коэф. (за ранг), −15% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "ragnarok", 0.30, 0.0))
        );
    }

    /* =============================== WARRIOR: ARMS (1.14.0) =============================== */
    private static TalentTree arms() {
        final String S = "arms";
        return tree(S,
                n("ar_tactical_mastery", S, 1, "A", List.of(), 1, 5,
                        "Тактическое мастерство", "«Удар Тира»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "tyr_strike", 6.0, 0.0)),
                n("ar_sharpened_blade", S, 1, "B", List.of(), 1, 5,
                        "Заточенный клинок", "+4 СИЛЫ постоянно (за ранг)",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("ar_deep_wounds", S, 2, "A", List.of("ar_tactical_mastery"), 1, 5,
                        "Глубокие раны", "«Удар Тира»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "tyr_strike", 0.20, 0.0)),
                n("ar_mortal_strike", S, 2, "A", List.of("ar_tactical_mastery"), 1, 5,
                        "Смертельный удар", "«Удар Тира»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "tyr_strike", 0.15, 0.0)),
                n("ar_sweeping_strikes", S, 2, "B", List.of("ar_sharpened_blade"), 1, 5,
                        "Широкие удары", "«Берсеркерганг»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "berserkergang", 0.20, 0.0)),
                n("ar_weapon_mastery", S, 2, "B", List.of("ar_sharpened_blade"), 1, 5,
                        "Мастерство оружия", "+5% физрезиста (за ранг)",
                        new TalentEffect("resist", "phys", 5.0, 0.0)),
                n("ar_bladestorm", S, 3, "A", List.of("ar_deep_wounds", "ar_mortal_strike"), 1, 5,
                        "Шторм клинков", "«Удар Тира»: +25% коэффициента (за ранг), AoE",
                        new TalentEffect("kit_mult", "tyr_strike", 0.25, 0.0)),
                n("ar_endless_rage", S, 3, "B", List.of("ar_sweeping_strikes", "ar_weapon_mastery"), 1, 5,
                        "Бесконечная ярость", "Ярость регенерирует на +2/с быстрее (за ранг)",
                        new TalentEffect("regen", "resource", 2.0, 0.0)),
                n("ar_taste_for_blood", S, 4, "A", List.of("ar_bladestorm", "ar_endless_rage"), 1, 5,
                        "Вкус крови", "Пассив «Казнь»: +8% шанса (за ранг), ×3.5 урона",
                        new TalentEffect("proc", "execute_passive", 0.08, 0.0))
        );
    }

    /* =============================== HUNTER: MARKSMAN =============================== */
    private static TalentTree marksman() {
        final String S = "marksman";
        return tree(S,
                n("l_true_aim", S, 1, "A", List.of(), 1, 5,
                        "Верный прицел", "Пассив «Хищник»: +4% шанса (за ранг)",
                        new TalentEffect("proc", "predator", 0.04, 0.0)),
                n("l_light_step", S, 1, "B", List.of(), 1, 5,
                        "Лёгкий шаг", "+4 ЛОВКОСТИ постоянно (за ранг)",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("l_pierce", S, 2, "A", List.of("l_true_aim"), 1, 5,
                        "Бронебойность", "«Пронзающий выстрел»: +10 к базе (за ранг)",
                        new TalentEffect("kit_base", "piercing_shot", 10.0, 0.0)),
                n("l_mark_deep", S, 2, "A", List.of("l_true_aim"), 1, 5,
                        "Глубокая метка", "«Метка Волка»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "wolf_mark", 0.20, 0.0)),
                n("l_quick_hands", S, 2, "B", List.of("l_light_step"), 1, 5,
                        "Быстрые руки", "«Пронзающий выстрел»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "piercing_shot", 0.15, 0.0)),
                n("l_fan_master", S, 2, "B", List.of("l_light_step"), 1, 5,
                        "Мастер веера", "«Веер стрел»: +4 к базе каждой стрелы (за ранг)",
                        new TalentEffect("kit_base", "arrow_fan", 4.0, 0.0)),
                n("l_headshot", S, 3, "A", List.of("l_pierce", "l_mark_deep"), 1, 5,
                        "Выстрел в голову", "«Пронзающий выстрел»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "piercing_shot", 0.25, 0.0)),
                n("l_storm_arrows", S, 3, "B", List.of("l_quick_hands", "l_fan_master"), 1, 5,
                        "Шторм стрел", "«Дождь стрел»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "arrow_rain", 0.20, 0.0)),
                n("l_execution_protocol", S, 4, "A", List.of("l_headshot", "l_storm_arrows"), 1, 5,
                        "Протокол ликвидации", "«Дождь стрел»: +30% коэф. (за ранг); «Хищник» +4% (за ранг)",
                        new TalentEffect("kit_mult", "arrow_rain", 0.30, 0.0))
        );
    }

    /* =============================== HUNTER: SURVIVAL (1.14.0) =============================== */
    private static TalentTree survival() {
        final String S = "survival";
        return tree(S,
                n("s_snare_wire", S, 1, "A", List.of(), 1, 5,
                        "Силки", "«Метка Волка»: +6 к базе (контроль-часть, за ранг)",
                        new TalentEffect("kit_base", "wolf_mark", 6.0, 0.0)),
                n("s_campcraft", S, 1, "B", List.of(), 1, 5,
                        "Лагерный навык", "Концентрация регенерирует на 1/с быстрее (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("s_rain_setup", S, 2, "A", List.of("s_snare_wire"), 1, 5,
                        "Дождь из засады", "«Дождь стрел»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "arrow_rain", 8.0, 0.0)),
                n("s_terrain", S, 2, "A", List.of("s_snare_wire"), 1, 5,
                        "Чтение местности", "+3% уклонения (за ранг)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("s_survivor", S, 2, "B", List.of("s_campcraft"), 1, 5,
                        "Выживальщик", "Концентрация регенерирует ещё на 1/с быстрее (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("s_swallow_reserve", S, 2, "B", List.of("s_campcraft"), 1, 5,
                        "Запас зелий", "«Ласточка»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "swallow", 0.20, 0.0)),
                n("s_master_trapper", S, 3, "A", List.of("s_rain_setup", "s_terrain"), 1, 5,
                        "Мастер силков", "«Метка Волка»: +25% коэф., −15% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "wolf_mark", 0.25, 0.0)),
                n("s_wild_guard", S, 3, "B", List.of("s_survivor", "s_swallow_reserve"), 1, 5,
                        "Дикая стража", "+5% физрезиста (за ранг)",
                        new TalentEffect("resist", "phys", 5.0, 0.0)),
                n("s_apex_hunter", S, 4, "A", List.of("s_master_trapper", "s_wild_guard"), 1, 5,
                        "Верховный охотник", "«Веер стрел»: +30% коэф., +3% уклонения (за ранг)",
                        new TalentEffect("kit_mult", "arrow_fan", 0.30, 0.0))
        );
    }

    /* =============================== HUNTER: BEAST_MASTER (1.14.0) =============================== */
    private static TalentTree beastMaster() {
        final String S = "beast_master";
        return tree(S,
                n("bm_wild_bond", S, 1, "A", List.of(), 1, 5,
                        "Дикая связь", "Пассив «Хищник»: +3% шанса (за ранг)",
                        new TalentEffect("proc", "predator", 0.03, 0.0)),
                n("bm_tracker_step", S, 1, "B", List.of(), 1, 5,
                        "Шаг следопыта", "+4 ЛОВКОСТИ постоянно (за ранг)",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("bm_pack_leader", S, 2, "A", List.of("bm_wild_bond"), 1, 5,
                        "Вожак стаи", "«Метка Волка»: +15% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "wolf_mark", 0.15, 0.0)),
                n("bm_feral_instinct", S, 2, "A", List.of("bm_wild_bond"), 1, 5,
                        "Звериный инстинкт", "+3% уклонения (за ранг)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("bm_primal_fury", S, 2, "B", List.of("bm_tracker_step"), 1, 5,
                        "Первобытная ярость", "«Пронзающий выстрел»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "piercing_shot", 8.0, 0.0)),
                n("bm_endurance", S, 2, "B", List.of("bm_tracker_step"), 1, 5,
                        "Выносливость зверя", "+5% физрезиста (за ранг)",
                        new TalentEffect("resist", "phys", 5.0, 0.0)),
                n("bm_bestial_wrath", S, 3, "A", List.of("bm_pack_leader", "bm_feral_instinct"), 1, 5,
                        "Звериная ярость", "«Метка Волка»: +25% коэф., +4 ЛОВК. (за ранг)",
                        new TalentEffect("kit_mult", "wolf_mark", 0.25, 0.0)),
                n("bm_spirit_link", S, 3, "B", List.of("bm_primal_fury", "bm_endurance"), 1, 5,
                        "Связь духа", "Реген Концентрации +1/с (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("bm_alpha_predator", S, 4, "A", List.of("bm_bestial_wrath", "bm_spirit_link"), 1, 5,
                        "Альфа-хищник", "«Дождь стрел»: +30% коэф., +6 ЛОВК. (за ранг)",
                        new TalentEffect("kit_mult", "arrow_rain", 0.30, 0.0))
        );
    }

    /* =============================== PRIEST: HOLY (1.14.0) =============================== */
    private static TalentTree holy() {
        final String S = "holy";
        return tree(S,
                n("o_ward", S, 1, "A", List.of(), 1, 5,
                        "Оберег", "+4% магрезиста (за ранг)",
                        new TalentEffect("resist", "magic", 4.0, 0.0)),
                n("o_insight", S, 1, "B", List.of(), 1, 5,
                        "Прозорливость", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("o_faith_wall", S, 2, "A", List.of("o_ward"), 1, 5,
                        "Стена веры", "«Эгида Веры»: +5 к базе (за ранг)",
                        new TalentEffect("kit_base", "aegis_faith", 5.0, 0.0)),
                n("o_sanctuary_echo", S, 2, "A", List.of("o_ward"), 1, 5,
                        "Эхо убежища", "«Эгида Веры»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "aegis_faith", 0.15, 0.0)),
                n("o_tear_focus", S, 2, "B", List.of("o_insight"), 1, 5,
                        "Сосредоточие слезы", "«Слеза Святой»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "saint_tear", 8.0, 0.0)),
                n("o_word_power", S, 2, "B", List.of("o_insight"), 1, 5,
                        "Сила слова", "«Слово Жизни»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "word_of_life", 0.20, 0.0)),
                n("o_divine_shell", S, 3, "A", List.of("o_faith_wall", "o_sanctuary_echo"), 1, 5,
                        "Божественный покров", "+4% физ, +4% маг резиста (за ранг)",
                        new TalentEffect("resist", "both", 4.0, 4.0)),
                n("o_prophecy", S, 3, "B", List.of("o_tear_focus", "o_word_power"), 1, 5,
                        "Пророчество", "«Круг Элизия»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "circle_elysium", 0.25, 0.0)),
                n("o_voice_of_light", S, 4, "A", List.of("o_divine_shell", "o_prophecy"), 1, 5,
                        "Голос Света", "«Слово Жизни»: +30% коэф., реген Света +1/с (за ранг)",
                        new TalentEffect("kit_mult", "word_of_life", 0.30, 0.0))
        );
    }

    /* =============================== PRIEST: SHADOWWEAVER =============================== */
    private static TalentTree shadowweaver() {
        final String S = "shadowweaver";
        return tree(S,
                n("i_zeal", S, 1, "A", List.of(), 1, 5,
                        "Рвение", "«Кара Небес»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "wrath_heaven", 8.0, 0.0)),
                n("i_faith_armor", S, 1, "B", List.of(), 1, 5,
                        "Доспех веры", "+4% физрезиста (за ранг)",
                        new TalentEffect("resist", "phys", 4.0, 0.0)),
                n("i_brand", S, 2, "A", List.of("i_zeal"), 1, 5,
                        "Клеймо еретика", "«Кара Небес»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "wrath_heaven", 0.20, 0.0)),
                n("i_smite_drill", S, 2, "A", List.of("i_zeal"), 1, 5,
                        "Школа кары", "«Кара Небес»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "wrath_heaven", 0.15, 0.0)),
                n("i_unbreakable", S, 2, "B", List.of("i_faith_armor"), 1, 5,
                        "Несокрушимость", "+4 СИЛЫ (за ранг)",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("i_martyr_tear", S, 2, "B", List.of("i_faith_armor"), 1, 5,
                        "Слеза мученика", "«Слеза Святой»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "saint_tear", 6.0, 0.0)),
                n("i_holy_wrath", S, 3, "A", List.of("i_brand", "i_smite_drill"), 1, 5,
                        "Святая ярость", "«Кара Небес»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "wrath_heaven", 0.25, 0.0)),
                n("i_confessor", S, 3, "B", List.of("i_unbreakable", "i_martyr_tear"), 1, 5,
                        "Исповедник", "+6% магрезиста (за ранг)",
                        new TalentEffect("resist", "magic", 6.0, 0.0)),
                n("i_hand_of_justice", S, 4, "A", List.of("i_holy_wrath", "i_confessor"), 1, 5,
                        "Десница правосудия", "«Кара Небес»: +30% коэф., «Благодать» +5% (за ранг)",
                        new TalentEffect("kit_mult", "wrath_heaven", 0.30, 0.0))
        );
    }

    /* =============================== PRIEST: DISCIPLINE (1.14.0) =============================== */
    private static TalentTree discipline() {
        final String S = "discipline";
        return tree(S,
                n("d_atonement", S, 1, "A", List.of(), 1, 5,
                        "Искупление", "«Слово Жизни»: +5 к базе (за ранг)",
                        new TalentEffect("kit_base", "word_of_life", 5.0, 0.0)),
                n("d_inner_focus", S, 1, "B", List.of(), 1, 5,
                        "Внутренний фокус", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("d_power_word_shield", S, 2, "A", List.of("d_atonement"), 1, 5,
                        "Слово Силы: Щит", "«Эгида Веры»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "aegis_faith", 0.20, 0.0)),
                n("d_grace", S, 2, "A", List.of("d_atonement"), 1, 5,
                        "Благодать", "«Слеза Святой»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "saint_tear", 6.0, 0.0)),
                n("d_mental_strength", S, 2, "B", List.of("d_inner_focus"), 1, 5,
                        "Сила духа", "+4% магрезиста (за ранг)",
                        new TalentEffect("resist", "magic", 4.0, 0.0)),
                n("d_renewed_hope", S, 2, "B", List.of("d_inner_focus"), 1, 5,
                        "Обновлённая надежда", "«Круг Элизия»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "circle_elysium", 0.15, 0.0)),
                n("d_penance", S, 3, "A", List.of("d_power_word_shield", "d_grace"), 1, 5,
                        "Исповедь", "«Слово Жизни»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "word_of_life", 0.25, 0.0)),
                n("d_borrowed_time", S, 3, "B", List.of("d_mental_strength", "d_renewed_hope"), 1, 5,
                        "Заёмное время", "Реген Света +1/с, +4% физрезиста (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("d_divine_aegis", S, 4, "A", List.of("d_penance", "d_borrowed_time"), 1, 5,
                        "Божественный эгид", "«Эгида Веры»: +30% коэф., +6% магрезиста (за ранг)",
                        new TalentEffect("kit_mult", "aegis_faith", 0.30, 0.0))
        );
    }

    /* =============================== MAGE: ARCANE =============================== */
    private static TalentTree arcane() {
        final String S = "arcane";
        return tree(S,
                n("e_ember", S, 1, "A", List.of(), 1, 5,
                        "Тлеющий уголь", "«Огонь Прометея»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "fire_prometheus", 6.0, 0.0)),
                n("e_frost_vein", S, 1, "B", List.of(), 1, 5,
                        "Морозная жилка", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("e_wildfire", S, 2, "A", List.of("e_ember"), 1, 5,
                        "Дикий огонь", "«Огонь Прометея»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "fire_prometheus", 0.20, 0.0)),
                n("e_zeus_channel", S, 2, "A", List.of("e_ember"), 1, 5,
                        "Канал Зевса", "«Гнев Зевса»: +10 к базе (за ранг)",
                        new TalentEffect("kit_base", "zeus_wrath", 10.0, 0.0)),
                n("e_deep_freeze", S, 2, "B", List.of("e_frost_vein"), 1, 5,
                        "Глубокая заморозка", "«Дыхание Борея»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "boreas_breath", 0.20, 0.0)),
                n("e_mana_flow", S, 2, "B", List.of("e_frost_vein"), 1, 5,
                        "Поток маны", "Реген маны сдвигается на +1 тир (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("e_pyromaster", S, 3, "A", List.of("e_wildfire", "e_zeus_channel"), 1, 5,
                        "Пиромант-мастер", "«Огонь Прометея»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "fire_prometheus", 0.25, 0.0)),
                n("e_absolute_zero", S, 3, "B", List.of("e_deep_freeze", "e_mana_flow"), 1, 5,
                        "Абсолютный ноль", "«Дыхание Борея»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "boreas_breath", 0.20, 0.0)),
                n("e_avatar_of_storms", S, 4, "A", List.of("e_pyromaster", "e_absolute_zero"), 1, 5,
                        "Аватар бурь", "«Гнев Зевса»: +30% коэф., −15% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "zeus_wrath", 0.30, 0.0))
        );
    }

    /* =============================== MAGE: FROST =============================== */
    private static TalentTree frost() {
        final String S = "frost";
        return tree(S,
                n("f_permafrost", S, 1, "A", List.of(), 1, 5,
                        "Вечная мерзлота", "«Дыхание Борея»: +5 к базе (за ранг)",
                        new TalentEffect("kit_base", "boreas_breath", 5.0, 0.0)),
                n("f_crystal_blood", S, 1, "B", List.of(), 1, 5,
                        "Кристаллическая кровь", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("f_glacial_heart", S, 2, "A", List.of("f_permafrost"), 1, 5,
                        "Ледяное сердце", "+5% магрезиста (за ранг)",
                        new TalentEffect("resist", "magic", 5.0, 0.0)),
                n("f_frozen_shroud", S, 2, "A", List.of("f_permafrost"), 1, 5,
                        "Застывший саван", "«Эгида Афины»: +5 к базе (за ранг)",
                        new TalentEffect("kit_base", "athena_aegis", 5.0, 0.0)),
                n("f_ice_tomb", S, 2, "B", List.of("f_crystal_blood"), 1, 5,
                        "Ледяная гробница", "«Дыхание Борея»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "boreas_breath", 0.20, 0.0)),
                n("f_cold_snap", S, 2, "B", List.of("f_crystal_blood"), 1, 5,
                        "Резкое похолодание", "«Дыхание Борея»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "boreas_breath", 0.15, 0.0)),
                n("f_eternal_winter", S, 3, "A", List.of("f_glacial_heart", "f_frozen_shroud"), 1, 5,
                        "Вечная зима", "«Дыхание Борея»: +25% коэф., +4% маг резиста (за ранг)",
                        new TalentEffect("kit_mult", "boreas_breath", 0.25, 0.0)),
                n("f_frostbite", S, 3, "B", List.of("f_ice_tomb", "f_cold_snap"), 1, 5,
                        "Обморожение", "«Дыхание Борея»: +8 к базе, −10% кулдауна (за ранг)",
                        new TalentEffect("kit_base", "boreas_breath", 8.0, 0.0)),
                n("f_avatar_of_winter", S, 4, "A", List.of("f_eternal_winter", "f_frostbite"), 1, 5,
                        "Аватар зимы", "«Дыхание Борея»: +30% коэф., −20% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "boreas_breath", 0.30, 0.0))
        );
    }

    /* =============================== MAGE: FIRE (1.14.0) =============================== */
    private static TalentTree fire() {
        final String S = "fire";
        return tree(S,
                n("fi_ignite", S, 1, "A", List.of(), 1, 5,
                        "Воспламенение", "«Огонь Прометея»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "fire_prometheus", 6.0, 0.0)),
                n("fi_molten_armor", S, 1, "B", List.of(), 1, 5,
                        "Расплавленная броня", "+4% магрезиста (за ранг)",
                        new TalentEffect("resist", "magic", 4.0, 0.0)),
                n("fi_pyroblast", S, 2, "A", List.of("fi_ignite"), 1, 5,
                        "Огненная глыба", "«Огонь Прометея»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "fire_prometheus", 0.25, 0.0)),
                n("fi_impact", S, 2, "A", List.of("fi_ignite"), 1, 5,
                        "Сотрясение", "«Огонь Прометея»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "fire_prometheus", 0.15, 0.0)),
                n("fi_blazing_speed", S, 2, "B", List.of("fi_molten_armor"), 1, 5,
                        "Пылающая скорость", "+3% уклонения (за ранг)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("fi_firestarter", S, 2, "B", List.of("fi_molten_armor"), 1, 5,
                        "Зажигатель", "Реген маны +1/с (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("fi_combustion", S, 3, "A", List.of("fi_pyroblast", "fi_impact"), 1, 5,
                        "Возгорание", "«Огонь Прометея»: +30% коэф., горение +50% (за ранг)",
                        new TalentEffect("kit_mult", "fire_prometheus", 0.30, 0.0)),
                n("fi_molten_fury", S, 3, "B", List.of("fi_blazing_speed", "fi_firestarter"), 1, 5,
                        "Расплавленная ярость", "«Гнев Зевса»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "zeus_wrath", 0.20, 0.0)),
                n("fi_living_bomb", S, 4, "A", List.of("fi_combustion", "fi_molten_fury"), 1, 5,
                        "Живая бомба", "«Гнев Зевса»: +30% коэф., −20% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "zeus_wrath", 0.30, 0.0))
        );
    }

    /* =============================== ROGUE: ASSASSIN (1.14.0) =============================== */
    private static TalentTree assassin() {
        final String S = "assassin";
        return tree(S,
                n("a_toxin_mix", S, 1, "A", List.of(), 1, 5,
                        "Смесь токсинов", "«Яд Борджа»: +5 к базе (за ранг)",
                        new TalentEffect("kit_base", "borgia_poison", 5.0, 0.0)),
                n("a_shadow_drill", S, 1, "B", List.of(), 1, 5,
                        "Школа тени", "+4 ЛОВКОСТИ (за ранг)",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("a_deadly_poison", S, 2, "A", List.of("a_toxin_mix"), 1, 5,
                        "Смертельный яд", "«Яд Борджа»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "borgia_poison", 0.20, 0.0)),
                n("a_strangle_hold", S, 2, "A", List.of("a_toxin_mix"), 1, 5,
                        "Мёртвая хватка", "«Удушение палача»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "strangle", 8.0, 0.0)),
                n("a_backstab_master", S, 2, "B", List.of("a_shadow_drill"), 1, 5,
                        "Мастер спины", "Пассив «Садизм»: +2 к бонусу (за ранг)",
                        new TalentEffect("proc", "sadism", 2.0, 0.0)),
                n("a_cloak_reserve", S, 2, "B", List.of("a_shadow_drill"), 1, 5,
                        "Запас плаща", "«Плащ теней»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "shadow_cloak", 0.20, 0.0)),
                n("a_venom_master", S, 3, "A", List.of("a_deadly_poison", "a_strangle_hold"), 1, 5,
                        "Мастер ядов", "«Яд Борджа»: +25% коэф., «Отравл. клинки» +5% (за ранг)",
                        new TalentEffect("kit_mult", "borgia_poison", 0.25, 0.0)),
                n("a_executioner", S, 3, "B", List.of("a_backstab_master", "a_cloak_reserve"), 1, 5,
                        "Палач", "«Удушение палача»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "strangle", 0.25, 0.0)),
                n("a_death_mark", S, 4, "A", List.of("a_venom_master", "a_executioner"), 1, 5,
                        "Метка смерти", "«Удушение палача»: +30% коэф., +3% уклонения (за ранг)",
                        new TalentEffect("kit_mult", "strangle", 0.30, 0.0))
        );
    }

    /* =============================== ROGUE: OUTLAW (1.14.0) =============================== */
    private static TalentTree outlaw() {
        final String S = "outlaw";
        return tree(S,
                n("ol_reflex", S, 1, "A", List.of(), 1, 5,
                        "Рефлексы", "+3% уклонения (за ранг)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("ol_light_hands", S, 1, "B", List.of(), 1, 5,
                        "Лёгкие руки", "+4 ЛОВКОСТИ (за ранг)",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("ol_afterimage", S, 2, "A", List.of("ol_reflex"), 1, 5,
                        "Послеобраз", "+3% уклонения (итого +6, за ранг)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("ol_parry_trick", S, 2, "A", List.of("ol_reflex"), 1, 5,
                        "Трюк парирования", "+3% парирования (за ранг)",
                        new TalentEffect("avoid", "parry", 3.0, 0.0)),
                n("ol_blade_tempo", S, 2, "B", List.of("ol_light_hands"), 1, 5,
                        "Темп клинков", "«Веер клинков»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "blade_fan", 0.15, 0.0)),
                n("ol_fan_edge", S, 2, "B", List.of("ol_light_hands"), 1, 5,
                        "Кромка веера", "«Веер клинков»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "blade_fan", 6.0, 0.0)),
                n("ol_untouchable", S, 3, "A", List.of("ol_afterimage", "ol_parry_trick"), 1, 5,
                        "Неприкасаемый", "+4% уклонения (итого +10), +3% физрезиста (за ранг)",
                        new TalentEffect("avoid", "dodge", 4.0, 0.0)),
                n("ol_blade_storm", S, 3, "B", List.of("ol_blade_tempo", "ol_fan_edge"), 1, 5,
                        "Шторм клинков", "«Веер клинков»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "blade_fan", 0.25, 0.0)),
                n("ol_dance_of_cuts", S, 4, "A", List.of("ol_untouchable", "ol_blade_storm"), 1, 5,
                        "Танец тысячи порезов", "«Веер клинков»: +30% коэф., dodge proc (за ранг)",
                        new TalentEffect("kit_mult", "blade_fan", 0.30, 0.0))
        );
    }

    /* =============================== ROGUE: SUBTLETY (1.14.0) =============================== */
    private static TalentTree subtlety() {
        final String S = "subtlety";
        return tree(S,
                n("su_nightstalker", S, 1, "A", List.of(), 1, 5,
                        "Ночной охотник", "+3% уклонения (за ранг)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("su_shadowstep", S, 1, "B", List.of(), 1, 5,
                        "Шаг сквозь тень", "+4 ЛОВКОСТИ (за ранг)",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("su_ambush", S, 2, "A", List.of("su_nightstalker"), 1, 5,
                        "Засада", "«Удушение палача»: +15% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "strangle", 0.15, 0.0)),
                n("su_opportunity", S, 2, "A", List.of("su_nightstalker"), 1, 5,
                        "Возможность", "«Веер клинков»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "blade_fan", 6.0, 0.0)),
                n("su_preparation", S, 2, "B", List.of("su_shadowstep"), 1, 5,
                        "Подготовка", "«Плащ теней»: −25% кулдауна (за ранг)",
                        new TalentEffect("cd", "shadow_cloak", 0.25, 0.0)),
                n("su_shadow_dance", S, 2, "B", List.of("su_shadowstep"), 1, 5,
                        "Танец теней", "«Танец теней»: +10 ЛОВК. (вместо 30, за ранг)",
                        new TalentEffect("kit_base", "shadow_dance", 10.0, 0.0)),
                n("su_death_from_above", S, 3, "A", List.of("su_ambush", "su_opportunity"), 1, 5,
                        "Смерть сверху", "«Удушение палача»: +25% коэф., +5% уклонения (за ранг)",
                        new TalentEffect("kit_mult", "strangle", 0.25, 0.0)),
                n("su_master_of_subtlety", S, 3, "B", List.of("su_preparation", "su_shadow_dance"), 1, 5,
                        "Мастер скрытности", "Пассив «Садизм»: +3 к бонусу (за ранг)",
                        new TalentEffect("proc", "sadism", 3.0, 0.0)),
                n("su_shadow_blades", S, 4, "A", List.of("su_death_from_above", "su_master_of_subtlety"), 1, 5,
                        "Теневые клинки", "«Танец теней»: +30% коэф., +6 ЛОВК. (за ранг)",
                        new TalentEffect("kit_mult", "shadow_dance", 0.30, 0.0))
        );
    }

    /* =============================== WARLOCK: AFFLICTION (1.14.0) =============================== */
    private static TalentTree affliction() {
        final String S = "affliction";
        return tree(S,
                n("af_corruption", S, 1, "A", List.of(), 1, 5,
                        "Порча", "«Чёрное Слово»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "black_word", 8.0, 0.0)),
                n("af_shadow_embrace", S, 1, "B", List.of(), 1, 5,
                        "Объятие тени", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("af_agony", S, 2, "A", List.of("af_corruption"), 1, 5,
                        "Агония", "«Чёрное Слово»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "black_word", 0.20, 0.0)),
                n("af_unstable_affliction", S, 2, "A", List.of("af_corruption"), 1, 5,
                        "Нестабильная порча", "«Раскол Души»: +10 к базе (за ранг)",
                        new TalentEffect("kit_base", "soul_rift", 10.0, 0.0)),
                n("af_siphon_life", S, 2, "B", List.of("af_shadow_embrace"), 1, 5,
                        "Вытягивание жизни", "«Голод Скверны»: +3% дрейна (за ранг)",
                        new TalentEffect("kit_base", "hunger_corruption", 3.0, 0.0)),
                n("af_nightfall", S, 2, "B", List.of("af_shadow_embrace"), 1, 5,
                        "Сумерки", "«Небытие»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "unwriting", 0.15, 0.0)),
                n("af_haunt", S, 3, "A", List.of("af_agony", "af_unstable_affliction"), 1, 5,
                        "Призрак", "«Раскол Души»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "soul_rift", 0.25, 0.0)),
                n("af_soul_conduit", S, 3, "B", List.of("af_siphon_life", "af_nightfall"), 1, 5,
                        "Проводник душ", "+6% магрезиста, реген Скверны +1/с (за ранг)",
                        new TalentEffect("resist", "magic", 6.0, 0.0)),
                n("af_malefic_grasp", S, 4, "A", List.of("af_haunt", "af_soul_conduit"), 1, 5,
                        "Злой захват", "«Раскол Души»: +30% коэф., −20% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "soul_rift", 0.30, 0.0))
        );
    }

    /* =============================== WARLOCK: DESTRUCTION (1.14.0) =============================== */
    private static TalentTree destruction() {
        final String S = "destruction";
        return tree(S,
                n("de_immolate", S, 1, "A", List.of(), 1, 5,
                        "Жертвенный огонь", "«Чёрное Слово»: +10 к базе (за ранг)",
                        new TalentEffect("kit_base", "black_word", 10.0, 0.0)),
                n("de_aftermath", S, 1, "B", List.of(), 1, 5,
                        "Последствия", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("de_backdraft", S, 2, "A", List.of("de_immolate"), 1, 5,
                        "Обратная тяга", "«Чёрное Слово»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "black_word", 0.20, 0.0)),
                n("de_fire_and_brimstone", S, 2, "A", List.of("de_immolate"), 1, 5,
                        "Огонь и сера", "«Печать Погибели»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "ruin_seal", 0.20, 0.0)),
                n("de_shadowburn", S, 2, "B", List.of("de_aftermath"), 1, 5,
                        "Выжигание тени", "«Небытие»: +8 к базе (за ранг)",
                        new TalentEffect("kit_base", "unwriting", 8.0, 0.0)),
                n("de_ruin", S, 2, "B", List.of("de_aftermath"), 1, 5,
                        "Разрушение", "«Голод Скверны»: +20% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "hunger_corruption", 0.20, 0.0)),
                n("de_chaos_bolt", S, 3, "A", List.of("de_backdraft", "de_fire_and_brimstone"), 1, 5,
                        "Стрела хаоса", "«Печать Погибели»: +30% коэф., −15% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "ruin_seal", 0.30, 0.0)),
                n("de_ember_tap", S, 3, "B", List.of("de_shadowburn", "de_ruin"), 1, 5,
                        "Вытягивание жара", "Реген Скверны +2/с (за ранг)",
                        new TalentEffect("regen", "resource", 2.0, 0.0)),
                n("de_cataclysm", S, 4, "A", List.of("de_chaos_bolt", "de_ember_tap"), 1, 5,
                        "Катаклизм", "«Раскол Души»: +35% коэф., +4 ИНТ. (за ранг)",
                        new TalentEffect("kit_mult", "soul_rift", 0.35, 0.0))
        );
    }

    /* =============================== WARLOCK: DEMONOLOGY (1.14.0) =============================== */
    private static TalentTree demonology() {
        final String S = "demonology";
        return tree(S,
                n("dm_demonic_embrace", S, 1, "A", List.of(), 1, 5,
                        "Демоническое объятие", "+4% магрезиста (за ранг)",
                        new TalentEffect("resist", "magic", 4.0, 0.0)),
                n("dm_dark_pact", S, 1, "B", List.of(), 1, 5,
                        "Тёмный пакт", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("dm_soul_link", S, 2, "A", List.of("dm_demonic_embrace"), 1, 5,
                        "Связь души", "+5% физ, +3% маг резиста (за ранг)",
                        new TalentEffect("resist", "both", 5.0, 3.0)),
                n("dm_demonic_knowledge", S, 2, "A", List.of("dm_demonic_embrace"), 1, 5,
                        "Демоническое знание", "«Небытие»: +6 к базе (за ранг)",
                        new TalentEffect("kit_base", "unwriting", 6.0, 0.0)),
                n("dm_master_conjuror", S, 2, "B", List.of("dm_dark_pact"), 1, 5,
                        "Мастер призыва", "«Печать Погибели»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "ruin_seal", 0.20, 0.0)),
                n("dm_fel_synergy", S, 2, "B", List.of("dm_dark_pact"), 1, 5,
                        "Скверновая синергия", "Реген Скверны +1/с, +4 ИНТ. (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("dm_metamorphosis", S, 3, "A", List.of("dm_soul_link", "dm_demonic_knowledge"), 1, 5,
                        "Метаморфоза", "«Раскол Души»: +25% коэф., +6% магрезиста (за ранг)",
                        new TalentEffect("kit_mult", "soul_rift", 0.25, 0.0)),
                n("dm_demonic_empowerment", S, 3, "B", List.of("dm_master_conjuror", "dm_fel_synergy"), 1, 5,
                        "Демоническое усиление", "«Чёрное Слово»: +30% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "black_word", 0.30, 0.0)),
                n("dm_summon_infernal", S, 4, "A", List.of("dm_metamorphosis", "dm_demonic_empowerment"), 1, 5,
                        "Призыв инфернала", "«Раскол Души»: +35% коэф., −20% кулдауна (за ранг)",
                        new TalentEffect("kit_mult", "soul_rift", 0.35, 0.0))
        );
    }

    /* =============================== WARLOCK: OCCULT (legacy 1.10.0) =============================== */
    private static TalentTree occult() {
        final String S = "black_mage";
        return tree(S,
                n("w_acid_ink", S, 1, "A", List.of(), 1, 5,
                        "Едкие Чернила", "«Чёрное Слово»: +10% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "black_word", 0.10, 0.0)),
                n("w_sturdy_binding", S, 1, "B", List.of(), 1, 5,
                        "Крепкий Переплёт", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("w_greedy_word", S, 2, "A", List.of("w_acid_ink"), 1, 5,
                        "Жадное Слово", "«Чёрное Слово»: ещё +15% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "black_word", 0.15, 0.0)),
                n("w_slow_close", S, 2, "A", List.of("w_acid_ink"), 1, 5,
                        "Медленное Закрытие", "Скверна регенерирует на +1/с быстрее вне боя (за ранг)",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("w_deep_seal", S, 2, "B", List.of("w_sturdy_binding"), 1, 5,
                        "Глубокая Печать", "«Печать Погибели»: −15% кулдауна (за ранг)",
                        new TalentEffect("cd", "ruin_seal", 0.15, 0.0)),
                n("w_long_ban", S, 2, "B", List.of("w_sturdy_binding"), 1, 5,
                        "Долгий Запрет", "+4 ИНТЕЛЛЕКТА (за ранг)",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("w_open_book", S, 3, "A", List.of("w_greedy_word", "w_slow_close"), 1, 5,
                        "Открытая Книга", "«Раскол Души»: +25% коэффициента (за ранг)",
                        new TalentEffect("kit_mult", "soul_rift", 0.25, 0.0)),
                n("w_punishing_corruption", S, 3, "B", List.of("w_deep_seal", "w_long_ban"), 1, 5,
                        "Карающая Скверна", "+6% магрезиста постоянно (за ранг)",
                        new TalentEffect("resist", "magic", 6.0, 0.0)),
                n("w_last_page", S, 4, "A", List.of("w_open_book", "w_punishing_corruption"), 1, 5,
                        "Последняя Страница", "«Раскол Души»: −20% кулдауна (за ранг)",
                        new TalentEffect("cd", "soul_rift", 0.20, 0.0))
        );
    }
}
