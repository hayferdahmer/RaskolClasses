// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import java.util.List;
import java.util.Map;

/**
 * 1.14.0 «Спек 2.0»: дерево спеки + pure-правила покупки (дизайн-док, раздел 2).
 * Стоимость ранга = 1 очко из общего пула 46; ёмкость дерева 50–58 > 46.
 */
public record Spec2Tree(String specId, List<Spec2Node> nodes) {

    public Spec2Node find(String nodeId) {
        for (Spec2Node n : nodes) {
            if (n.id().equals(nodeId)) {
                return n;
            }
        }
        return null;
    }

    public int capacity() {
        int sum = 0;
        for (Spec2Node n : nodes) {
            sum += n.maxRank();
        }
        return sum;
    }

    public int spentInTree(Map<String, Integer> ranks) {
        int sum = 0;
        for (Integer v : ranks.values()) {
            sum += v == null ? 0 : v;
        }
        return sum;
    }

    public boolean prereqsMet(Spec2Node node, Map<String, Integer> ranks) {
        for (Map.Entry<String, Integer> e : node.prereqs().entrySet()) {
            if (ranks.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Серверное правило покупки следующего ранга. */
    public boolean canBuy(Spec2Node node, Map<String, Integer> ranks, int availablePoints) {
        int spent = spentInTree(ranks);
        if (!Spec2Points.rowUnlocked(node.row(), spent)) {
            return false;
        }
        if (!prereqsMet(node, ranks)) {
            return false;
        }
        if (ranks.getOrDefault(node.id(), 0) >= node.maxRank()) {
            return false;
        }
        return availablePoints >= 1;
    }
}
