// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Разбойника (3 спеки).
 * assassination (Ликвидация): FIGHTER, NATURE, DoT — unlock poison_burst/borgia_poison/deadly_brew,
 *         ульт deathmark.
 * outlaw (Головорез): FIGHTER, PHYSICAL, CC STUN — unlock pistol_shot/adrenaline/killing_spree,
 *         ульт between_the_eyes (STUN по bleed).
 * subtlety (Скрытность): FIGHTER, SHADOW, CC-контр — unlock backstab/shadow_dance/cloak_of_shadows,
 *         ульт shadow_blades.
 */
public final class RogueTrees {

    private RogueTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("assassination", assassination());
        into.put("outlaw", outlaw());
        into.put("subtlety", subtlety());
    }

    private static Spec2Node n(String id, String treeId, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, treeId, row, col, maxRank, prereqs, type, name, lore, effect);
    }

    /* ============ РАЗБОЙНИК · ЛИКВИДАЦИЯ (assassination) — ёмкость 51 ============ */
    private static Spec2Tree assassination() {
        final String T = "assassination";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("as_improved_poisons", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Улучшенные яды", "+10% урона DoT poison за ранг",
                        Spec2Effect.of("dot_mult", "poison", 10.0)),
                n("as_malice", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Злоба", "+2% crit за ранг",
                        Spec2Effect.of("crit_melee_pct", "self", 2.0)),
                n("as_quick_recovery", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Быстрое восстановление", "+10% регенерации энергии за ранг",
                        Spec2Effect.of("resource_regen_pct", "energy", 10.0)),
                n("as_lethality", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Смертоносность", "+3% физ-урона за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 3.0)),
                // Ряд 2 (гейт 5)
                n("as_poison_burst", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Взрыв яда", "НОВАЯ: детонация poison-стеков, урон = стеки ×5, КД 10 с",
                        Spec2Effect.of("unlock_ability", "poison_burst", 1.0)),
                n("as_vile_poisons", T, 2, 2, 3, Map.of("as_improved_poisons", 2), "passive_stat",
                        "Гнусные яды", "poison: +1 стек за ранг (кап 3→4→5)",
                        Spec2Effect.of("dot_stacks", "poison", 1.0)),
                n("as_deadly_brew", T, 2, 3, 2, Map.of(), "passive_proc",
                        "Смертельное варево", "20% за ранг: удар накладывает poison",
                        Spec2Effect.of("proc_apply_poison", "self", 0.20)),
                // Ряд 3 (гейт 10)
                n("as_borgia_poison", T, 3, 1, 1, Map.of(), "unlock_ability",
                        "Яд Борджа", "перенос: урон + DoT poison (старый кит, слот 4)",
                        Spec2Effect.of("unlock_ability", "borgia_poison", 1.0)),
                n("as_improved_expose", T, 3, 2, 3, Map.of("as_poison_burst", 1), "enhance_ability",
                        "Улучшенное обнажение", "poison_burst: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "poison_burst", 0.20)),
                n("as_seal_fate", T, 3, 3, 2, Map.of("as_vile_poisons", 1), "passive_proc",
                        "Печать судьбы", "30% за ранг: crit даёт +1 комбо-поинт",
                        Spec2Effect.of("proc_combo_point", "self", 0.30)),
                n("as_cold_blood", T, 3, 4, 1, Map.of("as_deadly_brew", 1), "unlock_ability",
                        "Хладнокровие", "НОВАЯ: след. удар гарантированный крит, КД 60 с",
                        Spec2Effect.of("unlock_ability", "cold_blood", 1.0)),
                // Ряд 4 (гейт 15)
                n("as_venomous_wounds", T, 4, 1, 2, Map.of("as_borgia_poison", 1), "passive_proc",
                        "Ядовитые раны", "15% за ранг: crit продлевает poison на 2 с",
                        Spec2Effect.of("proc_poison_extend", "self", 0.15)),
                n("as_master_poisoner", T, 4, 2, 2, Map.of("as_improved_expose", 1), "enhance_ability",
                        "Мастер ядов", "borgia_poison: +3 с DoT за ранг",
                        Spec2Effect.of("dot_dur", "poison", 3.0)),
                n("as_vendetta", T, 4, 3, 1, Map.of("as_seal_fate", 1), "unlock_ability",
                        "Вендетта", "НОВАЯ: цель получает +30% урона 10 с, КД 120 с",
                        Spec2Effect.of("unlock_ability", "vendetta", 1.0)),
                n("as_toxic_mastery", T, 4, 4, 2, Map.of("as_cold_blood", 1), "passive_stat",
                        "Мастер токсинов", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                // Ряд 5 (гейт 20)
                n("as_deadly_poison", T, 5, 1, 3, Map.of("as_venomous_wounds", 1), "passive_stat",
                        "Смертельный яд", "poison: +20% урона за ранг",
                        Spec2Effect.of("dot_mult", "poison", 20.0)),
                n("as_envenom", T, 5, 2, 1, Map.of("as_master_poisoner", 1), "unlock_ability",
                        "Отравление", "НОВАЯ: физ-урон + хил 30% от урона, КД 8 с",
                        Spec2Effect.of("unlock_ability", "envenom", 1.0)),
                n("as_cut_to_the_chase", T, 5, 3, 3, Map.of("as_vendetta", 1), "passive_proc",
                        "Ближе к делу", "20% за ранг: crit обновляет vendetta",
                        Spec2Effect.of("proc_vendetta_refresh", "self", 0.20)),
                n("as_shadow_focus", T, 5, 4, 2, Map.of("as_toxic_mastery", 1), "passive_stat",
                        "Теневой фокус", "+5% SHADOW-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("as_deathmark", T, 6, 2, 1, Map.of("as_deadly_poison", 2, "as_cut_to_the_chase", 2), "ultimate",
                        "Метка смерти", "ульт: execute по цели с ≥3 poison-стеками, КД 180 с",
                        Spec2Effect.of("unlock_ability", "deathmark", 1.0))
        ));
    }

    /* ============ РАЗБОЙНИК · ГОЛОВOREZ (outlaw) — ёмкость 50 ============ */
    private static Spec2Tree outlaw() {
        final String T = "outlaw";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("ol_improved_sinister_strike", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Улучшенный коварный удар", "+3% физ-урона за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 3.0)),
                n("ol_endurance", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Выносливость", "+5% maxHP за ранг",
                        Spec2Effect.of("hp_pct", "self", 5.0)),
                n("ol_lightning_reflexes", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Молниеносные рефлексы", "+3% уклонения за ранг",
                        Spec2Effect.of("avoid", "dodge", 3.0)),
                n("ol_improved_sprint", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Улучшенный спринт", "+10% скорости движения за ранг",
                        Spec2Effect.of("move_speed_pct", "self", 10.0)),
                // Ряд 2 (гейт 5)
                n("ol_pistol_shot", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Выстрел из пистолета", "НОВАЯ: STUN 1.5 с, КД 30 с",
                        Spec2Effect.of("unlock_ability", "pistol_shot", 1.0)),
                n("ol_precision", T, 2, 2, 3, Map.of(), "passive_stat",
                        "Точность", "+2% crit за ранг",
                        Spec2Effect.of("crit_melee_pct", "self", 2.0)),
                n("ol_quick_draw", T, 2, 3, 2, Map.of("ol_pistol_shot", 1), "enhance_ability",
                        "Быстрый выстрел", "pistol_shot: −5 с КД за ранг",
                        Spec2Effect.of("kit_cd", "pistol_shot", 5.0)),
                // Ряд 3 (гейт 10)
                n("ol_blade_flurry", T, 3, 1, 1, Map.of(), "unlock_ability",
                        "Шквал клинков", "НОВАЯ: удары поражают доп. цель 10 с, КД 60 с",
                        Spec2Effect.of("unlock_ability", "blade_flurry", 1.0)),
                n("ol_blade_master", T, 3, 2, 3, Map.of("ol_blade_flurry", 1), "passive_stat",
                        "Мастер клинка", "blade_fan: +10% урона за ранг",
                        Spec2Effect.of("kit_mult", "blade_fan", 0.10)),
                n("ol_adrenaline_rush", T, 3, 3, 1, Map.of(), "unlock_ability",
                        "Прилив адреналина", "НОВАЯ: +50% скорости атаки 10 с, КД 120 с",
                        Spec2Effect.of("unlock_ability", "adrenaline_rush", 1.0)),
                n("ol_dual_wield_specialization", T, 3, 4, 3, Map.of("ol_precision", 1), "passive_stat",
                        "Специализация парного оружия", "+5% урона офф-хенда за ранг",
                        Spec2Effect.of("offhand_dmg_pct", "self", 5.0)),
                // Ряд 4 (гейт 15)
                n("ol_killing_spree", T, 4, 1, 1, Map.of("ol_blade_flurry", 1), "unlock_ability",
                        "Серия убийств", "НОВАЯ: 5 ударов по случайным целям, КД 90 с",
                        Spec2Effect.of("unlock_ability", "killing_spree", 1.0)),
                n("ol_improved_adrenaline", T, 4, 2, 2, Map.of("ol_blade_master", 1), "enhance_ability",
                        "Улучшенный адреналин", "adrenaline_rush: +5 с за ранг",
                        Spec2Effect.of("kit_dur", "adrenaline_rush", 5.0)),
                n("ol_swordmaster", T, 4, 3, 2, Map.of("ol_adrenaline_rush", 1), "passive_stat",
                        "Мастер меча", "+5% парирования за ранг",
                        Spec2Effect.of("avoid", "parry", 5.0)),
                n("ol_ruthlessness", T, 4, 4, 2, Map.of("ol_dual_wield_specialization", 1), "passive_proc",
                        "Безжалостность", "20% за ранг: crit +30% урона",
                        Spec2Effect.of("proc_crit_bonus", "self", 0.20)),
                // Ряд 5 (гейт 20)
                n("ol_riposte", T, 5, 1, 3, Map.of("ol_killing_spree", 1), "passive_proc",
                        "Ответный удар", "15% за ранг: при парировании +50% урона след. удара",
                        Spec2Effect.of("proc_riposte", "self", 0.15)),
                n("ol_surprise_attacks", T, 5, 2, 2, Map.of("ol_improved_adrenaline", 1), "passive_proc",
                        "Внезапные атаки", "25% за ранг: удар не может быть уклонён",
                        Spec2Effect.of("proc_undodgeable", "self", 0.25)),
                n("ol_combat_potency", T, 5, 3, 3, Map.of("ol_swordmaster", 1), "passive_stat",
                        "Боевая мощь", "+10% регенерации энергии за ранг",
                        Spec2Effect.of("resource_regen_pct", "energy", 10.0)),
                n("ol_vitality", T, 5, 4, 2, Map.of("ol_ruthlessness", 1), "passive_stat",
                        "Живучесть", "+3% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 3.0)),
                // Ряд 6 (гейт 30) — ульт
                n("ol_between_the_eyes", T, 6, 2, 1, Map.of("ol_riposte", 2, "ol_combat_potency", 2), "ultimate",
                        "Между глаз", "ульт: STUN 3 с по bleed-цели, КД 180 с",
                        Spec2Effect.of("unlock_ability", "between_the_eyes", 1.0))
        ));
    }

    /* ============ РАЗБОЙНИК · СКРЫТНОСТЬ (subtlety) — ёмкость 53 ============ */
    private static Spec2Tree subtlety() {
        final String T = "subtlety";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("su_improved_evasion", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Улучшенное уклонение", "+3% уклонения за ранг",
                        Spec2Effect.of("avoid", "dodge", 3.0)),
                n("su_malicious_intent", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Злой умысел", "+2% crit за ранг",
                        Spec2Effect.of("crit_melee_pct", "self", 2.0)),
                n("su_fleet_footed", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Быстроногий", "+5% скорости движения за ранг",
                        Spec2Effect.of("move_speed_pct", "self", 5.0)),
                n("su_shadow_focus", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Теневой фокус", "+5% SHADOW-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 5.0)),
                // Ряд 2 (гейт 5)
                n("su_backstab", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Удар в спину", "НОВАЯ: ×1.5 урона со спины, КД 6 с",
                        Spec2Effect.of("unlock_ability", "backstab", 1.0)),
                n("su_opportunity", T, 2, 2, 3, Map.of("su_backstab", 1), "passive_stat",
                        "Возможность", "backstab: +10% урона за ранг",
                        Spec2Effect.of("kit_mult", "backstab", 0.10)),
                n("su_camouflage", T, 2, 3, 2, Map.of(), "passive_proc",
                        "Маскировка", "10% за ранг: невидимость длится +1 с",
                        Spec2Effect.of("proc_stealth_extend", "self", 0.10)),
                // Ряд 3 (гейт 10)
                n("su_shadowstep", T, 3, 1, 1, Map.of("su_backstab", 1), "unlock_ability",
                        "Шаг сквозь тень", "НОВАЯ: телепорт за спину цели, КД 20 с",
                        Spec2Effect.of("unlock_ability", "shadowstep", 1.0)),
                n("su_serrated_blades", T, 3, 2, 3, Map.of("su_opportunity", 1), "passive_stat",
                        "Зазубренные клинки", "pen_phys +5% за ранг",
                        Spec2Effect.of("pen_phys_pct", "self", 5.0)),
                n("su_initiative", T, 3, 3, 2, Map.of("su_camouflage", 1), "passive_proc",
                        "Инициатива", "20% за ранг: удар из невидимости +50% урона",
                        Spec2Effect.of("proc_stealth_bonus", "self", 0.20)),
                n("su_prep", T, 3, 4, 1, Map.of("su_shadowstep", 1), "unlock_ability",
                        "Подготовка", "НОВАЯ: сброс КД shadow_cloak/shadowstep, КД 300 с",
                        Spec2Effect.of("unlock_ability", "preparation", 1.0)),
                // Ряд 4 (гейт 15)
                n("su_shadow_dance", T, 4, 1, 1, Map.of(), "unlock_ability",
                        "Танец теней", "перенос: +30 ЛОВКОСТИ 4 с (старый кит, слот 5)",
                        Spec2Effect.of("unlock_ability", "shadow_dance", 1.0)),
                n("su_death_from_above", T, 4, 2, 2, Map.of("su_serrated_blades", 1), "enhance_ability",
                        "Смерть сверху", "backstab: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "backstab", 0.20)),
                n("su_cloak_of_shadows", T, 4, 3, 1, Map.of("su_initiative", 1), "unlock_ability",
                        "Плащ теней (очищение)", "перенос + НОВОЕ: cleanse CC self, КД 60 с",
                        Spec2Effect.of("unlock_ability", "cloak_of_shadows_cleanse", 1.0)),
                n("su_master_of_deception", T, 4, 4, 2, Map.of("su_prep", 1), "passive_stat",
                        "Мастер обмана", "shadow_cloak: +5 с за ранг",
                        Spec2Effect.of("kit_dur", "shadow_cloak", 5.0)),
                // Ряд 5 (гейт 20)
                n("su_find_weakness", T, 5, 1, 3, Map.of("su_shadow_dance", 1), "passive_proc",
                        "Обнаружение слабости", "25% за ранг: удар из невидимости игнорирует 20% брони",
                        Spec2Effect.of("proc_armor_pen", "self", 0.25)),
                n("su_hemorrhage", T, 5, 2, 1, Map.of("su_death_from_above", 1), "unlock_ability",
                        "Кровопускание", "НОВАЯ: физ-урон + bleed 8 с, КД 10 с",
                        Spec2Effect.of("unlock_ability", "hemorrhage", 1.0)),
                n("su_nerve_strike", T, 5, 3, 2, Map.of("su_cloak_of_shadows", 1), "enhance_ability",
                        "Удар по нервам", "strangle: +1 с SLOW за ранг",
                        Spec2Effect.of("kit_dur", "strangle", 1.0)),
                n("su_master_of_subtlety", T, 5, 4, 3, Map.of("su_master_of_deception", 1), "passive_stat",
                        "Мастер скрытности", "shadow_dance: +10 ЛОВКОСТИ за ранг",
                        Spec2Effect.of("kit_attr_agi", "shadow_dance", 10.0)),
                // Ряд 6 (гейт 30) — ульт
                n("su_shadow_blades", T, 6, 2, 1, Map.of("su_find_weakness", 2, "su_master_of_subtlety", 2), "ultimate",
                        "Теневые клинки", "ульт: все удары из «тени» 6 с, КД 180 с",
                        Spec2Effect.of("unlock_ability", "shadow_blades", 1.0))
        ));
    }
}
