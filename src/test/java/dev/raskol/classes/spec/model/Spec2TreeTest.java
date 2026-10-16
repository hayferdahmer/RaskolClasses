// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.registry.Spec2Registry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.10 (Sprint 7, волна 7.1, P0-11-01): unit-тесты модели деревьев.
 * Главный объект — регрессия 6-01: отключённый пререквизит считается выполненным
 * в prereqsMet/canBuy, иначе ульты снова станут недостижимы.
 * Реестр деревьев и Spec2Points — pure-классы, Bukkit не требуется.
 */
@DisplayName("Spec2Tree: ёмкость, пререквизиты, достижимость")
class Spec2TreeTest {

    @Test
    @DisplayName("все 18 деревьев в реестре, ёмкость в [50, 58] (инвариант P0-4)")
    void registryCapacityInvariant() {
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            assertNotNull(t, "дерево отсутствует: " + s.id());
            assertTrue(t.capacity() >= 50 && t.capacity() <= 58,
                    "ёмкость " + s.id() + " = " + t.capacity() + ", ожидалось [50, 58]");
        }
    }

    @Test
    @DisplayName("spentInTree игнорирует неизвестные id")
    void spentInTreeIgnoresUnknownIds() {
        Spec2Tree beast = Spec2Registry.treeOf("beastmaster");
        assertNotNull(beast);
        Spec2Node any = beast.nodes().get(0);
        Map<String, Integer> ranks = new HashMap<>();
        ranks.put(any.id(), 3);
        ranks.put("nonexistent_node_xyz", 9);
        assertTrue(beast.spentInTree(ranks) == 3,
                "spent = " + beast.spentInTree(ranks) + ", ожидалось 3");
    }

    @Test
    @DisplayName("6-01: disabled-пререквизит считается выполненным (beastmaster)")
    void disabledPrereqCountsAsMetBeastmaster() {
        Spec2Tree beast = Spec2Registry.treeOf("beastmaster");
        assertNotNull(beast);
        Spec2Node ult = beast.find("bm_bestial_wrath");
        assertNotNull(ult, "ульт beastmaster не найден");
        Map<String, Integer> empty = new HashMap<>();
        assertFalse(beast.prereqsMet(ult, empty, Set.of()),
                "без рангов и без стоп-листа пререквизит не выполнен");
        assertTrue(beast.prereqsMet(ult, empty, Set.of("bm_frenzy")),
                "disabled-пререквизит bm_frenzy должен считаться выполненным");
    }

    @Test
    @DisplayName("6-01: disabled-пререквизит считается выполненным (subtlety)")
    void disabledPrereqCountsAsMetSubtlety() {
        Spec2Tree sub = Spec2Registry.treeOf("subtlety");
        assertNotNull(sub);
        Spec2Node ult = sub.find("su_shadow_blades");
        assertNotNull(ult, "ульт subtlety не найден");
        Map<String, Integer> empty = new HashMap<>();
        assertFalse(sub.prereqsMet(ult, empty, Set.of()));
        assertTrue(sub.prereqsMet(ult, empty, Set.of("su_master_of_subtlety")));
    }

    @Test
    @DisplayName("isReachable: сам узел в стоп-листе → false; цепочка с disabled-пререком → true")
    void isReachablePolicy() {
        Spec2Tree beast = Spec2Registry.treeOf("beastmaster");
        assertNotNull(beast);
        assertFalse(beast.isReachable("bm_frenzy", Set.of("bm_frenzy")),
                "отключённый узел недостижим по определению");
        assertTrue(beast.isReachable("bm_bestial_wrath", Set.of("bm_frenzy")),
                "ульт достижим при disabled-пререквите");
        assertFalse(beast.isReachable("no_such_node", Set.of()),
                "несуществующий узел недостижим");
    }

    @Test
    @DisplayName("canBuy: ряд-гейт и очки соблюдаются (ульт beastmaster)")
    void canBuyRowGateAndPoints() {
        Spec2Tree beast = Spec2Registry.treeOf("beastmaster");
        assertNotNull(beast);
        Spec2Node ult = beast.find("bm_bestial_wrath");
        assertNotNull(ult);

        // Пустые ранги: ульт в ряду 6 (гейт 30) — покупка закрыта даже с очками.
        Map<String, Integer> empty = new HashMap<>();
        assertFalse(beast.canBuy(ult, empty, 5, Set.of("bm_frenzy")),
                "ряд 6 закрыт при нуле очков в дереве");

        // Заполняем ряды 1–5 до максимума: spent ≥ 30, гейт ряда 6 открыт.
        Map<String, Integer> full = new HashMap<>();
        for (Spec2Node n : beast.nodes()) {
            if (n.row() < 6) {
                full.put(n.id(), n.maxRank());
            }
        }
        int spent = beast.spentInTree(full);
        assertTrue(Spec2Points.rowUnlocked(ult.row(), spent),
                "spent = " + spent + " должен открывать ряд 6");
        assertTrue(beast.canBuy(ult, full, 1, Set.of("bm_frenzy")),
                "при открытом ряде и выполненном (disabled) пререквите покупка возможна");
        assertFalse(beast.canBuy(ult, full, 0, Set.of("bm_frenzy")),
                "без свободного очка покупка закрыта");
    }
}
