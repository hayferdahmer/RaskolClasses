// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.spec.model.Spec2Effect;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Tree;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0: деревья Чернокнижника (3 спеки), ёмкости 51/51/51 (>46 — закрыть нельзя).
 * affliction (Колдовство): DoT wither (SHADOW, dots.wither из конфига 1.14.0-Б7),
 *         unlock withering / unwriting (перенос + SILENCE-тег) / soul_siphon,
 *         ульт soul_harvest (детонация всех wither-стеков).
 * destruction (Разрушение): burst FIRE + burning, unlock immolate / chaos_bolt /
 *         conflagrate, ульт soul_rift (перенос старого кита, слот 5).
 * demonology (Демонология): петы + Скверна, unlock dreadfire / summon_demon /
 *         demonic_pact, ульт demon_soul (слияние с петом 10 с).
 * Пет-подсистема (summon_demon/shadowfiend/pet_wolf) реализуется в 1.14.6;
 * узлы уже открывают способности-заглушки через unlock_ability.
 */
public final class WarlockTrees {

    private WarlockTrees() {
    }

    public static void register(Map<String, Spec2Tree> into) {
        into.put("affliction", affliction());
        into.put("destruction", destruction());
        into.put("demonology", demonology());
    }

    private static Spec2Node n(String id, String treeId, int row, int col, int maxRank,
                               Map<String, Integer> prereqs, String type,
                               String name, String lore, Spec2Effect effect) {
        return new Spec2Node(id, treeId, row, col, maxRank, prereqs, type, name, lore, effect);
    }

    /* ============ ЧЕРНОКНИЖНИК · КОЛДОВСТВО (affliction) — ёмкость 51 ============ */
    private static Spec2Tree affliction() {
        final String T = "affliction";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("af_corruption_training", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Школа порчи", "+10% урона wither за ранг",
                        Spec2Effect.of("dot_mult", "wither", 10.0)),
                n("af_shadow_mind", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Теневой разум", "+4 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 4.0)),
                n("af_soul_drain", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Дренаж души", "+1 Скверны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("af_dark_embrace", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Тёмные объятия", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                // Ряд 2 (гейт 5)
                n("af_withering", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Иссушение", "НОВАЯ: маг-урон + DoT wither 6 с, КД 6 с",
                        Spec2Effect.of("unlock_ability", "withering", 1.0)),
                n("af_agony", T, 2, 2, 3, Map.of("af_corruption_training", 2), "passive_stat",
                        "Агония", "+1 с длительности wither за ранг",
                        Spec2Effect.of("dot_dur", "wither", 1.0)),
                n("af_unstable_affliction", T, 2, 3, 2, Map.of(), "passive_stat",
                        "Нестабильная порча", "+1 стек wither за ранг (кап 3→4→5)",
                        Spec2Effect.of("dot_stacks", "wither", 1.0)),
                n("af_nightfall", T, 2, 4, 3, Map.of("af_shadow_mind", 2), "passive_stat",
                        "Сумерки", "+1 Скверны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                // Ряд 3 (гейт 10)
                n("af_withering_enh", T, 3, 1, 3, Map.of("af_withering", 1), "enhance_ability",
                        "Усиленное иссушение", "withering: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "withering", 0.15)),
                n("af_soul_conduit", T, 3, 2, 2, Map.of("af_agony", 1), "passive_stat",
                        "Проводник душ", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                n("af_malefic", T, 3, 3, 3, Map.of("af_unstable_affliction", 1), "passive_stat",
                        "Зловредность", "+2% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                n("af_corruptor", T, 3, 4, 2, Map.of("af_nightfall", 1), "passive_stat",
                        "Развратитель", "pen_magic +4% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 4.0)),
                // Ряд 4 (гейт 15)
                n("af_unwriting", T, 4, 1, 1, Map.of("af_withering_enh", 1), "unlock_ability",
                        "Небытие", "перенос: диспел бафов + урон за стёртый (старый кит, слот 4)",
                        Spec2Effect.of("unlock_ability", "unwriting", 1.0)),
                n("af_haunt", T, 4, 2, 2, Map.of("af_soul_conduit", 1), "enhance_ability",
                        "Призрак", "withering: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "withering", 0.20)),
                n("af_drain_mastery", T, 4, 3, 2, Map.of("af_malefic", 1), "passive_stat",
                        "Мастерство дренажа", "+10% урона wither за ранг",
                        Spec2Effect.of("dot_mult", "wither", 10.0)),
                n("af_dark_pact", T, 4, 4, 2, Map.of("af_corruptor", 1), "passive_stat",
                        "Тёмный пакт", "+2% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 2.0)),
                // Ряд 5 (гейт 20)
                n("af_soul_siphon", T, 5, 1, 1, Map.of("af_unwriting", 1), "unlock_ability",
                        "Сифон души", "НОВАЯ: дрейн маны цели → Скверна, КД 30 с",
                        Spec2Effect.of("unlock_ability", "soul_siphon", 1.0)),
                n("af_malefic_grasp", T, 5, 2, 3, Map.of("af_haunt", 1), "passive_stat",
                        "Злой захват", "+2 с длительности wither за ранг",
                        Spec2Effect.of("dot_dur", "wither", 2.0)),
                n("af_shadow_mastery", T, 5, 3, 3, Map.of("af_drain_mastery", 1), "passive_stat",
                        "Мастерство тени", "+4% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 4.0)),
                // Ряд 6 (гейт 30) — ульт
                n("af_soul_harvest", T, 6, 2, 1,
                        Map.of("af_soul_siphon", 1, "af_malefic_grasp", 2), "ultimate",
                        "Жатва душ", "ульт: детонация всех wither-стеков цели ×3 урона, КД 120 с",
                        Spec2Effect.of("unlock_ability", "soul_harvest", 1.0))
        ));
    }

    /* ============ ЧЕРНОКНИЖНИК · РАЗРУШЕНИЕ (destruction) — ёмкость 51 ============ */
    private static Spec2Tree destruction() {
        final String T = "destruction";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("de_immolate_training", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Школа жертвенного огня", "+10% урона burning за ранг",
                        Spec2Effect.of("dot_mult", "burning", 10.0)),
                n("de_aftermath", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Последствия", "+4 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 4.0)),
                n("de_ember_master", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Мастер углей", "+2% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                n("de_molten_ward", T, 1, 4, 4, Map.of(), "passive_stat",
                        "Расплавленный оберег", "+3% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 3.0)),
                // Ряд 2 (гейт 5)
                n("de_immolate", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Жертвенный огонь", "НОВАЯ: маг-урон + burning 5 с, КД 5 с",
                        Spec2Effect.of("unlock_ability", "immolate", 1.0)),
                n("de_backdraft", T, 2, 2, 3, Map.of("de_immolate_training", 2), "enhance_ability",
                        "Обратная тяга", "black_word: −10% кулдауна за ранг",
                        Spec2Effect.of("cd", "black_word", 0.10)),
                n("de_fire_brimstone", T, 2, 3, 2, Map.of(), "enhance_ability",
                        "Огонь и сера", "ruin_seal: +15% амплификации за ранг",
                        Spec2Effect.of("kit_mult", "ruin_seal", 0.15)),
                n("de_chaos_prep", T, 2, 4, 3, Map.of("de_aftermath", 2), "passive_stat",
                        "Подготовка хаоса", "pen_magic +4% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 4.0)),
                // Ряд 3 (гейт 10)
                n("de_immolate_enh", T, 3, 1, 3, Map.of("de_immolate", 1), "enhance_ability",
                        "Усиленный жертвенный огонь", "immolate: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "immolate", 0.15)),
                n("de_incinerate", T, 3, 2, 2, Map.of("de_backdraft", 1), "passive_stat",
                        "Испепеление", "+1 с длительности burning за ранг",
                        Spec2Effect.of("dot_dur", "burning", 1.0)),
                n("de_emberstorm", T, 3, 3, 3, Map.of("de_fire_brimstone", 1), "passive_stat",
                        "Шторм углей", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("de_flame_rift", T, 3, 4, 2, Map.of("de_chaos_prep", 1), "passive_stat",
                        "Пламенный разлом", "+1 стек burning за ранг (кап 3→4→5)",
                        Spec2Effect.of("dot_stacks", "burning", 1.0)),
                // Ряд 4 (гейт 15)
                n("de_chaos_bolt", T, 4, 1, 1, Map.of("de_immolate_enh", 1), "unlock_ability",
                        "Стрела хаоса", "НОВАЯ: маг-урон, игнор 20% маг-резиста, КД 12 с",
                        Spec2Effect.of("unlock_ability", "chaos_bolt", 1.0)),
                n("de_chaos_prep_enh", T, 4, 2, 2, Map.of("de_incinerate", 1), "enhance_ability",
                        "Усиленная стрела хаоса", "chaos_bolt: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "chaos_bolt", 0.20)),
                n("de_ruin", T, 4, 3, 2, Map.of("de_emberstorm", 1), "passive_stat",
                        "Разрушение", "+3% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 3.0)),
                n("de_ashen_veil", T, 4, 4, 2, Map.of("de_flame_rift", 1), "passive_stat",
                        "Пепельная вуаль", "+2% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 2.0)),
                // Ряд 5 (гейт 20)
                n("de_conflagrate", T, 5, 1, 1, Map.of("de_chaos_bolt", 1), "unlock_ability",
                        "Конфлаграция", "НОВАЯ: детонация burning-стеков, КД 10 с",
                        Spec2Effect.of("unlock_ability", "conflagrate", 1.0)),
                n("de_cataclysm_prep", T, 5, 2, 3, Map.of("de_chaos_prep_enh", 1), "passive_stat",
                        "Подготовка катаклизма", "+15% урона burning за ранг",
                        Spec2Effect.of("dot_mult", "burning", 15.0)),
                n("de_shadowburn_mastery", T, 5, 3, 3, Map.of("de_ruin", 1), "passive_stat",
                        "Мастерство выжигания", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("de_soul_rift", T, 6, 2, 1,
                        Map.of("de_conflagrate", 1, "de_cataclysm_prep", 2), "ultimate",
                        "Раскол Души", "ульт: перенос старого кита (слот 5), канал + анти-хил",
                        Spec2Effect.of("unlock_ability", "soul_rift", 1.0))
        ));
    }

    /* ============ ЧЕРНОКНИЖНИК · ДЕМОНОЛОГИЯ (demonology) — ёмкость 51 ============ */
    private static Spec2Tree demonology() {
        final String T = "demonology";
        return new Spec2Tree(T, List.of(
                // Ряд 1 (гейт 0)
                n("dm_demonic_embrace", T, 1, 1, 5, Map.of(), "passive_stat",
                        "Демонические объятия", "+4% маг-резиста за ранг",
                        Spec2Effect.of("resist", "magic", 4.0)),
                n("dm_dark_pact", T, 1, 2, 4, Map.of(), "passive_stat",
                        "Тёмный пакт", "+4 ИНТЕЛЛЕКТА за ранг",
                        Spec2Effect.of("attr", "int", 4.0)),
                n("dm_fel_synergy", T, 1, 3, 4, Map.of(), "passive_stat",
                        "Скверновая синергия", "+1 Скверны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                n("dm_conjuror_prep", T, 1, 4, 4, Map.of(), "enhance_ability",
                        "Подготовка призывателя", "ruin_seal: −10% кулдауна за ранг",
                        Spec2Effect.of("cd", "ruin_seal", 0.10)),
                // Ряд 2 (гейт 5)
                n("dm_dreadfire", T, 2, 1, 1, Map.of(), "unlock_ability",
                        "Грозный огонь", "НОВАЯ: маг-урон + burning (SHADOW-окрас), КД 7 с",
                        Spec2Effect.of("unlock_ability", "dreadfire", 1.0)),
                n("dm_soul_link", T, 2, 2, 3, Map.of("dm_demonic_embrace", 2), "passive_stat",
                        "Связь души", "+3% физ/маг резиста за ранг",
                        Spec2Effect.of("resist", "both", 3.0)),
                n("dm_demonic_knowledge", T, 2, 3, 2, Map.of(), "enhance_ability",
                        "Демоническое знание", "unwriting: +6 к базе за ранг",
                        Spec2Effect.of("kit_base", "unwriting", 6.0)),
                n("dm_felguard_prep", T, 2, 4, 3, Map.of("dm_dark_pact", 2), "passive_stat",
                        "Подготовка стража", "+2% маг-урона за ранг",
                        Spec2Effect.of("magic_dmg_pct", "self", 2.0)),
                // Ряд 3 (гейт 10)
                n("dm_dreadfire_enh", T, 3, 1, 3, Map.of("dm_dreadfire", 1), "enhance_ability",
                        "Усиленный грозный огонь", "dreadfire: +15% урона за ранг",
                        Spec2Effect.of("kit_mult", "dreadfire", 0.15)),
                n("dm_metamorphosis_prep", T, 3, 2, 2, Map.of("dm_soul_link", 1), "passive_stat",
                        "Подготовка метаморфозы", "+5% maxHP за ранг",
                        Spec2Effect.of("hp_pct", "self", 5.0)),
                n("dm_summon_prep", T, 3, 3, 3, Map.of("dm_demonic_knowledge", 1), "passive_stat",
                        "Подготовка призыва", "+3% SP за ранг",
                        Spec2Effect.of("sp_pct", "self", 3.0)),
                n("dm_demonic_resilience", T, 3, 4, 2, Map.of("dm_felguard_prep", 1), "passive_stat",
                        "Демоническая стойкость", "+4% физ-резиста за ранг",
                        Spec2Effect.of("resist", "phys", 4.0)),
                // Ряд 4 (гейт 15)
                n("dm_summon_demon", T, 4, 1, 1, Map.of("dm_dreadfire_enh", 1), "unlock_ability",
                        "Призыв демона", "открывает пета-демона (подсистема 1.14.6)",
                        Spec2Effect.of("unlock_ability", "summon_demon", 1.0)),
                n("dm_demonic_empowerment", T, 4, 2, 2, Map.of("dm_metamorphosis_prep", 1), "enhance_ability",
                        "Демоническое усиление", "black_word: +20% урона за ранг",
                        Spec2Effect.of("kit_mult", "black_word", 0.20)),
                n("dm_master_summoner", T, 4, 3, 2, Map.of("dm_summon_prep", 1), "enhance_ability",
                        "Мастер призыва", "dreadfire: −3 с кулдауна за ранг",
                        Spec2Effect.of("kit_cd", "dreadfire", 3.0)),
                n("dm_fel_pact", T, 4, 4, 2, Map.of("dm_demonic_resilience", 1), "passive_stat",
                        "Пакт скверны", "+1 Скверны/с за ранг",
                        Spec2Effect.of("regen", "resource", 1.0)),
                // Ряд 5 (гейт 20)
                n("dm_demonic_pact", T, 5, 1, 1, Map.of("dm_summon_demon", 1), "unlock_ability",
                        "Демонический пакт", "НОВАЯ: бафф от пета +10% SP группе 10 с, КД 60 с",
                        Spec2Effect.of("unlock_ability", "demonic_pact", 1.0)),
                n("dm_metamorphosis", T, 5, 2, 3, Map.of("dm_demonic_empowerment", 1), "passive_stat",
                        "Метаморфоза", "+5% maxHP за ранг",
                        Spec2Effect.of("hp_pct", "self", 5.0)),
                n("dm_grimoire", T, 5, 3, 3, Map.of("dm_master_summoner", 1), "passive_stat",
                        "Гримуар", "pen_magic +5% за ранг",
                        Spec2Effect.of("pen_magic_pct", "self", 5.0)),
                // Ряд 6 (гейт 30) — ульт
                n("dm_demon_soul", T, 6, 2, 1,
                        Map.of("dm_demonic_pact", 1, "dm_metamorphosis", 2), "ultimate",
                        "Душа демона", "ульт: слияние с петом 10 с (×2 урон, иммунитет CC), КД 180 с",
                        Spec2Effect.of("unlock_ability", "demon_soul", 1.0))
        ));
    }
}
