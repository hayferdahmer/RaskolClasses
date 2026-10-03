// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Жреца (3 спеки), ёмкости 52/52/51 (>46 — закрыть нельзя).
 * discipline (Послушание): HEALER, щиты + кара — unlock purge (cleanse CC+DoT),
 *         pain_suppression, ульт spirit_shell (щит-пул от последнего хила).
 * holy (Свет): HEALER, одиночный/групповой хил — unlock flash_heal, circle_elysium
 *         (перенос, слот 4), lightwell, ульт divine_hymn (груп-хил канал 3 с).
 * shadow (Тьма): FIGHTER, DoT wither (SHADOW) + контроль — unlock withering_touch,
 *         mind_flay (SLOW-канал), shadowfiend, ульт wrath_heaven (перенос, слот 5).
 */
public final class PriestTrees {

    private PriestTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("discipline", discipline());
        into.put("holy", holy());
        into.put("shadow", shadow());
    }

    private static Spec2Node n(String id, String treeId, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, treeId, row, col, maxRank, prereqs, type, name, lore, effect);
    }

    /* ============ ЖРЕЦ · ПОСЛУШАНИЕ (discipline) — ёмкость 52 ============ */
    private static Spec2Tree discipline() {
        final String T = "discipline";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("dis_focus", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Сосредоточие послушания", "+3% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 3.0)),
                n("dis_inner_strength", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Внутренняя сила", "+4 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 4.0)),
                n("dis_resilience", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Стойкость ума", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                n("dis_penitent", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Кающийся", "+2% маг-урона способностей за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                // Ряд 2 (гейт 5)
                n("dis_purge", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Очищение", "НОВАЯ: cleanse CC + DoT с союзника, КД 12 с",
                        Spec2Effect.of("unlock_ability", "purge", 1.0)),
                n("dis_power_word_prep", T, 2, 2, 3, Map.of("dis_focus", 2), "enhance_ability",
                        "Слово силы: подготовка", "aegis_faith: +15% эффективности за ранг",
                        Spec2Effect.of("kit_mult", "aegis_faith", 0.15)),
                n("dis_borrowed_time", T, 2, 3, 2, Map.of(), "enhance_ability",
                        "Заёмное время", "aegis_faith: −4 с кулдауна за ранг",
                        Spec2Effect.of("kit_cd", "aegis_faith", 4.0)),
                n("dis_atonement_training", T, 2, 4, 3, Map.of("dis_inner_strength", 2), "passive_stat",
                        "Школа искупления", "+2% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 2.0)),
                // Ряд 3 (гейт 10)
                n("dis_radiant_grace", T, 3, 1, 3, Map.of("dis_purge", 1), "passive_stat",
                        "Сияющая благодать", "+3% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 3.0)),
                n("dis_mastery", T, 3, 2, 2, Map.of("dis_power_word_prep", 1), "passive_stat",
                        "Мастерство послушания", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("dis_shield_discipline", T, 3, 3, 3, Map.of("dis_borrowed_time", 1), "passive_stat",
                        "Дисциплина щита", "+3% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 3.0)),
                n("dis_mental_agility", T, 3, 4, 2, Map.of("dis_atonement_training", 1), "enhance_ability",
                        "Подвижность ума", "saint_tear: −10% кулдауна за ранг",
                        Spec2Effect.of("cd", "saint_tear", 0.10)),
                // Ряд 4 (гейт 15)
                n("dis_pain_suppression", T, 4, 1, 1, Map.of("dis_radiant_grace", 1), "unlock_ability",
                        "Подавление боли", "НОВАЯ: −40% входящего урона союзнику 5 с, КД 90 с",
                        Spec2Effect.of("unlock_ability", "pain_suppression", 1.0)),
                n("dis_divine_providence", T, 4, 2, 2, Map.of("dis_mastery", 1), "passive_stat",
                        "Божественный промысел", "+4% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 4.0)),
                n("dis_soul_guard", T, 4, 3, 2, Map.of("dis_shield_discipline", 1), "passive_stat",
                        "Страж души", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                n("dis_improved_purge", T, 4, 4, 2, Map.of("dis_mental_agility", 1), "enhance_ability",
                        "Улучшенное очищение", "purge: −5 с кулдауна за ранг",
                        Spec2Effect.of("kit_cd", "purge", 5.0)),
                // Ряд 5 (гейт 20)
                n("dis_evangelism", T, 5, 1, 3, Map.of("dis_pain_suppression", 1), "passive_stat",
                        "Евангелизм", "+4% силы исцеления за ранг",
                        Spec2Effect.of("hpow_pct", "self", 4.0)),
                n("dis_grace_of_light", T, 5, 2, 2, Map.of("dis_divine_providence", 1), "passive_stat",
                        "Благодать света", "+1 Света/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("dis_shield_mastery", T, 5, 3, 3, Map.of("dis_soul_guard", 1), "enhance_ability",
                        "Мастерство щита", "aegis_faith: +2 с за ранг",
                        Spec2Effect.of("kit_dur", "aegis_faith", 2.0)),
                // Ряд 6 (гейт 30) — ульт
                n("dis_spirit_shell", T, 6, 2, 1,
                        Map.of("dis_evangelism", 2, "dis_shield_mastery", 2), "ultimate",
                        "Оболочка духа", "ульт: щит-пул = 30% последнего хила, 8 с, КД 180 с",
                        Spec2Effect.of("unlock_ability", "spirit_shell", 1.0))
        ));
    }

    /* ============ ЖРЕЦ · СВЕТ (holy) — ёмкость 52 ============ */
    private static Spec2Tree holy() {
        final String T = "holy";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("ho_power", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Сила света", "+3% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 3.0)),
                n("ho_divine_grace", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Божественная благодать", "+3% силы исцеления за ранг",
                        Spec2Effect.of("hpow_pct", "self", 3.0)),
                n("ho_blessed_recovery", T, 1, 3, 5, Map.of(), "passive_stat",
                        "Благословенное восстановление", "+1 Света/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("ho_holy_ward", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Святой оберег", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                // Ряд 2 (гейт 5)
                n("ho_flash_heal", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Быстрое исцеление", "НОВАЯ: мгновенный хил, КД 2 с",
                        Spec2Effect.of("unlock_ability", "flash_heal", 1.0)),
                n("ho_holy_mending", T, 2, 2, 3, Map.of("ho_power", 2), "passive_stat",
                        "Святое врачевание", "+3% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 3.0)),
                n("ho_light_infusion", T, 2, 3, 2, Map.of("ho_divine_grace", 1), "passive_stat",
                        "Вливание света", "+3% силы исцеления за ранг",
                        Spec2Effect.of("hpow_pct", "self", 3.0)),
                n("ho_blessed_hands", T, 2, 4, 3, Map.of("ho_holy_ward", 2), "enhance_ability",
                        "Благословенные руки", "saint_tear: +8 к базе за ранг",
                        Spec2Effect.of("kit_base", "saint_tear", 8.0)),
                // Ряд 3 (гейт 10)
                n("ho_flash_heal_enh", T, 3, 1, 3, Map.of("ho_flash_heal", 1), "enhance_ability",
                        "Усиленное быстрое исцеление", "flash_heal: +15% хила за ранг",
                        Spec2Effect.of("kit_mult", "flash_heal", 0.15)),
                n("ho_concentration", T, 3, 2, 2, Map.of("ho_holy_mending", 1), "passive_stat",
                        "Сосредоточение", "+3% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 3.0)),
                n("ho_surge_of_light", T, 3, 3, 3, Map.of("ho_light_infusion", 1), "passive_proc",
                        "Вспышка света", "10% за ранг: след. хил бесплатный",
                        Spec2Effect.of("proc_free_heal", "self", 0.10)),
                n("ho_divine_providence", T, 3, 4, 2, Map.of("ho_blessed_hands", 1), "enhance_ability",
                        "Провидение", "word_of_life: −10% кулдауна за ранг",
                        Spec2Effect.of("cd", "word_of_life", 0.10)),
                // Ряд 4 (гейт 15)
                n("ho_circle_elysium", T, 4, 1, 1, Map.of(), "unlock_ability",
                        "Круг Элизия", "перенос: групповой хил радиус 6 (старый кит, слот 4)",
                        Spec2Effect.of("unlock_ability", "circle_elysium", 1.0)),
                n("ho_sanctuary", T, 4, 2, 2, Map.of("ho_concentration", 1), "passive_stat",
                        "Святилище", "+4% исходящего лечения за ранг",
                        Spec2Effect.of("heal_out_pct", "self", 4.0)),
                n("ho_light_of_dawn", T, 4, 3, 2, Map.of("ho_surge_of_light", 1), "enhance_ability",
                        "Свет рассвета", "circle_elysium: +20% хила за ранг",
                        Spec2Effect.of("kit_mult", "circle_elysium", 0.20)),
                n("ho_blessed_resilience", T, 4, 4, 2, Map.of("ho_divine_providence", 1), "passive_stat",
                        "Благословенная стойкость", "+4% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 4.0)),
                // Ряд 5 (гейт 20)
                n("ho_lightwell", T, 5, 1, 1, Map.of("ho_circle_elysium", 1), "unlock_ability",
                        "Колодец света", "НОВАЯ: зона-хил +2 HP/с радиус 4, 8 с, КД 60 с",
                        Spec2Effect.of("unlock_ability", "lightwell", 1.0)),
                n("ho_holy_mastery", T, 5, 2, 3, Map.of("ho_sanctuary", 1), "passive_stat",
                        "Мастерство света", "+4% силы исцеления за ранг",
                        Spec2Effect.of("hpow_pct", "self", 4.0)),
                n("ho_radiant_veil", T, 5, 3, 3, Map.of("ho_light_of_dawn", 1), "passive_stat",
                        "Сияющая вуаль", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                // Ряд 6 (гейт 30) — ульт
                n("ho_divine_hymn", T, 6, 2, 1,
                        Map.of("ho_lightwell", 1, "ho_holy_mastery", 2), "ultimate",
                        "Божественный гимн", "ульт: канал 3 с, груп-хил всем союзникам r8, КД 180 с",
                        Spec2Effect.of("unlock_ability", "divine_hymn", 1.0))
        ));
    }

    /* ============ ЖРЕЦ · ТЬМА (shadow) — ёмкость 51 ============ */
    private static Spec2Tree shadow() {
        final String T = "shadow";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("sh_power", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Сила тьмы", "+3% маг-урона способностей за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 3.0)),
                n("sh_dark_embrace", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Тёмные объятия", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                n("sh_wither_training", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Школа иссушения", "+10% урона wither за ранг",
                        Spec2Effect.of("dot_mult", "wither", 10.0)),
                n("sh_mind_mastery", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Владычество разума", "+4 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 4.0)),
                // Ряд 2 (гейт 5)
                n("sh_withering_touch", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Иссушающее касание", "НОВАЯ: маг-урон + DoT wither (SHADOW) 6 с, КД 6 с",
                        Spec2Effect.of("unlock_ability", "withering_touch", 1.0)),
                n("sh_shadow_focus", T, 2, 2, 3, Map.of("sh_power", 2), "passive_stat",
                        "Теневая сосредоточенность", "pen_magic +4% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 4.0)),
                n("sh_dark_pact", T, 2, 3, 2, Map.of(), "passive_stat",
                        "Тёмный пакт", "+1 Света/с за ранг (тьма питает)",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("sh_shadow_weave", T, 2, 4, 3, Map.of("sh_dark_embrace", 2), "passive_stat",
                        "Теневое плетение", "+2% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                // Ряд 3 (гейт 10)
                n("sh_wither_enh", T, 3, 1, 3, Map.of("sh_withering_touch", 1), "enhance_ability",
                        "Усиленное иссушение", "wither: +2 с длительности за ранг",
                        Spec2Effect.of("dot_dur", "wither", 2.0)),
                n("sh_shadow_mastery", T, 3, 2, 2, Map.of("sh_shadow_focus", 1), "passive_stat",
                        "Мастерство тени", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("sh_void_corruption", T, 3, 3, 3, Map.of("sh_dark_pact", 1), "passive_stat",
                        "Порча пустоты", "+1 стек wither за ранг (кап 1→2→3)",
                        Spec2Effect.of("dot_stacks", "wither", 1.0)),
                n("sh_dark_veil", T, 3, 4, 2, Map.of("sh_shadow_weave", 1), "passive_stat",
                        "Тёмная вуаль", "+2% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 2.0)),
                // Ряд 4 (гейт 15)
                n("sh_mind_flay", T, 4, 1, 1, Map.of("sh_wither_enh", 1), "unlock_ability",
                        "Пытка разума", "НОВАЯ: канал 3 с, урон + SLOW, КД 15 с",
                        Spec2Effect.of("unlock_ability", "mind_flay", 1.0)),
                n("sh_shadow_affinity", T, 4, 2, 2, Map.of("sh_shadow_mastery", 1), "enhance_ability",
                        "Теневое родство", "mind_flay: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "mind_flay", 0.20)),
                n("sh_void_tendrils", T, 4, 3, 2, Map.of("sh_void_corruption", 1), "passive_stat",
                        "Щупальца пустоты", "+10% урона wither за ранг",
                        Spec2Effect.of("dot_mult", "wither", 10.0)),
                n("sh_dark_resilience", T, 4, 4, 2, Map.of("sh_dark_veil", 1), "passive_stat",
                        "Тёмная стойкость", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                // Ряд 5 (гейт 20)
                n("sh_shadowfiend", T, 5, 1, 1, Map.of("sh_mind_flay", 1), "unlock_ability",
                        "Тенескот", "НОВАЯ: пет-мини 8 с, атакует цель, возвращает Свет, КД 90 с",
                        Spec2Effect.of("unlock_ability", "shadowfiend", 1.0)),
                n("sh_void_mastery", T, 5, 2, 3, Map.of("sh_shadow_affinity", 1), "passive_stat",
                        "Мастерство пустоты", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                n("sh_shadow_form", T, 5, 3, 3, Map.of("sh_void_tendrils", 1), "passive_stat",
                        "Облик тени", "+3% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 3.0)),
                // Ряд 6 (гейт 30) — ульт
                n("sh_wrath_heaven", T, 6, 2, 1,
                        Map.of("sh_shadowfiend", 1, "sh_void_mastery", 2), "ultimate",
                        "Кара Небес (тьма)", "ульт: перенос старого кита (слот 5), execute ×3",
                        Spec2Effect.of("unlock_ability", "wrath_heaven", 1.0))
        ));
    }
}
