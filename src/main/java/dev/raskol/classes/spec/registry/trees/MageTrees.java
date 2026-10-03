// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Мага (3 спеки), ёмкости 51/51/52 (>46 — закрыть нельзя).
 * arcane (Тайная магия): burst ARCANE, unlock arcane_missiles/counterspell(SILENCE)/
 *         presence_of_mind, ульт zeus_wrath (перенос старого кита, слот 5).
 * fire (Огонь): burst FIRE + DoT burning, unlock scorch/flamestrike/combustion,
 *         ульт pyroblast (новый: большой burning).
 * frost (Лёд): контроль FROST + DoT chilled, unlock frostbolt/blizzard/ice_barrier,
 *         ульт ice_lance_shatter (×3 по chilled-цели).
 */
public final class MageTrees {

    private MageTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("arcane", arcane());
        into.put("fire", fire());
        into.put("frost", frost());
    }

    private static Spec2Node n(String id, String treeId, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, treeId, row, col, maxRank, prereqs, type, name, lore, effect);
    }

    /* ============ МАГ · ТАЙНАЯ МАГИЯ (arcane) — ёмкость 51 ============ */
    private static Spec2Tree arcane() {
        final String T = "arcane";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("arc_attunement", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Тайная настройка", "+2% маг-урона способностей за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                n("arc_mind", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Тайный разум", "+4 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 4.0)),
                n("arc_mana_adept", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Адепт маны", "+1 маны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("arc_barrier", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Тайный барьер", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                // Ряд 2 (гейт 5)
                n("arc_missiles", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Чародейские стрелы", "НОВАЯ: 3 залпа маг-урона, КД 8 с",
                        Spec2Effect.of("unlock_ability", "arcane_missiles", 1.0)),
                n("arc_power_surge", T, 2, 2, 3, Map.of("arc_attunement", 2), "passive_stat",
                        "Всплеск силы", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("arc_mana_gem", T, 2, 3, 3, Map.of(), "passive_stat",
                        "Самоцвет маны", "+1 маны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("arc_focus", T, 2, 4, 3, Map.of("arc_mind", 2), "passive_stat",
                        "Тайная сосредоточенность", "pen_magic +4% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 4.0)),
                // Ряд 3 (гейт 10)
                n("arc_missiles_enh", T, 3, 1, 3, Map.of("arc_missiles", 1), "enhance_ability",
                        "Усиленные стрелы", "arcane_missiles: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "arcane_missiles", 0.15)),
                n("arc_stability", T, 3, 2, 2, Map.of("arc_power_surge", 1), "passive_stat",
                        "Тайная стабильность", "+2% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 2.0)),
                n("arc_mind_mastery", T, 3, 3, 3, Map.of("arc_mana_gem", 1), "passive_stat",
                        "Владычество разума", "+3 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 3.0)),
                n("arc_spellflow", T, 3, 4, 2, Map.of("arc_focus", 1), "enhance_ability",
                        "Поток заклинаний", "fire_prometheus: −10% кулдауна за ранг",
                        Spec2Effect.of("cd", "fire_prometheus", 0.10)),
                // Ряд 4 (гейт 15)
                n("arc_counterspell", T, 4, 1, 1, Map.of("arc_missiles_enh", 1), "unlock_ability",
                        "Контрзаклинание", "НОВАЯ: SILENCE 3 с по цели, КД 30 с (CCService 1.13.0)",
                        Spec2Effect.of("unlock_ability", "counterspell", 1.0)),
                n("arc_potency", T, 4, 2, 2, Map.of("arc_stability", 1), "passive_stat",
                        "Тайная мощь", "+3% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 3.0)),
                n("arc_ward", T, 4, 3, 2, Map.of("arc_mind_mastery", 1), "passive_stat",
                        "Тайный оберег", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                n("arc_improved_counter", T, 4, 4, 2, Map.of("arc_spellflow", 1), "enhance_ability",
                        "Улучшенное контрзаклинание", "counterspell: −5 с КД за ранг",
                        Spec2Effect.of("kit_cd", "counterspell", 5.0)),
                // Ряд 5 (гейт 20)
                n("arc_presence_of_mind", T, 5, 1, 1, Map.of("arc_counterspell", 1), "unlock_ability",
                        "Ясность ума", "НОВАЯ: след. каст без стоимости и КД-трата ×0.5, КД 90 с",
                        Spec2Effect.of("unlock_ability", "presence_of_mind", 1.0)),
                n("arc_empowerment", T, 5, 2, 3, Map.of("arc_potency", 1), "passive_stat",
                        "Тайное усиление", "+4% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 4.0)),
                n("arc_spell_mastery", T, 5, 3, 3, Map.of("arc_ward", 1), "passive_stat",
                        "Мастерство заклинаний", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("arc_zeus_wrath", T, 6, 2, 1,
                        Map.of("arc_presence_of_mind", 1, "arc_spell_mastery", 2), "ultimate",
                        "Гнев Зевса", "ульт: перенос старого кита (слот 5), сцена молнии + burning",
                        Spec2Effect.of("unlock_ability", "zeus_wrath", 1.0))
        ));
    }

    /* ============ МАГ · ОГОНЬ (fire) — ёмкость 51 ============ */
    private static Spec2Tree fire() {
        final String T = "fire";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("fi_power", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Сила пламени", "+2% маг-урона способностей за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                n("fi_molten_skin", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Расплавленная кожа", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                n("fi_ignite_training", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Школа воспламенения", "+10% урона burning за ранг",
                        Spec2Effect.of("dot_mult", "burning", 10.0)),
                n("fi_thermal_void", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Термальная пустота", "+3 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 3.0)),
                // Ряд 2 (гейт 5)
                n("fi_scorch", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Ожог", "НОВАЯ: маг-урон + стек burning, КД 4 с",
                        Spec2Effect.of("unlock_ability", "scorch", 1.0)),
                n("fi_burning_soul", T, 2, 2, 3, Map.of("fi_ignite_training", 2), "passive_stat",
                        "Пламенная душа", "+1 с длительности burning за ранг",
                        Spec2Effect.of("dot_dur", "burning", 1.0)),
                n("fi_firestarter", T, 2, 3, 2, Map.of(), "passive_proc",
                        "Поджигатель", "15% за ранг: crit продлевает burning на 1 с",
                        Spec2Effect.of("proc_burning_extend", "self", 0.15)),
                n("fi_flame_barrier", T, 2, 4, 3, Map.of("fi_molten_skin", 2), "passive_stat",
                        "Пламенный барьер", "+3% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 3.0)),
                // Ряд 3 (гейт 10)
                n("fi_scorch_enh", T, 3, 1, 3, Map.of("fi_scorch", 1), "enhance_ability",
                        "Усиленный ожог", "scorch: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "scorch", 0.15)),
                n("fi_blaze", T, 3, 2, 2, Map.of("fi_burning_soul", 1), "passive_stat",
                        "Пламя", "+10% урона burning за ранг",
                        Spec2Effect.of("dot_mult", "burning", 10.0)),
                n("fi_pyromaniac", T, 3, 3, 3, Map.of("fi_firestarter", 1), "passive_stat",
                        "Пироман", "+1 маны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("fi_critical_mass", T, 3, 4, 2, Map.of("fi_flame_barrier", 1), "passive_stat",
                        "Критическая масса", "+3% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 3.0)),
                // Ряд 4 (гейт 15)
                n("fi_flamestrike", T, 4, 1, 1, Map.of("fi_scorch_enh", 1), "unlock_ability",
                        "Огненный столб", "НОВАЯ: AoE радиус 4 + burning всем, КД 20 с",
                        Spec2Effect.of("unlock_ability", "flamestrike", 1.0)),
                n("fi_combustion_prep", T, 4, 2, 2, Map.of("fi_blaze", 1), "enhance_ability",
                        "Подготовка возгорания", "flamestrike: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "flamestrike", 0.20)),
                n("fi_molten_fury", T, 4, 3, 2, Map.of("fi_pyromaniac", 1), "passive_stat",
                        "Расплавленная ярость", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("fi_fire_ward", T, 4, 4, 2, Map.of("fi_critical_mass", 1), "passive_stat",
                        "Огненный оберег", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                // Ряд 5 (гейт 20)
                n("fi_combustion", T, 5, 1, 1, Map.of("fi_flamestrike", 1), "unlock_ability",
                        "Возгорание", "НОВАЯ: +20% крит-шанса спеллов на 8 с, КД 90 с",
                        Spec2Effect.of("unlock_ability", "combustion", 1.0)),
                n("fi_pyroclasm", T, 5, 2, 3, Map.of("fi_combustion_prep", 1), "passive_stat",
                        "Пироклазм", "+1 стек burning за ранг (кап 3→4→5)",
                        Spec2Effect.of("dot_stacks", "burning", 1.0)),
                n("fi_fire_mastery", T, 5, 3, 3, Map.of("fi_molten_fury", 1), "passive_stat",
                        "Мастерство огня", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("fi_pyroblast", T, 6, 2, 1,
                        Map.of("fi_combustion", 1, "fi_pyroclasm", 2), "ultimate",
                        "Огненная глыба", "ульт: огромный урон + burning ×2 стека, КД 120 с",
                        Spec2Effect.of("unlock_ability", "pyroblast", 1.0))
        ));
    }

    /* ============ МАГ · ЛЁД (frost) — ёмкость 52 ============ */
    private static Spec2Tree frost() {
        final String T = "frost";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("fr_power", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Сила льда", "+2% маг-урона способностей за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                n("fr_permafrost_training", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Школа вечной мерзлоты", "+1 с длительности chilled за ранг",
                        Spec2Effect.of("dot_dur", "chilled", 1.0)),
                n("fr_icy_veins", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Ледяные жилы", "+2% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 2.0)),
                n("fr_frost_armor", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Ледяной доспех", "+4% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 4.0)),
                // Ряд 2 (гейт 5)
                n("fr_frostbolt", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Ледяная стрела", "НОВАЯ: маг-урон + chilled, КД 5 с",
                        Spec2Effect.of("unlock_ability", "frostbolt", 1.0)),
                n("fr_deep_freeze_prep", T, 2, 2, 3, Map.of("fr_permafrost_training", 2), "passive_stat",
                        "Глубокая заморозка", "+10% урона chilled за ранг",
                        Spec2Effect.of("dot_mult", "chilled", 10.0)),
                n("fr_winter_chill", T, 2, 3, 3, Map.of("fr_icy_veins", 2), "passive_stat",
                        "Зимняя стужа", "+10% урона chilled за ранг",
                        Spec2Effect.of("dot_mult", "chilled", 10.0)),
                n("fr_glacial_mastery", T, 2, 4, 3, Map.of("fr_frost_armor", 2), "passive_stat",
                        "Ледниковое мастерство", "+3 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 3.0)),
                // Ряд 3 (гейт 10)
                n("fr_frostbolt_enh", T, 3, 1, 3, Map.of("fr_frostbolt", 1), "enhance_ability",
                        "Усиленная стрела", "frostbolt: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "frostbolt", 0.15)),
                n("fr_ice_floes", T, 3, 2, 2, Map.of("fr_deep_freeze_prep", 1), "passive_stat",
                        "Ледяные глыбы", "+3% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 3.0)),
                n("fr_frozen_veins", T, 3, 3, 3, Map.of("fr_winter_chill", 1), "passive_stat",
                        "Замёрзшие жилы", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("fr_cold_snap_prep", T, 3, 4, 2, Map.of("fr_glacial_mastery", 1), "enhance_ability",
                        "Подготовка хладомара", "boreas_breath: −10% кулдауна за ранг",
                        Spec2Effect.of("cd", "boreas_breath", 0.10)),
                // Ряд 4 (гейт 15)
                n("fr_blizzard", T, 4, 1, 1, Map.of("fr_frostbolt_enh", 1), "unlock_ability",
                        "Снежная буря", "НОВАЯ: зона chilled + SLOW 5 с, КД 25 с",
                        Spec2Effect.of("unlock_ability", "blizzard", 1.0)),
                n("fr_shatter_prep", T, 4, 2, 2, Map.of("fr_ice_floes", 1), "enhance_ability",
                        "Подготовка раскола", "blizzard: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "blizzard", 0.20)),
                n("fr_frost_ward", T, 4, 3, 2, Map.of("fr_frozen_veins", 1), "passive_stat",
                        "Морозный оберег", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                n("fr_ice_barrier", T, 4, 4, 2, Map.of("fr_cold_snap_prep", 1), "unlock_ability",
                        "Ледяная преграда", "НОВАЯ: щит-пул 15% maxHP на 6 с, КД 45 с",
                        Spec2Effect.of("unlock_ability", "ice_barrier", 1.0)),
                // Ряд 5 (гейт 20)
                n("fr_frozen_mastery", T, 5, 1, 3, Map.of("fr_blizzard", 1), "passive_stat",
                        "Мастерство льда", "+2 с длительности chilled за ранг",
                        Spec2Effect.of("dot_dur", "chilled", 2.0)),
                n("fr_glacial_spike", T, 5, 2, 2, Map.of("fr_shatter_prep", 1), "passive_stat",
                        "Ледниковый шип", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                n("fr_ice_ward_enh", T, 5, 3, 3, Map.of("fr_ice_barrier", 1), "enhance_ability",
                        "Усиленная преграда", "ice_barrier: +2 с за ранг",
                        Spec2Effect.of("kit_dur", "ice_barrier", 2.0)),
                // Ряд 6 (гейт 30) — ульт
                n("fr_ice_lance_shatter", T, 6, 2, 1,
                        Map.of("fr_frozen_mastery", 2, "fr_glacial_spike", 1), "ultimate",
                        "Ледяное копьё: раскол", "ульт: ×3 урона по chilled-цели, КД 120 с",
                        Spec2Effect.of("unlock_ability", "ice_lance_shatter", 1.0))
        ));
    }
}
