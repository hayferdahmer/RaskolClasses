// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.10 (Sprint 7, волна 7.1b, P0-11-01): record Spec2Node — конструктор,
 * isUltimate, isUnlock. Поля name/lore/prereqs/effect для тестовых методов
 * не нужны, поэтому передаются null.
 */
@DisplayName("Spec2Node: record и предикаты")
class Spec2NodeTest {

    /** Базовый пассивный узел ряда 1, колонка 1. */
    private static Spec2Node passive() {
        return new Spec2Node("test_node", "arms", 1, 1, 3, Map.of(),
                "passive_stat", "Тест", "Лор", null);
    }

    /** Ульт-узел ряда 6 с пререквизитом. */
    private static Spec2Node ult() {
        return new Spec2Node("ult_node", "arms", 6, 1, 1,
                Map.of("prereq_node", 2),
                "ultimate", "Ульт", "Ультовый лор", null);
    }

    /** unlock_ability узел — открывает способность. */
    private static Spec2Node unlock() {
        return new Spec2Node("unlock_node", "arms", 4, 2, 1, Map.of(),
                "unlock_ability", "Открыть", "Открывает способность", null);
    }

    @Test
    @DisplayName("record-конструктор: все поля доступны через accessor'ы")
    void recordAccessors() {
        Spec2Node n = new Spec2Node("id", "tree", 3, 2, 5,
                Map.of("p", 1), "type", "name", "lore", null);
        assertEquals("id", n.id());
        assertEquals("tree", n.treeId());
        assertEquals(3, n.row());
        assertEquals(2, n.col());
        assertEquals(5, n.maxRank());
        assertEquals(Map.of("p", 1), n.prereqs());
        assertEquals("type", n.type());
        assertEquals("name", n.name());
        assertEquals("lore", n.lore());
        assertNull(n.effect());
    }

    @Test
    @DisplayName("isUltimate: только тип 'ultimate' возвращает true")
    void isUltimate() {
        assertTrue(ult().isUltimate());
        assertFalse(passive().isUltimate());
        assertFalse(unlock().isUltimate());
        // регистр важен: 'Ultimate' не считается
        Spec2Node wrongCase = new Spec2Node("x", "x", 6, 1, 1, Map.of(),
                "Ultimate", null, null, null);
        assertFalse(wrongCase.isUltimate());
    }

    @Test
    @DisplayName("isUnlock: 'unlock_ability' или 'ultimate'")
    void isUnlock() {
        assertTrue(ult().isUnlock(), "ульт — это unlock");
        assertTrue(unlock().isUnlock(), "unlock_ability — это unlock");
        assertFalse(passive().isUnlock(), "пассивка не unlock");

        Spec2Node enhance = new Spec2Node("x", "x", 2, 1, 3, Map.of(),
                "enhance_ability", null, null, null);
        assertFalse(enhance.isUnlock(), "enhance_ability не unlock");
    }

    @Test
    @DisplayName("prereqs: пустая карта не ломает isUltimate/isUnlock")
    void emptyPrereqsSafe() {
        Spec2Node n = new Spec2Node("x", "arms", 1, 1, 1, Map.of(),
                "ultimate", null, null, null);
        assertNotNull(n.prereqs());
        assertTrue(n.prereqs().isEmpty());
        assertTrue(n.isUltimate());
    }

    @Test
    @DisplayName("prereqs: ранговые требования хранятся корректно")
    void prereqsStored() {
        Map<String, Integer> prereqs = Map.of("a", 1, "b", 2, "c", 3);
        Spec2Node n = new Spec2Node("x", "arms", 3, 1, 1, prereqs,
                "passive_stat", null, null, null);
        assertEquals(3, n.prereqs().size());
        assertEquals(1, n.prereqs().get("a"));
        assertEquals(2, n.prereqs().get("b"));
        assertEquals(3, n.prereqs().get("c"));
    }
}
