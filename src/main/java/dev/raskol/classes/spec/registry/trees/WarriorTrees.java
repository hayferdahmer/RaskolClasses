// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Воина (3 спеки).
 * arms (Оружие): FIGHTER, PHYSICAL, bleed — эталон Б1.
 * fury (Неистовство): FIGHTER, PHYSICAL+bleed, темп — unlock bloodthirst/rampage,
 *         ульт bladedstorm (AoE-вихрь с иммунитетом к ROOT).
 * guard (Защита): TANK, PHYSICAL/HOLY — unlock shield_bash (STUN), taunt (ROOT-агро),
 *         ульт last_stand (щит-пул 25% maxHP на 8 с).
 */
public final class WarriorTrees {

    private WarriorTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("arms", arms());
        into.put("fury", fury());
        into.put("guard", guard());
    }

    private static Spec2Node n(String id, String treeId, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, treeId, row, col, maxRank, prereqs, type, name, lore, effect);
    }

    /* ============ ВОИН · ОРУЖИЕ (arms) — ёмкость 51 ============ */
    private static Spec2Tree arms() {
        final String T = "arms";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("arms_training", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Школа оружия", "+2% физ-урона способностей за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 2.0)),
                n("hardened_skin", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Закалённая кожа", "+2% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 2.0)),
                n("precision", T, 1, 3, 5, Map.of(), "passive_stat",
                        "Точность", "+1% крита мили за ранг",
                        Spec2Effect.of("crit_melee_pct", "self", 1.0)),
                n("field_medkit", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Полевой лечебный набор", "+3% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 3.0)),
                // Ряд 2 (гейт 5)
                n("deep_wounds", T, 2, 1, 3, Map.of(), "passive_proc",
                        "Глубокие раны", "крит накладывает bleed, dps +0.5 за ранг",
                        Spec2Effect.of("proc_bleed_on_crit", "bleed", 0.5)),
                n("sword_and_board", T, 2, 2, 3, Map.of(), "passive_stat",
                        "Меч и щит", "+3% парирования за ранг",
                        Spec2Effect.of("avoid", "parry", 3.0)),
                n("fenrir_blood", T, 2, 3, 1, Map.of(), "unlock_ability",
                        "Кровь Фенрира", "открывает способность старого кита (слот 4)",
                        Spec2Effect.of("unlock_ability", "fenrir_blood", 1.0)),
                // Ряд 3 (гейт 10)
                n("rend_master", T, 3, 1, 3, Map.of("deep_wounds", 1), "enhance_ability",
                        "Мастер рваных ран", "bleed: +1 с длительности за ранг",
                        Spec2Effect.of("dot_dur", "bleed", 1.0)),
                n("whirlwind_slash", T, 3, 2, 1, Map.of(), "unlock_ability",
                        "Вихревой удар", "НОВАЯ: секторный физ-урон 60°, КД 12 с",
                        Spec2Effect.of("unlock_ability", "whirlwind_slash", 1.0)),
                n("war_acumen", T, 3, 3, 3, Map.of(), "passive_stat",
                        "Воинская смекалка", "+2% WP за ранг",
                        Spec2Effect.of("wp_pct", "self", 2.0)),
                n("execute_drill", T, 3, 4, 2, Map.of(), "enhance_ability",
                        "Отработка казни", "tyr_strike: +6% base за ранг",
                        Spec2Effect.of("kit_base", "tyr_strike", 6.0)),
                // Ряд 4 (гейт 15)
                n("bloodletting", T, 4, 1, 3, Map.of("rend_master", 2), "passive_stat",
                        "Кровопускание", "bleed-стеки +1 за ранг (кап dots.bleed растёт)",
                        Spec2Effect.of("dot_stacks", "bleed", 1.0)),
                n("shield_wall_echo", T, 4, 2, 2, Map.of(), "enhance_ability",
                        "Эхо стены щитов", "balder_skin: +1 с за ранг",
                        Spec2Effect.of("kit_dur", "balder_skin", 1.0)),
                n("mortal_strike", T, 4, 3, 1, Map.of("whirlwind_slash", 1), "unlock_ability",
                        "Смертельный удар", "НОВАЯ: физ-урон + анти-хил 3 с",
                        Spec2Effect.of("unlock_ability", "mortal_strike", 1.0)),
                n("second_wind", T, 4, 4, 2, Map.of(), "passive_proc",
                        "Второе дыхание", "10% за ранг: при HP<35% мгновенный хил 8%",
                        Spec2Effect.of("proc_second_wind", "self", 0.10)),
                // Ряд 5 (гейт 20)
                n("executioner", T, 5, 1, 3, Map.of("mortal_strike", 1), "enhance_ability",
                        "Палач", "execute-порог +2% за ранг (25→31%)",
                        Spec2Effect.of("exec_threshold_pct", "self", 2.0)),
                n("titan_grip", T, 5, 2, 3, Map.of(), "passive_stat",
                        "Хватка титана", "pen_phys +5% за ранг (PenTraits, источник spec2)",
                        Spec2Effect.of("pen_phys_pct", "self", 5.0)),
                n("berserkers_echo", T, 5, 3, 2, Map.of(), "enhance_ability",
                        "Эхо берсерка", "berserkergang: −7 с КД за ранг",
                        Spec2Effect.of("kit_cd", "berserkergang", 7.0)),
                n("battle_trance", T, 5, 4, 1, Map.of(), "passive_proc",
                        "Боевой транс", "каждый 3-й удар: +5 ярости",
                        Spec2Effect.of("proc_trance", "resource", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("ragnarok", T, 6, 2, 1, Map.of("executioner", 2), "ultimate",
                        "Рагнарёк", "ульт: ×3.5 execute + bleed-взрыв (перенос старого кита)",
                        Spec2Effect.of("unlock_ability", "ragnarok", 1.0))
        ));
    }

    /* ============ ВОИН · НЕИСТОВСТВО (fury) — ёмкость 52 ============ */
    private static Spec2Tree fury() {
        final String T = "fury";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("fury_rage_pool", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Пул ярости", "+10 макс. ярости за ранг",
                        Spec2Effect.of("resource_max", "rage", 10.0)),
                n("fury_bloodlust", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Жажда крови", "+2% физ-урона за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 2.0)),
                n("fury_unyielding", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Непреклонность", "+2% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 2.0)),
                n("fury_battle_cry", T, 1, 4, 3, Map.of(), "passive_proc",
                        "Боевой клич", "5% за ранг: при касте +3 ярости",
                        Spec2Effect.of("proc_rage_on_cast", "self", 0.05)),
                // Ряд 2 (гейт 5)
                n("fury_enrage", T, 2, 1, 3, Map.of(), "passive_proc",
                        "Ярость", "при HP<50%: +5% урона за ранг",
                        Spec2Effect.of("proc_enrage_dmg", "self", 5.0)),
                n("fury_frenzy", T, 2, 2, 3, Map.of(), "passive_stat",
                        "Безумие", "+3% скорости атаки за ранг",
                        Spec2Effect.of("attack_speed_pct", "self", 3.0)),
                n("fury_berserkers_rage", T, 2, 3, 2, Map.of(), "enhance_ability",
                        "Ярость берсерка", "berserkergang: +1 с за ранг",
                        Spec2Effect.of("kit_dur", "berserkergang", 1.0)),
                // Ряд 3 (гейт 10)
                n("fury_bloodthirst", T, 3, 1, 1, Map.of("fury_enrage", 1), "unlock_ability",
                        "Жажда крови", "НОВАЯ: физ-урон + хил 15% от урона, КД 8 с",
                        Spec2Effect.of("unlock_ability", "bloodthirst", 1.0)),
                n("fury_rampage", T, 3, 2, 1, Map.of(), "unlock_ability",
                        "Буйство", "НОВАЯ: серия из 3 ударов, каждый +10% урона, КД 15 с",
                        Spec2Effect.of("unlock_ability", "rampage", 1.0)),
                n("fury_concussive_blow", T, 3, 3, 1, Map.of(), "unlock_ability",
                        "Оглушающий удар", "НОВАЯ: STUN 1.5 с, КД 45 с (CCService 1.13.0)",
                        Spec2Effect.of("unlock_ability", "concussive_blow", 1.0)),
                n("fury_wild_strikes", T, 3, 4, 3, Map.of("fury_frenzy", 1), "passive_proc",
                        "Дикие удары", "10% за ранг: двойной удар",
                        Spec2Effect.of("proc_double_strike", "self", 0.10)),
                // Ряд 4 (гейт 15)
                n("fury_death_wish", T, 4, 1, 2, Map.of("fury_bloodthirst", 1), "enhance_ability",
                        "Жажда смерти", "bloodthirst: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "bloodthirst", 0.20)),
                n("fury_unstoppable_force", T, 4, 2, 2, Map.of("fury_rampage", 1), "enhance_ability",
                        "Неудержимая сила", "rampage: +1 удар за ранг (3→4→5)",
                        Spec2Effect.of("kit_extra_hits", "rampage", 1.0)),
                n("fury_piercing_howl", T, 4, 3, 2, Map.of("fury_concussive_blow", 1), "enhance_ability",
                        "Пронзающий вой", "concussive_blow: +0.5 с STUN за ранг",
                        Spec2Effect.of("kit_dur", "concussive_blow", 0.5)),
                n("fury_massacre", T, 4, 4, 2, Map.of("fury_wild_strikes", 1), "passive_stat",
                        "Резня", "execute-порог +3% за ранг",
                        Spec2Effect.of("exec_threshold_pct", "self", 3.0)),
                // Ряд 5 (гейт 20)
                n("fury_bloodbath", T, 5, 1, 3, Map.of("fury_death_wish", 1), "passive_proc",
                        "Кровавая баня", "bleed-урон +15% за ранг",
                        Spec2Effect.of("dot_mult", "bleed", 15.0)),
                n("fury_relentless", T, 5, 2, 3, Map.of("fury_unstoppable_force", 1), "passive_stat",
                        "Безжалостность", "cc_resist +5% за ранг",
                        Spec2Effect.of("cc_resist", "self", 5.0)),
                n("fury_berserkers_frenzy", T, 5, 3, 2, Map.of("fury_piercing_howl", 1), "enhance_ability",
                        "Бешенство берсерка", "berserkergang: +10% урона за ранг",
                        Spec2Effect.of("kit_mult", "berserkergang", 0.10)),
                n("fury_battle_hardened", T, 5, 4, 2, Map.of("fury_massacre", 1), "passive_stat",
                        "Закалённый в бою", "+4% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 4.0)),
                // Ряд 6 (гейт 30) — ульт
                n("fury_bladestorm", T, 6, 2, 1, Map.of("fury_bloodbath", 2, "fury_relentless", 2), "ultimate",
                        "Шторм клинков", "ульт: AoE-вихрь 4 с с иммунитетом к ROOT, КД 120 с",
                        Spec2Effect.of("unlock_ability", "bladestorm", 1.0))
        ));
    }

    /* ============ ВОИН · ЗАЩИТА (guard) — ёмкость 53 ============ */
    private static Spec2Tree guard() {
        final String T = "guard";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("guard_shield_mastery", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Мастерство щита", "+3% блок-шанс за ранг",
                        Spec2Effect.of("block_pct", "self", 3.0)),
                n("guard_toughness", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Стойкость", "+2% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 2.0)),
                n("guard_vigilance", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Бдительность", "+2% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 2.0)),
                n("guard_protectors_resolve", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Решимость защитника", "+3% получаемого лечения за ранг",
                        Spec2Effect.of("heal_received_pct", "self", 3.0)),
                // Ряд 2 (гейт 5)
                n("guard_shield_bash", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Удар щитом", "НОВАЯ: STUN 2 с, КД 30 с (CCService 1.13.0)",
                        Spec2Effect.of("unlock_ability", "shield_bash", 1.0)),
                n("guard_spell_reflection", T, 2, 2, 2, Map.of(), "passive_proc",
                        "Отражение заклинаний", "10% за ранг: отразить маг-урон",
                        Spec2Effect.of("proc_reflect_magic", "self", 0.10)),
                n("guard_last_stand_prep", T, 2, 3, 3, Map.of(), "passive_stat",
                        "Подготовка к последнему рубежу", "+5% maxHP за ранг",
                        Spec2Effect.of("hp_pct", "self", 5.0)),
                // Ряд 3 (гейт 10)
                n("guard_shield_wall", T, 3, 1, 2, Map.of("guard_shield_bash", 1), "enhance_ability",
                        "Стена щитов", "balder_skin: +50% эффективности за ранг",
                        Spec2Effect.of("kit_mult", "balder_skin", 0.50)),
                n("guard_vigilant_guardian", T, 3, 2, 2, Map.of(), "enhance_ability",
                        "Бдительный страж", "balder_skin: +2 с за ранг",
                        Spec2Effect.of("kit_dur", "balder_skin", 2.0)),
                n("guard_taunt", T, 3, 3, 1, Map.of(), "unlock_ability",
                        "Насмешка", "НОВАЯ: ROOT-агро на мобов 5 с, КД 20 с",
                        Spec2Effect.of("unlock_ability", "taunt", 1.0)),
                n("guard_revenge", T, 3, 4, 3, Map.of("guard_spell_reflection", 1), "passive_proc",
                        "Возмездие", "15% за ранг: при блок +10% урона след. удара",
                        Spec2Effect.of("proc_revenge", "self", 0.15)),
                // Ряд 4 (гейт 15)
                n("guard_shield_specialization", T, 4, 1, 3, Map.of("guard_shield_wall", 1), "passive_stat",
                        "Специализация щита", "+2% блок-шанс за ранг",
                        Spec2Effect.of("block_pct", "self", 2.0)),
                n("guard_spell_ward", T, 4, 2, 2, Map.of("guard_vigilant_guardian", 1), "enhance_ability",
                        "Магический оберег", "balder_skin: +25% маг-резист за ранг",
                        Spec2Effect.of("kit_resist_magic", "balder_skin", 25.0)),
                n("guard_intervene", T, 4, 3, 2, Map.of("guard_taunt", 1), "enhance_ability",
                        "Вмешательство", "taunt: +2 с за ранг",
                        Spec2Effect.of("kit_dur", "taunt", 2.0)),
                n("guard_warbringer", T, 4, 4, 2, Map.of("guard_revenge", 1), "passive_stat",
                        "Вестник войны", "cc_resist +5% за ранг",
                        Spec2Effect.of("cc_resist", "self", 5.0)),
                // Ряд 5 (гейт 20)
                n("guard_shield_slam", T, 5, 1, 3, Map.of("guard_shield_specialization", 1), "passive_proc",
                        "Таранный удар", "20% за ранг: при блок +50% урона",
                        Spec2Effect.of("proc_shield_slam", "self", 0.20)),
                n("guard_devastate", T, 5, 2, 3, Map.of("guard_spell_ward", 1), "enhance_ability",
                        "Сокрушение", "tyr_strike: +8% base за ранг",
                        Spec2Effect.of("kit_base", "tyr_strike", 8.0)),
                n("guard_safeguard", T, 5, 3, 2, Map.of("guard_intervene", 1), "passive_stat",
                        "Защита", "+5% получаемого лечения за ранг",
                        Spec2Effect.of("heal_received_pct", "self", 5.0)),
                n("guard_unbreakable", T, 5, 4, 2, Map.of("guard_warbringer", 1), "passive_stat",
                        "Несокрушимый", "+3% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 3.0)),
                // Ряд 6 (гейт 30) — ульт
                n("guard_last_stand", T, 6, 2, 1, Map.of("guard_shield_slam", 2, "guard_devastate", 2), "ultimate",
                        "Последний рубеж", "ульт: щит-пул 25% maxHP на 8 с, КД 180 с",
                        Spec2Effect.of("unlock_ability", "last_stand", 1.0))
        ));
    }
}
