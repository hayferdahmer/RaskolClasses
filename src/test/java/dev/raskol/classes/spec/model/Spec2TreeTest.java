// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.registry.Spec2Registry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.10 (Sprint 7, волна 7.1, P0-11-01): unit-тесты модели деревьев.
 * Главный объект — регрессия 6-01: отключённый пререквизит считается выполненным
 * в prereqsMet/canBuy, иначе ульты снова станут недостижимы.
 *
 * 1.14.10-fix: prereq-id'ы больше не хардкодятся — берутся из node.prereqs().
 * Раньше тесты падали при расхождении отчёта Claude и реальных деревьев
 * (например, bm_bestial_wrath требует не bm_frenzy, а bm_beast_within).
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

    /**
     * 6-01: для КАЖДОГО узла с пререквизитами — если ВСЕ его prereq'и попадают
     * в стоп-лист, prereqsMet возвращает true (как если бы prereq'и были куплены).
     * Устойчив к переименованию prereq-id'ов в деревьях.
     */
    @Test
    @DisplayName("6-01: disabled-пререквизиты считаются выполненными (любое дерево)")
    void disabledPrereqCountsAsMet() {
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            assertNotNull(t);
            for (Spec2Node node : t.nodes()) {
                if (node.prereqs().isEmpty()) {
                    continue;
                }
                // без рангов и без стоп-листа: пререквизиты не выполнены
                Map<String, Integer> empty = new HashMap<>();
                assertFalse(t.prereqsMet(node, empty, Set.of()),
                        "узел " + s.id() + ":" + node.id() + " не должен быть доступен без рангов");

                // все prereq'и узла в стоп-листе → выполнены
                Set<String> allPrereqsDisabled = new HashSet<>(node.prereqs().keySet());
                assertTrue(t.prereqsMet(node, empty, allPrereqsDisabled),
                        "узел " + s.id() + ":" + node.id()
                                + ": disabled prereq'и " + allPrereqsDisabled + " должны считаться выполненными");
            }
        }
    }

    @Test
    @DisplayName("isReachable: сам узел в стоп-листе → false; цепочка с disabled-пререками → true")
    void isReachablePolicy() {
        Spec2Tree beast = Spec2Registry.treeOf("beastmaster");
        assertNotNull(beast);

        // Берём любой узел с prereq'ами, собираем его полную цепочку prereq'ов рекурсивно
        Spec2Node withPrereqs = null;
        for (Spec2Node n : beast.nodes()) {
            if (!n.prereqs().isEmpty()) {
                withPrereqs = n;
                break;
            }
        }
        assertNotNull(withPrereqs, "у beastmaster должен быть узел с prereq'ами");

        // собираем все prereq'и по цепочке (включая транзитивные)
        Set<String> fullChain = collectPrereqChain(beast, withPrereqs.id());

        // сам узел в стоп-листе → недостижим
        assertFalse(beast.isReachable(withPrereqs.id(), Set.of(withPrereqs.id())),
                "отключённый узел недостижим по определению");

        // вся цепочка prereq'ов в стоп-листе → узел достижим
        assertTrue(beast.isReachable(withPrereqs.id(), fullChain),
                "узел достижим, если вся цепочка prereq'ов отключена");

        // несуществующий узел
        assertFalse(beast.isReachable("no_such_node", Set.of()),
                "несуществующий узел недостижим");
    }

    private static Set<String> collectPrereqChain(Spec2Tree tree, String nodeId) {
        Set<String> visited = new HashSet<>();
        collectPrereqChainRecursive(tree, nodeId, visited);
        visited.remove(nodeId); // сам узел не входит в prereq-цепочку
        return visited;
    }

    private static void collectPrereqChainRecursive(Spec2Tree tree, String nodeId, Set<String> visited) {
        if (!visited.add(nodeId)) {
            return; // цикл / уже обработано
        }
        Spec2Node n = tree.find(nodeId);
        if (n == null) {
            return;
        }
        for (String prereqId : n.prereqs().keySet()) {
            collectPrereqChainRecursive(tree, prereqId, visited);
        }
    }

    @Test
    @DisplayName("canBuy: ряд-гейт и очки соблюдаются (любой узел с prereq'ами)")
    void canBuyRowGateAndPoints() {
        // находим первый узел в ряду 6 (ульт) у любого дерева
        Spec2Tree tree = null;
        Spec2Node ult = null;
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            if (t == null) continue;
            for (Spec2Node n : t.nodes()) {
                if (n.row() == 6) {
                    tree = t;
                    ult = n;
                    break;
                }
            }
            if (ult != null) break;
        }
        assertNotNull(tree, "не найдено дерево с ультом в ряду 6");
        assertNotNull(ult, "ульт в ряду 6 не найден");

        // Пустые ранги: ульт в ряду 6 (гейт 30) — покупка закрыта.
        Map<String, Integer> empty = new HashMap<>();
        Set<String> prereqsDisabled = new HashSet<>(ult.prereqs().keySet());
        assertFalse(tree.canBuy(ult, empty, 5, prereqsDisabled),
                "ряд 6 закрыт при нуле очков в дереве");

        // Заполняем ряды 1–5 до максимума: spent ≥ 30, гейт ряда 6 открыт.
        Map<String, Integer> full = new HashMap<>();
        for (Spec2Node n : tree.nodes()) {
            if (n.row() < 6) {
                full.put(n.id(), n.maxRank());
            }
        }
        int spent = tree.spentInTree(full);
        assertTrue(Spec2Points.rowUnlocked(ult.row(), spent),
                "spent = " + spent + " должен открывать ряд 6");
        assertTrue(tree.canBuy(ult, full, 1, prereqsDisabled),
                "при открытом ряде, заполненных prereq'ах и 1 очке покупка возможна");
        assertFalse(tree.canBuy(ult, full, 0, prereqsDisabled),
                "без свободного очка покупка закрыта");
    }
}
