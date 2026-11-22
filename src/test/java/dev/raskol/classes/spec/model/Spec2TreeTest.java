// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.ability.TransferableAbilities;
import dev.raskol.classes.ability.TreeAbilities;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.registry.Spec2Registry;

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
            assertTrue(
                    t.capacity() >= 50 && t.capacity() <= 58,
                    "ёмкость " + s.id() + " = " + t.capacity() + ", ожидалось [50, 58]");
        }
    }

    @Test
    @DisplayName("все 18 деревьев имеют целостный DAG и уникальную сетку узлов")
    void registryGraphIntegrity() {
        int totalNodes = 0;
        for (Spec spec : Spec.values()) {
            Spec2Tree tree = Spec2Registry.treeOf(spec.id());
            assertNotNull(tree, "дерево отсутствует: " + spec.id());
            Set<String> ids = new HashSet<>();
            Set<String> coordinates = new HashSet<>();
            Map<String, Integer> declarationOrder = new HashMap<>();
            List<Spec2Node> orderedNodes = tree.nodes();
            for (int i = 0; i < orderedNodes.size(); i++) {
                declarationOrder.put(orderedNodes.get(i).id(), i);
            }
            for (Spec2Node node : orderedNodes) {
                assertTrue(ids.add(node.id()), "дублирующийся node id: " + spec.id() + "/" + node.id());
                assertTrue(
                        coordinates.add(node.row() + ":" + node.col()),
                        "дублирующаяся позиция: " + spec.id() + "/" + node.row() + ":" + node.col());
                assertTrue(node.row() >= 1 && node.row() <= 6, "неверный ряд: " + spec.id() + "/" + node.id());
                assertTrue(node.col() >= 1 && node.col() <= 4, "неверный столбец: " + spec.id() + "/" + node.id());
                assertTrue(node.maxRank() > 0, "maxRank должен быть положительным: " + spec.id() + "/" + node.id());
                for (Map.Entry<String, Integer> prereq : node.prereqs().entrySet()) {
                    assertNotNull(
                            tree.find(prereq.getKey()),
                            "отсутствующий prerequisite: " + spec.id() + "/" + node.id() + " -> " + prereq.getKey());
                    assertTrue(
                            prereq.getValue() > 0,
                            "ранг prerequisite должен быть положительным: " + spec.id() + "/" + node.id());
                    Spec2Node prerequisite = tree.find(prereq.getKey());
                    assertTrue(
                            prerequisite.row() < node.row()
                                    || (prerequisite.row() == node.row()
                                            && declarationOrder.get(prerequisite.id())
                                                    < declarationOrder.get(node.id())),
                            "prerequisite должен идти в более раннем ряду или раньше в том же ряду: " + spec.id() + "/"
                                    + node.id() + " -> " + prerequisite.id());
                }
                assertTrue(tree.isReachable(node.id(), Set.of()), "цикл в графе: " + spec.id() + "/" + node.id());
            }
            totalNodes += tree.nodes().size();
        }
        assertEquals(361, totalNodes, "изменение числа узлов должно проходить осознанный review");
    }

    @Test
    @DisplayName("per-tree row gate переопределяет глобальный порог только для своего дерева")
    void perTreeRowGateOverride() {
        Spec2Points.Snapshot snapshot = new Spec2Points.Snapshot(
                15, 46, new int[] {0, 5, 10, 15, 20, 30}, Map.of("beastmaster", new int[] {0, 5, 10, 15, 20, 27}));
        assertEquals(27, snapshot.rowGate(6, "beastmaster"));
        assertEquals(30, snapshot.rowGate(6, "arms"));
        assertEquals(30, snapshot.rowGate(6, null));
    }

    @Test
    @DisplayName("все узлы открытия ведут к уникальным зарегистрированным способностям")
    void unlockAbilityNodesAreUniqueAndRegistered() {
        Set<String> declaredAbilities = new HashSet<>();
        for (PlayerClass playerClass : PlayerClass.values()) {
            declaredAbilities.addAll(TreeAbilities.idsFor(playerClass));
        }
        declaredAbilities.addAll(TransferableAbilities.allIds());

        Set<String> unlockedAbilities = new HashSet<>();
        for (Spec spec : Spec.values()) {
            Spec2Tree tree = Spec2Registry.treeOf(spec.id());
            assertNotNull(tree, "дерево отсутствует: " + spec.id());
            for (Spec2Node node : tree.nodes()) {
                if (node.effect() == null
                        || !"unlock_ability".equals(node.effect().kind())) {
                    continue;
                }
                String abilityId = node.effect().target();
                assertTrue(
                        unlockedAbilities.add(abilityId),
                        "дублирующий узел открытия способности: " + abilityId + " в " + spec.id() + "/" + node.id());
                assertTrue(
                        declaredAbilities.contains(abilityId),
                        "способность не зарегистрирована: " + abilityId + " в " + spec.id() + "/" + node.id());
            }
        }
        assertEquals(81, unlockedAbilities.size(), "ожидалось 69 древесных и 12 перенесённых способностей");
        assertEquals(
                declaredAbilities, unlockedAbilities, "список доступных способностей и узлы открытия должны совпадать");
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
        assertTrue(beast.spentInTree(ranks) == 3, "spent = " + beast.spentInTree(ranks) + ", ожидалось 3");
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
                assertFalse(
                        t.prereqsMet(node, empty, Set.of()),
                        "узел " + s.id() + ":" + node.id() + " не должен быть доступен без рангов");

                // все prereq'и узла в стоп-листе → выполнены
                Set<String> allPrereqsDisabled = new HashSet<>(node.prereqs().keySet());
                assertTrue(
                        t.prereqsMet(node, empty, allPrereqsDisabled),
                        "узел " + s.id() + ":" + node.id() + ": disabled prereq'и " + allPrereqsDisabled
                                + " должны считаться выполненными");
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
        assertFalse(
                beast.isReachable(withPrereqs.id(), Set.of(withPrereqs.id())),
                "отключённый узел недостижим по определению");

        // вся цепочка prereq'ов в стоп-листе → узел достижим
        assertTrue(
                beast.isReachable(withPrereqs.id(), fullChain), "узел достижим, если вся цепочка prereq'ов отключена");

        // несуществующий узел
        assertFalse(beast.isReachable("no_such_node", Set.of()), "несуществующий узел недостижим");
    }

    @Test
    @DisplayName("nodes: входной список и accessor не раскрывают внутреннее состояние")
    void nodesAreDefensivelyCopied() {
        List<Spec2Node> source = new java.util.ArrayList<>();
        source.add(syntheticNode("first", Map.of()));
        Spec2Tree tree = new Spec2Tree("synthetic", source);

        source.add(syntheticNode("second", Map.of()));
        assertTrue(tree.nodes().size() == 1);
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> tree.nodes().add(syntheticNode("third", Map.of())));
    }

    @Test
    @DisplayName("isReachable: общий пререквизит в разных ветвях DAG не является циклом")
    void isReachableSupportsSharedPrerequisites() {
        Spec2Tree tree = new Spec2Tree(
                "synthetic",
                List.of(
                        syntheticNode("root", Map.of("left", 1, "right", 1)),
                        syntheticNode("left", Map.of("shared", 1)),
                        syntheticNode("right", Map.of("shared", 1)),
                        syntheticNode("shared", Map.of())));

        assertTrue(
                tree.isReachable("root", Set.of()),
                "общий предок, достижимый из двух ветвей, не должен считаться циклом");
    }

    @Test
    @DisplayName("isReachable: циклическая цепочка пререквизитов недостижима")
    void isReachableRejectsCycles() {
        Spec2Tree tree = new Spec2Tree(
                "synthetic",
                List.of(
                        syntheticNode("cycle_a", Map.of("cycle_b", 1)),
                        syntheticNode("cycle_b", Map.of("cycle_a", 1))));

        assertFalse(tree.isReachable("cycle_a", Set.of()), "цикл в prereq-графе должен отклоняться");
    }

    private static Spec2Node syntheticNode(String id, Map<String, Integer> prereqs) {
        return new Spec2Node(id, "synthetic", 1, 1, 1, prereqs, "passive_stat", id, "", null);
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
        assertFalse(tree.canBuy(ult, empty, 5, prereqsDisabled), "ряд 6 закрыт при нуле очков в дереве");

        // Заполняем ряды 1–5 до максимума: spent ≥ 30, гейт ряда 6 открыт.
        Map<String, Integer> full = new HashMap<>();
        for (Spec2Node n : tree.nodes()) {
            if (n.row() < 6) {
                full.put(n.id(), n.maxRank());
            }
        }
        int spent = tree.spentInTree(full);
        assertTrue(Spec2Points.rowUnlocked(ult.row(), spent), "spent = " + spent + " должен открывать ряд 6");
        assertTrue(
                tree.canBuy(ult, full, 1, prereqsDisabled),
                "при открытом ряде, заполненных prereq'ах и 1 очке покупка возможна");
        assertFalse(tree.canBuy(ult, full, 0, prereqsDisabled), "без свободного очка покупка закрыта");
    }
}
