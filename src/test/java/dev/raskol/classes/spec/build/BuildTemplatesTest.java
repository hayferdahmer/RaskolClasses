// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.registry.Spec2Registry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.14.15: валидация BuildTemplate на реальном реестре деревьев.
 *
 * Дефолтный снапшот Spec2Points (15/46/[0,5,10,15,20,30]) активен без configure() —
 * тесты не зависят от config.yml и от test-хелперов.
 *
 * Покрывает: иммутабельность ranks, фабричные guard'ы, парсинг архетипов,
 * структуру prereq реального дерева, детекцию перегруза бюджета очков.
 * Полная валидация load() (prereq-дыра, неизвестный узел, disabled-узел) —
 * в selftest-чеке 123 на реальном builds.yml (волна 15B).
 */
class BuildTemplatesTest {

    @Test
    void registryIsComplete() {
        // sanity: все 18 деревьев зарегистрированы
        String[] expected = {
                "arms", "fury", "guard",
                "marksmanship", "survival", "beastmaster",
                "assassination", "outlaw", "subtlety",
                "arcane", "fire", "frost",
                "discipline", "holy", "shadow",
                "affliction", "destruction", "demonology"
        };
        for (String spec : expected) {
            assertNotNull(Spec2Registry.treeOf(spec),
                    "дерево " + spec + " должно быть в реестре");
            assertFalse(Spec2Registry.treeOf(spec).nodes().isEmpty(),
                    "дерево " + spec + " не должно быть пустым");
        }
    }

    @Test
    void validTemplatePasses() {
        // arms с 1 узлом ряда 1 — минимально валидный
        BuildTemplate t = BuildTemplate.of("arms", Archetype.PVP,
                "Test", "t", Map.of("arms_training", 3), "1.14.15");
        assertEquals(3, t.spentPoints());
        assertEquals(1, t.nodeCount());
    }

    @Test
    void factoryGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildTemplate.of(" ", Archetype.PVP, "N", "t", Map.of(), "1"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildTemplate.of("arms", null, "N", "t", Map.of(), "1"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildTemplate.of("arms", Archetype.PVP, " ", "t", Map.of(), "1"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildTemplate.of("arms", Archetype.PVP, "N", null, Map.of(), "1"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildTemplate.of("arms", Archetype.PVP, "N", "t", null, "1"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildTemplate.of("arms", Archetype.PVP, "N", "t",
                        Map.of("x", -1), "1"));
    }

    @Test
    void ranksImmutable() {
        Map<String, Integer> ranks = Map.of("arms_training", 3);
        BuildTemplate t = BuildTemplate.of("arms", Archetype.PVP,
                "T", "t", ranks, "1");
        assertThrows(UnsupportedOperationException.class,
                () -> t.ranks().put("arms_training", 5));
    }

    @Test
    void archetypeFromConfig() {
        assertEquals(Archetype.PVP, Archetype.fromConfig("pvp"));
        assertEquals(Archetype.PVE, Archetype.fromConfig("PvE"));
        assertEquals(Archetype.PVP, Archetype.fromConfig("duel"));
        assertEquals(Archetype.PVE, Archetype.fromConfig("farm"));
        assertNull(Archetype.fromConfig("moba"));
        assertNull(Archetype.fromConfig(""));
        assertNull(Archetype.fromConfig(null));
    }

    @Test
    void prereqStructure_realTree() {
        // arms: mortal_strike требует whirlwind_slash≥1 (реальное prereq из WarriorTrees).
        // Шаблон БЕЗ prereq-узла должен провалить валидацию load() — здесь проверяем
        // саму структуру prereq-карты как precondition для чека 123.
        BuildTemplate bad = BuildTemplate.of("arms", Archetype.PVP,
                "Bad", "b", Map.of("mortal_strike", 1), "1");
        Spec2Node node = Spec2Registry.treeOf("arms").find("mortal_strike");
        assertNotNull(node, "mortal_strike должен существовать (Spec2Tree.find)");
        assertFalse(node.prereqs().isEmpty());
        assertTrue(node.prereqs().containsKey("whirlwind_slash"));
        assertEquals(0, bad.ranks().getOrDefault("whirlwind_slash", 0));
    }

    @Test
    void overBudgetDetected() {
        // Сумма рангов 48 > maxPoints=46 (дефолтный снапшот) — валидация load() отклонит.
        Map<String, Integer> ranks = Map.ofEntries(
                Map.entry("arms_training", 5),
                Map.entry("hardened_skin", 4),
                Map.entry("precision", 5),
                Map.entry("field_medkit", 3),
                Map.entry("deep_wounds", 3),
                Map.entry("sword_and_board", 3),
                Map.entry("fenrir_blood", 1),
                Map.entry("rend_master", 3),
                Map.entry("whirlwind_slash", 1),
                Map.entry("war_acumen", 3),
                Map.entry("execute_drill", 2),
                Map.entry("bloodletting", 3),
                Map.entry("shield_wall_echo", 2),
                Map.entry("mortal_strike", 1),
                Map.entry("second_wind", 2),
                Map.entry("executioner", 3),
                Map.entry("titan_grip", 3)
        );
        BuildTemplate t = BuildTemplate.of("arms", Archetype.PVP, "Over", "o", ranks, "1");
        assertEquals(48, t.spentPoints());
        assertTrue(t.spentPoints() > Spec2Points.snapshot().maxPoints(),
                "превышение бюджета 46 должно быть видно без configure()");
    }
}
