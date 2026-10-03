// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import java.util.Map;

/**
 * 1.14.0 «Спек 2.0»: узел дерева.
 * row 1..6, col 1..4 (раскладка GUI), maxRank 1..5 (разные ранги как в референсе).
 * prereqs — РАНГОВЫЕ пререквизиты: nodeId → требуемый ранг («deep_wounds → 1»).
 * type: passive_stat | passive_proc | unlock_ability | enhance_ability | ultimate.
 */
public record Spec2Node(
        String id,
        String treeId,
        int row,
        int col,
        int maxRank,
        Map<String, Integer> prereqs,
        String type,
        String name,
        String lore,
        Spec2Effect effect) {

    public boolean isUltimate() {
        return "ultimate".equals(type);
    }

    public boolean isUnlock() {
        return "unlock_ability".equals(type) || isUltimate();
    }
}
