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
