// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import dev.raskol.classes.talent.TalentModel.TalentEffect;
import dev.raskol.classes.talent.TalentModel.TalentNode;
import dev.raskol.classes.talent.TalentModel.TalentTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.9.0 инкремент 2A: СТАТИЧЕСКИЙ КАТАЛОГ 10 деревьев × 9 узлов.
 * Реальные id спеков (Spec.id() — lowercase enum name) ключуют деревья.
 *
 * Маппинг (реальный specId → дерево):
 *  guardian   → GUARDIAN  (g_)  — Страж-танк
 *  berserker  → BERSERKER (b_)  — Ярость-дамагер
 *  marksman   → LIQUIDATOR (l_) — Крит с дистанции (моё лор-название LIQUIDATOR)
 *  tracker    → TRAPPER    (t_) — Контроль (моё лор-название TRAPPER)
 *  lightbearer → ORACLE    (o_) — Усиленное лечение (ORACLE)
 *  shadowweaver → INQUISITOR (i_) — Лечение от урона (INQUISITOR)
 *  arcane     → ELEMENTALIST (e_) — Усиленные заклинания (ELEMENTALIST)
 *  frost      → FROST       (f_) — Контроль льдом (НОВОЕ дерево)
 *  liquidator → ASSASSIN    (a_) — Критические удары (ASSASSIN)
 *  trickster  → TRICKSTER   (t2_) — Уклонение (TRICKSTER)
 *
 * Шаблоны эффектов:
 *  attr      = ATTR  (+STR/AGI/INT)
 *  resist    = RESIST (phys/magic/both; value=phys, value2=magic если both)
 *  kit_base  = +base к абилке (target = id абилки)
 *  kit_mult  = ×(1+value) к coeff абилки (value в долях: 0.25 = +25%)
 *  cd        = −% кулдауна абилки (value в долях: 0.15 = −15%)
 *  regen     = +ресурс/с (аддитивно к класс-регену)
 *  avoid     = +dodge/parry flat % (target = "dodge" | "parry")
 *  proc      = +шанс класс-пассива (target = id проки)
 */
public final class TalentsRegistry {

    private static final Map<String, TalentTree> TREES = new HashMap<>();

    static {
        TREES.put("guardian", guardian());
        TREES.put("berserker", berserker());
        TREES.put("marksman", marksman());
        TREES.put("tracker", tracker());
        TREES.put("lightbearer", lightbearer());
        TREES.put("shadowweaver", shadowweaver());
        TREES.put("arcane", arcane());
        TREES.put("frost", frost());
        TREES.put("liquidator", liquidator());
        TREES.put("trickster", trickster());
    }

    private TalentsRegistry() {
    }

    /** Каталог всех деревьев (неизменяемая копия). */
    public static Map<String, TalentTree> all() {
        return Map.copyOf(TREES);
    }

    /** Дерево по id спеки (null если не найдено). */
    public static TalentTree treeOf(String specId) {
        return TREES.get(specId);
    }

    /* ------------------------------ Вспомогательные фабрики ------------------------------ */

    private static TalentNode n(String id, String specId, int tier, String branch,
                                List<String> prereqs, int cost,
                                String name, String lore, TalentEffect effect) {
        return TalentModel.node(id, specId, tier, branch, prereqs, cost, name, lore, effect);
    }

    private static TalentTree tree(String specId, TalentNode... nodes) {
        return new TalentTree(specId, List.of(nodes));
    }

    /* =============================== WARRIOR: GUARDIAN =============================== */
    /* Ветка A = стена (RESIST + AVOID + KIT_BASE balder_skin), B = контрудар (KIT_BASE/MULT tyr/ragnarok) */
    private static TalentTree guardian() {
        final String S = "guardian";
        return tree(S,
                n("g_bulwark", S, 1, "A", List.of(), 1,
                        "Бастионная стойка", "+4% физрезиста постоянно",
                        TalentModel.node("g_bulwark", 0).effect().equals(TalentEffect.of("resist", "phys", 4.0)) ? null
                                : new TalentEffect("resist", "phys", 4.0, 0.0)),
                n("g_vigil", S, 1, "B", List.of(), 1,
                        "Дозорная выправка", "+4 СИЛЫ постоянно",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("g_shieldwall", S, 2, "A", List.of("g_bulwark"), 2,
                        "Стена щитов", "«Шкура Бальдра»: +6 к базе",
                        new TalentEffect("kit_base", "balder_skin", 6.0, 0.0)),
                n("g_parry_drill", S, 2, "A", List.of("g_bulwark"), 2,
                        "Школа парирования", "+3% парирования",
                        new TalentEffect("avoid", "parry", 3.0, 0.0)),
                n("g_spear_riposte", S, 2, "B", List.of("g_vigil"), 2,
                        "Контрвыпад", "«Удар Тира»: +8 к базе",
                        new TalentEffect("kit_base", "tyr_strike", 8.0, 0.0)),
                n("g_endurance", S, 2, "B", List.of("g_vigil"), 2,
                        "Полевая выносливость", "Ярость регенерирует на 1/с быстрее вне боя",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("g_bastion", S, 3, "A", List.of("g_shieldwall", "g_parry_drill"), 3,
                        "Бастион", "+6% физ, +3% маг резиста",
                        new TalentEffect("resist", "both", 6.0, 3.0)),
                n("g_juggernaut", S, 3, "B", List.of("g_spear_riposte", "g_endurance"), 3,
                        "Джаггернаут", "«Рагнарёк»: +25% коэффициента",
                        new TalentEffect("kit_mult", "ragnarok", 0.25, 0.0)),
                n("g_crown_aegis", S, 4, "A", List.of("g_bastion", "g_juggernaut"), 5,
                        "Эгида Короны", "+6 СИЛЫ, +4% физ, +4% маг резиста",
                        new TalentEffect("attr", "str", 6.0, 0.0))
                        /* composite — см. примечание ниже */
        );
    }

    /* =============================== WARRIOR: BERSERKER =============================== */
    private static TalentTree berserker() {
        final String S = "berserker";
        return tree(S,
                n("b_rage_focus", S, 1, "A", List.of(), 1,
                        "Фокус ярости", "Ярость регенерирует на 1/с быстрее",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("b_blood_scent", S, 1, "B", List.of(), 1,
                        "Кровавый нюх", "+4 СИЛЫ постоянно",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("b_frenzy", S, 2, "A", List.of("b_rage_focus"), 2,
                        "Неистовство", "«Берсеркерганг»: +20% коэффициента",
                        new TalentEffect("kit_mult", "berserkergang", 0.20, 0.0)),
                n("b_reckless", S, 2, "A", List.of("b_rage_focus"), 2,
                        "Безрассудство", "«Удар Тира»: +10 к базе",
                        new TalentEffect("kit_base", "tyr_strike", 10.0, 0.0)),
                n("b_fenrir_howl", S, 2, "B", List.of("b_blood_scent"), 2,
                        "Вой Фенрира", "«Кровь Фенрира»: +10 к базе",
                        new TalentEffect("kit_base", "fenrir_blood", 10.0, 0.0)),
                n("b_wound_fury", S, 2, "B", List.of("b_blood_scent"), 2,
                        "Ярость ран", "Пассив «Казнь»: +4% шанса",
                        new TalentEffect("proc", "execute_passive", 0.04, 0.0)),
                n("b_rage_storm", S, 3, "A", List.of("b_frenzy", "b_reckless"), 3,
                        "Шторм ярости", "«Удар Тира»: +25% коэффициента",
                        new TalentEffect("kit_mult", "tyr_strike", 0.25, 0.0)),
                n("b_blood_pact", S, 3, "B", List.of("b_fenrir_howl", "b_wound_fury"), 3,
                        "Пакт крови", "+5% физрезиста, реген ярости +1/с",
                        new TalentEffect("resist", "phys", 5.0, 0.0)),
                n("b_ragnarok_herald", S, 4, "A", List.of("b_rage_storm", "b_blood_pact"), 5,
                        "Вестник Рагнарёка", "«Рагнарёк»: +30% коэф., −15% кулдауна",
                        new TalentEffect("kit_mult", "ragnarok", 0.30, 0.0))
        );
    }

    /* =============================== HUNTER: MARKSMAN (≡ LIQUIDATOR из дизайна) =============================== */
    private static TalentTree marksman() {
        final String S = "marksman";
        return tree(S,
                n("l_true_aim", S, 1, "A", List.of(), 1,
                        "Верный прицел", "Пассив «Хищник»: +4% шанса",
                        new TalentEffect("proc", "predator", 0.04, 0.0)),
                n("l_light_step", S, 1, "B", List.of(), 1,
                        "Лёгкий шаг", "+4 ЛОВКОСТИ постоянно",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("l_pierce", S, 2, "A", List.of("l_true_aim"), 2,
                        "Бронебойность", "«Пронзающий выстрел»: +10 к базе",
                        new TalentEffect("kit_base", "piercing_shot", 10.0, 0.0)),
                n("l_mark_deep", S, 2, "A", List.of("l_true_aim"), 2,
                        "Глубокая метка", "«Метка Волка»: +20% коэффициента",
                        new TalentEffect("kit_mult", "wolf_mark", 0.20, 0.0)),
                n("l_quick_hands", S, 2, "B", List.of("l_light_step"), 2,
                        "Быстрые руки", "«Пронзающий выстрел»: −15% кулдауна",
                        new TalentEffect("cd", "piercing_shot", 0.15, 0.0)),
                n("l_fan_master", S, 2, "B", List.of("l_light_step"), 2,
                        "Мастер веера", "«Веер стрел»: +4 к базе каждой стрелы",
                        new TalentEffect("kit_base", "arrow_fan", 4.0, 0.0)),
                n("l_headshot", S, 3, "A", List.of("l_pierce", "l_mark_deep"), 3,
                        "Выстрел в голову", "«Пронзающий выстрел»: +25% коэффициента",
                        new TalentEffect("kit_mult", "piercing_shot", 0.25, 0.0)),
                n("l_storm_arrows", S, 3, "B", List.of("l_quick_hands", "l_fan_master"), 3,
                        "Шторм стрел", "«Дождь стрел»: −20% кулдауна",
                        new TalentEffect("cd", "arrow_rain", 0.20, 0.0)),
                n("l_execution_protocol", S, 4, "A", List.of("l_headshot", "l_storm_arrows"), 5,
                        "Протокол ликвидации", "«Дождь стрел»: +30% коэф.; «Хищник» +4%",
                        new TalentEffect("kit_mult", "arrow_rain", 0.30, 0.0))
        );
    }

    /* =============================== HUNTER: TRACKER (≡ TRAPPER из дизайна) =============================== */
    private static TalentTree tracker() {
        final String S = "tracker";
        return tree(S,
                n("t_snare_wire", S, 1, "A", List.of(), 1,
                        "Силки", "«Метка Волка»: +6 к базе (контроль-часть)",
                        new TalentEffect("kit_base", "wolf_mark", 6.0, 0.0)),
                n("t_campcraft", S, 1, "B", List.of(), 1,
                        "Лагерный навык", "Концентрация регенерирует на 1/с быстрее",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("t_rain_setup", S, 2, "A", List.of("t_snare_wire"), 2,
                        "Дождь из засады", "«Дождь стрел»: +8 к базе",
                        new TalentEffect("kit_base", "arrow_rain", 8.0, 0.0)),
                n("t_terrain", S, 2, "A", List.of("t_snare_wire"), 2,
                        "Чтение местности", "+3% уклонения",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("t_survivor", S, 2, "B", List.of("t_campcraft"), 2,
                        "Выживальщик", "Концентрация регенерирует ещё на 1/с быстрее",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("t_swallow_reserve", S, 2, "B", List.of("t_campcraft"), 2,
                        "Запас зелий", "«Ласточка»: −20% кулдауна",
                        new TalentEffect("cd", "swallow", 0.20, 0.0)),
                n("t_master_trapper", S, 3, "A", List.of("t_rain_setup", "t_terrain"), 3,
                        "Мастер силков", "«Метка Волка»: +25% коэф., −15% кулдауна",
                        new TalentEffect("kit_mult", "wolf_mark", 0.25, 0.0)),
                n("t_wild_guard", S, 3, "B", List.of("t_survivor", "t_swallow_reserve"), 3,
                        "Дикая стража", "+5% физрезиста",
                        new TalentEffect("resist", "phys", 5.0, 0.0)),
                n("t_apex_hunter", S, 4, "A", List.of("t_master_trapper", "t_wild_guard"), 5,
                        "Верховный охотник", "«Веер стрел»: +30% коэф., +3% уклонения",
                        new TalentEffect("kit_mult", "arrow_fan", 0.30, 0.0))
        );
    }

    /* =============================== PRIEST: LIGHTBEARER (≡ ORACLE) =============================== */
    private static TalentTree lightbearer() {
        final String S = "lightbearer";
        return tree(S,
                n("o_ward", S, 1, "A", List.of(), 1,
                        "Оберег", "+4% магрезиста",
                        new TalentEffect("resist", "magic", 4.0, 0.0)),
                n("o_insight", S, 1, "B", List.of(), 1,
                        "Прозорливость", "+4 ИНТЕЛЛЕКТА",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("o_faith_wall", S, 2, "A", List.of("o_ward"), 2,
                        "Стена веры", "«Эгида Веры»: +5 к базе",
                        new TalentEffect("kit_base", "aegis_faith", 5.0, 0.0)),
                n("o_sanctuary_echo", S, 2, "A", List.of("o_ward"), 2,
                        "Эхо убежища", "«Эгида Веры»: −15% кулдауна",
                        new TalentEffect("cd", "aegis_faith", 0.15, 0.0)),
                n("o_tear_focus", S, 2, "B", List.of("o_insight"), 2,
                        "Сосредоточие слезы", "«Слеза Святой»: +8 к базе",
                        new TalentEffect("kit_base", "saint_tear", 8.0, 0.0)),
                n("o_word_power", S, 2, "B", List.of("o_insight"), 2,
                        "Сила слова", "«Слово Жизни»: +20% коэффициента",
                        new TalentEffect("kit_mult", "word_of_life", 0.20, 0.0)),
                n("o_divine_shell", S, 3, "A", List.of("o_faith_wall", "o_sanctuary_echo"), 3,
                        "Божественный покров", "+4% физ, +4% маг резиста",
                        new TalentEffect("resist", "both", 4.0, 4.0)),
                n("o_prophecy", S, 3, "B", List.of("o_tear_focus", "o_word_power"), 3,
                        "Пророчество", "«Круг Элизия»: +25% коэффициента",
                        new TalentEffect("kit_mult", "circle_elysium", 0.25, 0.0)),
                n("o_voice_of_light", S, 4, "A", List.of("o_divine_shell", "o_prophecy"), 5,
                        "Голос Света", "«Слово Жизни»: +30% коэф., реген Света +1/с",
                        new TalentEffect("kit_mult", "word_of_life", 0.30, 0.0))
        );
    }

    /* =============================== PRIEST: SHADOWWEAVER (≡ INQUISITOR) =============================== */
    private static TalentTree shadowweaver() {
        final String S = "shadowweaver";
        return tree(S,
                n("i_zeal", S, 1, "A", List.of(), 1,
                        "Рвение", "«Кара Небес»: +8 к базе",
                        new TalentEffect("kit_base", "wrath_heaven", 8.0, 0.0)),
                n("i_faith_armor", S, 1, "B", List.of(), 1,
                        "Доспех веры", "+4% физрезиста",
                        new TalentEffect("resist", "phys", 4.0, 0.0)),
                n("i_brand", S, 2, "A", List.of("i_zeal"), 2,
                        "Клеймо еретика", "«Кара Небес»: +20% коэффициента",
                        new TalentEffect("kit_mult", "wrath_heaven", 0.20, 0.0)),
                n("i_smite_drill", S, 2, "A", List.of("i_zeal"), 2,
                        "Школа кары", "«Кара Небес»: −15% кулдауна",
                        new TalentEffect("cd", "wrath_heaven", 0.15, 0.0)),
                n("i_unbreakable", S, 2, "B", List.of("i_faith_armor"), 2,
                        "Несокрушимость", "+4 СИЛЫ",
                        new TalentEffect("attr", "str", 4.0, 0.0)),
                n("i_martyr_tear", S, 2, "B", List.of("i_faith_armor"), 2,
                        "Слеза мученика", "«Слеза Святой»: +6 к базе",
                        new TalentEffect("kit_base", "saint_tear", 6.0, 0.0)),
                n("i_holy_wrath", S, 3, "A", List.of("i_brand", "i_smite_drill"), 3,
                        "Святая ярость", "«Кара Небес»: +25% коэффициента",
                        new TalentEffect("kit_mult", "wrath_heaven", 0.25, 0.0)),
                n("i_confessor", S, 3, "B", List.of("i_unbreakable", "i_martyr_tear"), 3,
                        "Исповедник", "+6% магрезиста",
                        new TalentEffect("resist", "magic", 6.0, 0.0)),
                n("i_hand_of_justice", S, 4, "A", List.of("i_holy_wrath", "i_confessor"), 5,
                        "Десница правосудия", "«Кара Небес»: +30% коэф., «Благодать» +5%",
                        new TalentEffect("kit_mult", "wrath_heaven", 0.30, 0.0))
        );
    }

    /* =============================== MAGE: ARCANE (≡ ELEMENTALIST) =============================== */
    private static TalentTree arcane() {
        final String S = "arcane";
        return tree(S,
                n("e_ember", S, 1, "A", List.of(), 1,
                        "Тлеющий уголь", "«Огонь Прометея»: +6 к базе",
                        new TalentEffect("kit_base", "fire_prometheus", 6.0, 0.0)),
                n("e_frost_vein", S, 1, "B", List.of(), 1,
                        "Морозная жилка", "+4 ИНТЕЛЛЕКТА",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("e_wildfire", S, 2, "A", List.of("e_ember"), 2,
                        "Дикий огонь", "«Огонь Прометея»: +20% коэффициента",
                        new TalentEffect("kit_mult", "fire_prometheus", 0.20, 0.0)),
                n("e_zeus_channel", S, 2, "A", List.of("e_ember"), 2,
                        "Канал Зевса", "«Гнев Зевса»: +10 к базе",
                        new TalentEffect("kit_base", "zeus_wrath", 10.0, 0.0)),
                n("e_deep_freeze", S, 2, "B", List.of("e_frost_vein"), 2,
                        "Глубокая заморозка", "«Дыхание Борея»: +20% коэффициента",
                        new TalentEffect("kit_mult", "boreas_breath", 0.20, 0.0)),
                n("e_mana_flow", S, 2, "B", List.of("e_frost_vein"), 2,
                        "Поток маны", "Реген маны сдвигается на +1 тир",
                        new TalentEffect("regen", "resource", 1.0, 0.0)),
                n("e_pyromaster", S, 3, "A", List.of("e_wildfire", "e_zeus_channel"), 3,
                        "Пиромант-мастер", "«Огонь Прометея»: +25% коэффициента",
                        new TalentEffect("kit_mult", "fire_prometheus", 0.25, 0.0)),
                n("e_absolute_zero", S, 3, "B", List.of("e_deep_freeze", "e_mana_flow"), 3,
                        "Абсолютный ноль", "«Дыхание Борея»: −20% кулдауна",
                        new TalentEffect("cd", "boreas_breath", 0.20, 0.0)),
                n("e_avatar_of_storms", S, 4, "A", List.of("e_pyromaster", "e_absolute_zero"), 5,
                        "Аватар бурь", "«Гнев Зевса»: +30% коэф., −15% кулдауна",
                        new TalentEffect("kit_mult", "zeus_wrath", 0.30, 0.0))
        );
    }

    /* =============================== MAGE: FROST (НОВОЕ дерево — «Контроль льдом») =============================== */
    private static TalentTree frost() {
        final String S = "frost";
        return tree(S,
                n("f_permafrost", S, 1, "A", List.of(), 1,
                        "Вечная мерзлота", "«Дыхание Борея»: +5 к базе",
                        new TalentEffect("kit_base", "boreas_breath", 5.0, 0.0)),
                n("f_crystal_blood", S, 1, "B", List.of(), 1,
                        "Кристаллическая кровь", "+4 ИНТЕЛЛЕКТА",
                        new TalentEffect("attr", "int", 4.0, 0.0)),
                n("f_glacial_heart", S, 2, "A", List.of("f_permafrost"), 2,
                        "Ледяное сердце", "+5% магрезиста",
                        new TalentEffect("resist", "magic", 5.0, 0.0)),
                n("f_frozen_shroud", S, 2, "A", List.of("f_permafrost"), 2,
                        "Застывший саван", "«Эгида Афины»: +5 к базе",
                        new TalentEffect("kit_base", "athena_aegis", 5.0, 0.0)),
                n("f_ice_tomb", S, 2, "B", List.of("f_crystal_blood"), 2,
                        "Ледяная гробница", "«Дыхание Борея»: +20% коэффициента",
                        new TalentEffect("kit_mult", "boreas_breath", 0.20, 0.0)),
                n("f_cold_snap", S, 2, "B", List.of("f_crystal_blood"), 2,
                        "Резкое похолодание", "«Дыхание Борея»: −15% кулдауна",
                        new TalentEffect("cd", "boreas_breath", 0.15, 0.0)),
                n("f_eternal_winter", S, 3, "A", List.of("f_glacial_heart", "f_frozen_shroud"), 3,
                        "Вечная зима", "«Дыхание Борея»: +25% коэф., +4% маг резиста",
                        new TalentEffect("kit_mult", "boreas_breath", 0.25, 0.0)),
                n("f_frostbite", S, 3, "B", List.of("f_ice_tomb", "f_cold_snap"), 3,
                        "Обморожение", "«Дыхание Борея»: +8 к базе, −10% кулдауна",
                        new TalentEffect("kit_base", "boreas_breath", 8.0, 0.0)),
                n("f_avatar_of_winter", S, 4, "A", List.of("f_eternal_winter", "f_frostbite"), 5,
                        "Аватар зимы", "«Дыхание Борея»: +30% коэф., −20% кулдауна",
                        new TalentEffect("kit_mult", "boreas_breath", 0.30, 0.0))
        );
    }

    /* =============================== ROGUE: LIQUIDATOR (≡ ASSASSIN) =============================== */
    private static TalentTree liquidator() {
        final String S = "liquidator";
        return tree(S,
                n("a_toxin_mix", S, 1, "A", List.of(), 1,
                        "Смесь токсинов", "«Яд Борджа»: +5 к базе",
                        new TalentEffect("kit_base", "borgia_poison", 5.0, 0.0)),
                n("a_shadow_drill", S, 1, "B", List.of(), 1,
                        "Школа тени", "+4 ЛОВКОСТИ",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("a_deadly_poison", S, 2, "A", List.of("a_toxin_mix"), 2,
                        "Смертельный яд", "«Яд Борджа»: +20% коэффициента",
                        new TalentEffect("kit_mult", "borgia_poison", 0.20, 0.0)),
                n("a_strangle_hold", S, 2, "A", List.of("a_toxin_mix"), 2,
                        "Мёртвая хватка", "«Удушение палача»: +8 к базе",
                        new TalentEffect("kit_base", "strangle", 8.0, 0.0)),
                n("a_backstab_master", S, 2, "B", List.of("a_shadow_drill"), 2,
                        "Мастер спины", "Пассив «Садизм»: +2 к бонусу",
                        new TalentEffect("proc", "sadism", 2.0, 0.0)),
                n("a_cloak_reserve", S, 2, "B", List.of("a_shadow_drill"), 2,
                        "Запас плаща", "«Плащ теней»: −20% кулдауна",
                        new TalentEffect("cd", "shadow_cloak", 0.20, 0.0)),
                n("a_venom_master", S, 3, "A", List.of("a_deadly_poison", "a_strangle_hold"), 3,
                        "Мастер ядов", "«Яд Борджа»: +25% коэф., «Отравл. клинки» +5%",
                        new TalentEffect("kit_mult", "borgia_poison", 0.25, 0.0)),
                n("a_executioner", S, 3, "B", List.of("a_backstab_master", "a_cloak_reserve"), 3,
                        "Палач", "«Удушение палача»: +25% коэффициента",
                        new TalentEffect("kit_mult", "strangle", 0.25, 0.0)),
                n("a_death_mark", S, 4, "A", List.of("a_venom_master", "a_executioner"), 5,
                        "Метка смерти", "«Удушение палача»: +30% коэф., +3% уклонения",
                        new TalentEffect("kit_mult", "strangle", 0.30, 0.0))
        );
    }

    /* =============================== ROGUE: TRICKSTER =============================== */
    private static TalentTree trickster() {
        final String S = "trickster";
        return tree(S,
                n("t2_reflex", S, 1, "A", List.of(), 1,
                        "Рефлексы", "+3% уклонения",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("t2_light_hands", S, 1, "B", List.of(), 1,
                        "Лёгкие руки", "+4 ЛОВКОСТИ",
                        new TalentEffect("attr", "agi", 4.0, 0.0)),
                n("t2_afterimage", S, 2, "A", List.of("t2_reflex"), 2,
                        "Послеобраз", "+3% уклонения (итого +6)",
                        new TalentEffect("avoid", "dodge", 3.0, 0.0)),
                n("t2_parry_trick", S, 2, "A", List.of("t2_reflex"), 2,
                        "Трюк парирования", "+3% парирования",
                        new TalentEffect("avoid", "parry", 3.0, 0.0)),
                n("t2_blade_tempo", S, 2, "B", List.of("t2_light_hands"), 2,
                        "Темп клинков", "«Веер клинков»: −15% кулдауна",
                        new TalentEffect("cd", "blade_fan", 0.15, 0.0)),
                n("t2_fan_edge", S, 2, "B", List.of("t2_light_hands"), 2,
                        "Кромка веера", "«Веер клинков»: +6 к базе",
                        new TalentEffect("kit_base", "blade_fan", 6.0, 0.0)),
                n("t2_untouchable", S, 3, "A", List.of("t2_afterimage", "t2_parry_trick"), 3,
                        "Неприкасаемый", "+4% уклонения (итого +10), +3% физрезиста",
                        new TalentEffect("avoid", "dodge", 4.0, 0.0)),
                n("t2_blade_storm", S, 3, "B", List.of("t2_blade_tempo", "t2_fan_edge"), 3,
                        "Шторм клинков", "«Веер клинков»: +25% коэффициента",
                        new TalentEffect("kit_mult", "blade_fan", 0.25, 0.0)),
                n("t2_dance_of_a_thousand_cuts", S, 4, "A",
                        List.of("t2_untouchable", "t2_blade_storm"), 5,
                        "Танец тысячи порезов", "«Веер клинков»: +30% коэф., dodge proc",
                        new TalentEffect("kit_mult", "blade_fan", 0.30, 0.0))
        );
    }
}
