// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Охотника (3 спеки).
 * marksmanship (Стрельба): FIGHTER, PHYSICAL, bleed — unlock aimed_shot/arrow_fan/true_shot,
 *         ульт arrow_rain (перенос).
 * survival (Выживание): FIGHTER, NATURE/FIRE, CC ROOT — unlock poison_shot/explosive_trap/survivor,
 *         ульт serpent_sting (новый DoT-ульт).
 * beastmaster (Повелитель зверей): FIGHTER, NATURE — unlock pet_wolf/beast_ferocity,
 *         ульт bestial_wrath.
 *
 * 1.14.7 (Спринт 2, P0-4): ёмкости подняты до ≥ spec2.tree-capacity-min (50):
 *   marksmanship 46→50 (mm_steady_hand 5→7, mm_eagle_eye 4→5, mm_swift_quiver 4→5);
 *   survival     49→50 (sv_endurance 5→6);
 *   beastmaster  49→50 (bm_animal_handler 5→6).
 *   Повышены maxRank ТОЛЬКО пассивных узлов ряда 1 без зависимостей по maxRank —
 *   гейты рядов, пререквизиты и ширина рядов (≤9 узлов) не изменились.
 *   Баланс: суммарная доступная сила дерева выросла незначительно (бюджет очков
 *   по-прежнему 46, закрыть дерево целиком всё ещё нельзя).
 */
public final class HunterTrees {

    private HunterTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("marksmanship", marksmanship());
        into.put("survival", survival());
        into.put("beastmaster", beastmaster());
    }

    private static Spec2Node n(String id, String treeId, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, treeId, row, col, maxRank, prereqs, type, name, lore, effect);
    }

    /* ============ ОХОТНИК · СТРЕЛЬБА (marksmanship) — ёмкость 50 ============ */
    private static Spec2Tree marksmanship() {
        final String T = "marksmanship";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("mm_steady_hand", T, 1, 1, 7, Map.of(), "passive_stat",
                        "Твёрдая рука", "+2% урона стрел за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 2.0)),
                n("mm_eagle_eye", T, 1, 2, 5, Map.of(), "passive_stat",
                        "Орлиный глаз", "+1% крита стрел за ранг",
                        Spec2Effect.of("crit_ranged_pct", "self", 1.0)),
                n("mm_swift_quiver", T, 1, 3, 5, Map.of(), "passive_stat",
                        "Быстрый колчан", "+2% скорости стрельбы за ранг",
                        Spec2Effect.of("attack_speed_pct", "self", 2.0)),
                n("mm_survivalist", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Выживальщик", "+3% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 3.0)),
                // Ряд 2 (гейт 5)
                n("mm_aimed_shot", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Прицельный выстрел", "НОВАЯ: тяжёлый одиночный + pen_phys 20%, КД 10 с",
                        Spec2Effect.of("unlock_ability", "aimed_shot", 1.0)),
                n("mm_lethal_shots", T, 2, 2, 3, Map.of(), "passive_proc",
                        "Смертоносные выстрелы", "10% за ранг: крит +50% урона",
                        Spec2Effect.of("proc_crit_bonus", "self", 0.10)),
                n("mm_piercing_shots", T, 2, 3, 2, Map.of("mm_aimed_shot", 1), "enhance_ability",
                        "Бронебойные выстрелы", "aimed_shot: +10% pen за ранг",
                        Spec2Effect.of("kit_pen_phys", "aimed_shot", 10.0)),
                // Ряд 3 (гейт 10)
                n("mm_arrow_fan", T, 3, 1, 1, Map.of(), "unlock_ability",
                        "Веер стрел", "перенос: 3 стрелы конусом (старый кит, слот 4)",
                        Spec2Effect.of("unlock_ability", "arrow_fan", 1.0)),
                n("mm_master_marksman", T, 3, 2, 3, Map.of("mm_lethal_shots", 1), "passive_stat",
                        "Мастер-стрелок", "+3% урона стрел за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 3.0)),
                n("mm_rapid_fire", T, 3, 3, 2, Map.of("mm_arrow_fan", 1), "enhance_ability",
                        "Скорострельность", "arrow_fan: +1 стрела за ранг (3→4→5)",
                        Spec2Effect.of("kit_extra_arrows", "arrow_fan", 1.0)),
                n("mm_silencing_shot", T, 3, 4, 1, Map.of("mm_piercing_shots", 1), "unlock_ability",
                        "Заглушающий выстрел", "НОВАЯ: SILENCE 3 с, КД 25 с",
                        Spec2Effect.of("unlock_ability", "silencing_shot", 1.0)),
                // Ряд 4 (гейт 15)
                n("mm_barrage", T, 4, 1, 2, Map.of("mm_arrow_fan", 1), "enhance_ability",
                        "Шквал", "arrow_fan: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "arrow_fan", 0.15)),
                n("mm_careful_aim", T, 4, 2, 2, Map.of("mm_master_marksman", 1), "passive_stat",
                        "Тщательный прицел", "aimed_shot: +10% base за ранг",
                        Spec2Effect.of("kit_base", "aimed_shot", 10.0)),
                n("mm_chimera_shot", T, 4, 3, 1, Map.of("mm_rapid_fire", 1), "unlock_ability",
                        "Выстрел химеры", "НОВАЯ: физ+маг урон + bleed, КД 12 с",
                        Spec2Effect.of("unlock_ability", "chimera_shot", 1.0)),
                n("mm_sniper_training", T, 4, 4, 2, Map.of("mm_silencing_shot", 1), "passive_stat",
                        "Снайперская подготовка", "crit +2% за ранг",
                        Spec2Effect.of("crit_ranged_pct", "self", 2.0)),
                // Ряд 5 (гейт 20)
                n("mm_true_shot", T, 5, 1, 1, Map.of("mm_barrage", 1), "unlock_ability",
                        "Верный выстрел", "НОВАЯ: +30% урона на 6 с, КД 60 с",
                        Spec2Effect.of("unlock_ability", "true_shot", 1.0)),
                n("mm_headshot", T, 5, 2, 3, Map.of("mm_careful_aim", 1), "passive_proc",
                        "Выстрел в голову", "15% за ранг: крит ×2 урона",
                        Spec2Effect.of("proc_headshot", "self", 0.15)),
                n("mm_penetrating_shots", T, 5, 3, 3, Map.of("mm_chimera_shot", 1), "passive_stat",
                        "Пронзающие выстрелы", "pen_phys +5% за ранг",
                        Spec2Effect.of("pen_phys_pct", "self", 5.0)),
                n("mm_master_of_arrows", T, 5, 4, 2, Map.of("mm_sniper_training", 1), "enhance_ability",
                        "Мастер стрел", "arrow_fan: −5 с КД за ранг",
                        Spec2Effect.of("kit_cd", "arrow_fan", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("mm_arrow_rain", T, 6, 2, 1, Map.of("mm_true_shot", 1, "mm_headshot", 2), "ultimate",
                        "Дождь стрел", "ульт: AoE радиус 5 (перенос старого кита), КД 90 с",
                        Spec2Effect.of("unlock_ability", "arrow_rain", 1.0))
        ));
    }

    /* ============ ОХОТНИК · ВЫЖИВАНИЕ (survival) — ёмкость 50 ============ */
    private static Spec2Tree survival() {
        final String T = "survival";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("sv_endurance", T, 1, 1, 6, Map.of(), "passive_stat",
                        "Выносливость", "+5% maxHP за ранг",
                        Spec2Effect.of("hp_pct", "self", 5.0)),
                n("sv_trap_mastery", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Мастер ловушек", "+10% урона ловушек за ранг",
                        Spec2Effect.of("trap_dmg_pct", "self", 10.0)),
                n("sv_naturalist", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Натуралист", "+2% NATURE-резиста за ранг",
                        Spec2Effect.of("resist", "nature", 2.0)),
                n("sv_survival_instincts", T, 1, 4, 3, Map.of(), "passive_stat",
                        "Инстинкты выживания", "+3% уклонения за ранг",
                        Spec2Effect.of("avoid", "dodge", 3.0)),
                // Ряд 2 (гейт 5)
                n("sv_poison_shot", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Отравленный выстрел", "НОВАЯ: урон + DoT poison 5 с, КД 8 с",
                        Spec2Effect.of("unlock_ability", "poison_shot", 1.0)),
                n("sv_counterattack", T, 2, 2, 3, Map.of(), "passive_proc",
                        "Контратака", "15% за ранг: при уклонении +50% урона след. удара",
                        Spec2Effect.of("proc_counterattack", "self", 0.15)),
                n("sv_noxious_stings", T, 2, 3, 2, Map.of("sv_poison_shot", 1), "enhance_ability",
                        "Ядовитые жала", "poison_shot: +2 с DoT за ранг",
                        Spec2Effect.of("dot_dur", "poison", 2.0)),
                // Ряд 3 (гейт 10)
                n("sv_explosive_trap", T, 3, 1, 1, Map.of("sv_trap_mastery", 2), "unlock_ability",
                        "Взрывная ловушка", "НОВАЯ: AoE FIRE + ROOT 3 с, КД 30 с",
                        Spec2Effect.of("unlock_ability", "explosive_trap", 1.0)),
                n("sv_trap_expert", T, 3, 2, 3, Map.of("sv_explosive_trap", 1), "passive_stat",
                        "Эксперт ловушек", "explosive_trap: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "explosive_trap", 0.15)),
                n("sv_resourcefulness", T, 3, 3, 2, Map.of("sv_counterattack", 1), "passive_stat",
                        "Находчивость", "КД ловушек −10% за ранг",
                        Spec2Effect.of("cd_trap_pct", "self", 10.0)),
                n("sv_savage_strikes", T, 3, 4, 3, Map.of("sv_noxious_stings", 1), "passive_proc",
                        "Дикие удары", "10% за ранг: crit +30% урона",
                        Spec2Effect.of("proc_savage", "self", 0.10)),
                // Ряд 4 (гейт 15)
                n("sv_black_arrow", T, 4, 1, 2, Map.of("sv_explosive_trap", 1), "unlock_ability",
                        "Чёрная стрела", "НОВАЯ: DoT SHADOW 8 с, КД 15 с",
                        Spec2Effect.of("unlock_ability", "black_arrow", 1.0)),
                n("sv_surefooted", T, 4, 2, 2, Map.of("sv_trap_expert", 1), "passive_stat",
                        "Твёрдый шаг", "+5% уклонения за ранг",
                        Spec2Effect.of("avoid", "dodge", 5.0)),
                n("sv_wyvern_sting", T, 4, 3, 1, Map.of("sv_resourcefulness", 1), "unlock_ability",
                        "Укус виверны", "НОВАЯ: SLEEP 6 с (break on damage), КД 60 с",
                        Spec2Effect.of("unlock_ability", "wyvern_sting", 1.0)),
                n("sv_survivor", T, 4, 4, 2, Map.of("sv_savage_strikes", 1), "passive_stat",
                        "Выживший", "cc_resist +5% за ранг",
                        Spec2Effect.of("cc_resist", "self", 5.0)),
                // Ряд 5 (гейт 20)
                n("sv_expose_weakness", T, 5, 1, 3, Map.of("sv_black_arrow", 1), "passive_proc",
                        "Обнаружение слабости", "20% за ранг: цель получает +5% урона 6 с",
                        Spec2Effect.of("proc_expose", "target", 0.20)),
                n("sv_readiness", T, 5, 2, 2, Map.of("sv_surefooted", 1), "enhance_ability",
                        "Готовность", "сброс КД всех ловушек, КД 180 с",
                        Spec2Effect.of("unlock_ability", "readiness", 1.0)),
                n("sv_hunters_mark", T, 5, 3, 2, Map.of("sv_wyvern_sting", 1), "enhance_ability",
                        "Метка охотника", "wolf_mark: +10% урона за ранг",
                        Spec2Effect.of("kit_mult", "wolf_mark", 0.10)),
                n("sv_master_trapper", T, 5, 4, 3, Map.of("sv_survivor", 1), "passive_stat",
                        "Мастер-ловчий", "ROOT-длительность +1 с за ранг",
                        Spec2Effect.of("cc_dur", "root", 1.0)),
                // Ряд 6 (гейт 30) — ульт
                n("sv_serpent_sting", T, 6, 2, 1, Map.of("sv_expose_weakness", 2, "sv_master_trapper", 2), "ultimate",
                        "Укус змеи", "ульт: DoT NATURE, урон = стеки poison ×3, КД 120 с",
                        Spec2Effect.of("unlock_ability", "serpent_sting", 1.0))
        ));
    }

    /* ============ ОХОТНИК · ПОВЕЛИТЕЛЬ ЗВЕРЕЙ (beastmaster) — ёмкость 50 ============ */
    private static Spec2Tree beastmaster() {
        final String T = "beastmaster";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("bm_animal_handler", T, 1, 1, 6, Map.of(), "passive_stat",
                        "Дрессировщик", "+5% урона питомца за ранг",
                        Spec2Effect.of("pet_dmg_pct", "self", 5.0)),
                n("bm_ferocity", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Свирепость", "+2% физ-урона за ранг",
                        Spec2Effect.of("phys_dmg_pct", "self", 2.0)),
                n("bm_thick_hide", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Толстая шкура", "+3% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 3.0)),
                n("bm_wild_bond", T, 1, 4, 3, Map.of(), "passive_proc",
                        "Дикая связь", "5% за ранг: питомец копирует 10% урона охотника",
                        Spec2Effect.of("proc_pet_copy", "pet", 0.05)),
                // Ряд 2 (гейт 5)
                n("bm_intimidation", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Запугивание", "НОВАЯ: STUN 1 с + агро-передача, КД 30 с",
                        Spec2Effect.of("unlock_ability", "intimidation", 1.0)),
                n("bm_focused_fire", T, 2, 2, 3, Map.of(), "passive_stat",
                        "Сосредоточенный огонь", "+3% урона питомца за ранг",
                        Spec2Effect.of("pet_dmg_pct", "self", 3.0)),
                n("bm_endurance_training", T, 2, 3, 2, Map.of("bm_intimidation", 1), "enhance_ability",
                        "Тренировка выносливости", "питомец: +10% maxHP за ранг",
                        Spec2Effect.of("pet_hp_pct", "self", 10.0)),
                // Ряд 3 (гейт 10)
                n("bm_pet_wolf", T, 3, 1, 1, Map.of("bm_animal_handler", 2), "unlock_ability",
                        "Приручить волка", "открывает питомца-волка (vanilla wolf + MM-скин)",
                        Spec2Effect.of("unlock_ability", "pet_wolf", 1.0)),
                n("bm_bestial_discipline", T, 3, 2, 3, Map.of("bm_pet_wolf", 1), "passive_stat",
                        "Звериная дисциплина", "питомец: +5% скорости атаки за ранг",
                        Spec2Effect.of("pet_attack_speed_pct", "self", 5.0)),
                n("bm_ferocious_inspiration", T, 3, 3, 2, Map.of("bm_focused_fire", 1), "passive_proc",
                        "Свирепое вдохновение", "15% за ранг: крит питомца даёт +3% урона группе 10 с",
                        Spec2Effect.of("proc_group_buff", "group", 0.15)),
                n("bm_cobra_reflexes", T, 3, 4, 3, Map.of("bm_endurance_training", 1), "passive_stat",
                        "Рефлексы кобры", "питомец: +5% уклонения за ранг",
                        Spec2Effect.of("pet_dodge_pct", "self", 5.0)),
                // Ряд 4 (гейт 15)
                n("bm_beast_ferocity", T, 4, 1, 1, Map.of("bm_pet_wolf", 1), "unlock_ability",
                        "Звериная свирепость", "НОВАЯ: питомец ×1.5 урона 10 с, КД 60 с",
                        Spec2Effect.of("unlock_ability", "beast_ferocity", 1.0)),
                n("bm_spirit_bond", T, 4, 2, 2, Map.of("bm_bestial_discipline", 1), "passive_proc",
                        "Духовная связь", "10% за ранг: хил охотника лечит питомца на 50%",
                        Spec2Effect.of("proc_pet_heal", "pet", 0.10)),
                n("bm_go_for_the_throat", T, 4, 3, 2, Map.of("bm_ferocious_inspiration", 1), "passive_proc",
                        "В глотку", "20% за ранг: crit питомца +50% урона",
                        Spec2Effect.of("proc_pet_crit_bonus", "pet", 0.20)),
                n("bm_primal_fury", T, 4, 4, 2, Map.of("bm_cobra_reflexes", 1), "passive_stat",
                        "Первобытная ярость", "питомец: +10% физ-урона за ранг",
                        Spec2Effect.of("pet_phys_dmg_pct", "self", 10.0)),
                // Ряд 5 (гейт 20)
                n("bm_the_beast_within", T, 5, 1, 2, Map.of("bm_beast_ferocity", 1), "enhance_ability",
                        "Зверь внутри", "beast_ferocity: +5 с за ранг",
                        Spec2Effect.of("kit_dur", "beast_ferocity", 5.0)),
                n("bm_frenzy", T, 5, 2, 3, Map.of("bm_spirit_bond", 1), "passive_proc",
                        "Бешенство", "25% за ранг: crit питомца +30% скорости атаки 8 с",
                        Spec2Effect.of("proc_pet_frenzy", "pet", 0.25)),
                n("bm_pack_leader", T, 5, 3, 2, Map.of("bm_go_for_the_throat", 1), "passive_stat",
                        "Вожак стаи", "питомец: +5% crit за ранг",
                        Spec2Effect.of("pet_crit_pct", "self", 5.0)),
                n("bm_wilderness_survival", T, 5, 4, 3, Map.of("bm_primal_fury", 1), "passive_stat",
                        "Выживание в глуши", "+5% NATURE/FIRE резиста за ранг",
                        Spec2Effect.of("resist", "nature", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("bm_bestial_wrath", T, 6, 2, 1, Map.of("bm_the_beast_within", 1, "bm_frenzy", 2), "ultimate",
                        "Звериная ярость", "ульт: питомец ×2 урона 8 с + иммунитет к CC, КД 180 с",
                        Spec2Effect.of("unlock_ability", "bestial_wrath", 1.0))
        ));
    }
}
