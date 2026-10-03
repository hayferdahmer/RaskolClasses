// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Воина. Б1: «Оружие» (arms) — эталон дизайн-дока, раздел 4:
 * 20 узлов, 6 рядов, ёмкость 51 ранг (>46 → дерево незакрываемое),
 * ранговые пререквизиты, unlock-узлы fenrir_blood/whirlwind_slash/mortal_strike,
 * ульт ragnarok (ряд 6, гейт 30 очков в дереве, пререк executioner 2).
 * fury/guard приходят в 1.14.4.
 */
public final class WarriorTrees {

    private WarriorTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("arms", arms());
    }

    private static Spec2Node n(String id, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, "arms", row, col, maxRank, prereqs, type, name, lore, effect);
    }

    private static Spec2Tree arms() {
        return new Spec2Tree("arms", List.of(
                // Ряд 1 (гейт 0)
                n("arms_training", 1, 1, 5, Map.of(), "passive_stat",
                        "Школа оружия", "+2% физ-урона способностей за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 2.0)),
                n("hardened_skin", 1, 2, 4, Map.of(), "passive_stat",
                        "Закалённая кожа", "+2% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 2.0)),
                n("precision", 1, 3, 5, Map.of(), "passive_stat",
                        "Точность", "+1% крита мили за ранг",
                        Spec2Effect.of("crit_melee_pct", "self", 1.0)),
                n("field_medkit", 1, 4, 3, Map.of(), "passive_stat",
                        "Полевой лечебный набор", "+3% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 3.0)),
                // Ряд 2 (гейт 5)
                n("deep_wounds", 2, 1, 3, Map.of(), "passive_proc",
                        "Глубокие раны", "крит накладывает bleed, dps +0.5 за ранг",
                        Spec2Effect.of("proc_bleed_on_crit", "bleed", 0.5)),
                n("sword_and_board", 2, 2, 3, Map.of(), "passive_stat",
                        "Меч и щит", "+3% парирования за ранг",
                        Spec2Effect.of("avoid", "parry", 3.0)),
                n("fenrir_blood", 2, 3, 1, Map.of(), "unlock_ability",
                        "Кровь Фенрира", "открывает способность старого кита (слот 4)",
                        Spec2Effect.of("unlock_ability", "fenrir_blood", 1.0)),
                // Ряд 3 (гейт 10)
                n("rend_master", 3, 1, 3, Map.of("deep_wounds", 1), "enhance_ability",
                        "Мастер рваных ран", "bleed: +1 с длительности за ранг",
                        Spec2Effect.of("dot_dur", "bleed", 1.0)),
                n("whirlwind_slash", 3, 2, 1, Map.of(), "unlock_ability",
                        "Вихревой удар", "НОВАЯ: секторный физ-урон 60°, КД 12 с",
                        Spec2Effect.of("unlock_ability", "whirlwind_slash", 1.0)),
                n("war_acumen", 3, 3, 3, Map.of(), "passive_stat",
                        "Воинская смекалка", "+2% WP за ранг",
                        Spec2Effect.of("wp_pct", "self", 2.0)),
                n("execute_drill", 3, 4, 2, Map.of(), "enhance_ability",
                        "Отработка казни", "tyr_strike: +6% base за ранг",
                        Spec2Effect.of("kit_base", "tyr_strike", 6.0)),
                // Ряд 4 (гейт 15)
                n("bloodletting", 4, 1, 3, Map.of("rend_master", 2), "passive_stat",
                        "Кровопускание", "bleed-стеки +1 за ранг (кап dots.bleed растёт)",
                        Spec2Effect.of("dot_stacks", "bleed", 1.0)),
                n("shield_wall_echo", 4, 2, 2, Map.of(), "enhance_ability",
                        "Эхо стены щитов", "balder_skin: +1 с за ранг",
                        Spec2Effect.of("kit_dur", "balder_skin", 1.0)),
                n("mortal_strike", 4, 3, 1, Map.of("whirlwind_slash", 1), "unlock_ability",
                        "Смертельный удар", "НОВАЯ: физ-урон + анти-хил 3 с",
                        Spec2Effect.of("unlock_ability", "mortal_strike", 1.0)),
                n("second_wind", 4, 4, 2, Map.of(), "passive_proc",
                        "Второе дыхание", "10% за ранг: при HP<35% мгновенный хил 8%",
                        Spec2Effect.of("proc_second_wind", "self", 0.10)),
                // Ряд 5 (гейт 20)
                n("executioner", 5, 1, 3, Map.of("mortal_strike", 1), "enhance_ability",
                        "Палач", "execute-порог +2% за ранг (25→31%)",
                        Spec2Effect.of("exec_threshold_pct", "self", 2.0)),
                n("titan_grip", 5, 2, 3, Map.of(), "passive_stat",
                        "Хватка титана", "pen_phys +5% за ранг (PenTraits, источник spec2)",
                        Spec2Effect.of("pen_phys_pct", "self", 5.0)),
                n("berserkers_echo", 5, 3, 2, Map.of(), "enhance_ability",
                        "Эхо берсерка", "berserkergang: −7 с КД за ранг",
                        Spec2Effect.of("kit_cd", "berserkergang", 7.0)),
                n("battle_trance", 5, 4, 1, Map.of(), "passive_proc",
                        "Боевой транс", "каждый 3-й удар: +5 ярости",
                        Spec2Effect.of("proc_trance", "resource", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("ragnarok", 6, 2, 1, Map.of("executioner", 2), "ultimate",
                        "Рагнарёк", "ульт: ×3.5 execute + bleed-взрыв (перенос старого кита)",
                        Spec2Effect.of("unlock_ability", "ragnarok", 1.0))
        ));
    }
}
