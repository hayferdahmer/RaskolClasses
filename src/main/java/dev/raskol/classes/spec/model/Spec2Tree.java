// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.14.0: дерево специализации Спека 2.0 — узлы, ряды, ёмкость, пререквизиты.
 * Record имеет ДВЕ компоненты (id, nodes): именно так конструируют деревья
 * файлы spec/registry/trees/*Trees.java. Ёмкость и имя — вычисляемые методы:
 * capacity() = сумма maxRank узлов (инвариант P0-4: ≥ spec2.tree-capacity-min
 * и > бюджета 46, чек 23/93 selftest).
 *
 * 1.14.8-fix (Волна 6.1, P0-6B): prereqsMet и isReachable учитывают стоп-лист
 * spec2.disabled-nodes: отключённый пререквизит считается выполненным
 * («вырезан из дерева»). Без этого включённые узлы, чьи пререквизиты попали
 * в стоп-лист, становились недостижимы — регрессия 1.14.8 блокировала
 * 25 узлов, включая 6 ультов (bm_bestial_wrath, su_shadow_blades,
 * sv_serpent_sting, as_deathmark, fury_bladestorm, guard_last_stand).
 *
 * Редакция заменяет 1/5-fix волны 6.1: восстановлена двухкомпонентная форма
 * record'а (конструктор (String, List) для *Trees.java), capacity() снова
 * вычисляемый метод, а не компонента.
 */
public record Spec2Tree(
        String id,
        List<Spec2Node> nodes
) {

    /** Узел по id или null. */
    public Spec2Node find(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        for (Spec2Node n : nodes) {
            if (nodeId.equals(n.id())) {
                return n;
            }
        }
        return null;
    }

    /**
     * Ёмкость дерева = сумма maxRank всех узлов (P0-4: ≥ tree-capacity-min,
     * строго больше бюджета очков 46 — закрыть дерево целиком нельзя).
     */
    public int capacity() {
        int sum = 0;
        for (Spec2Node n : nodes) {
            sum += n.maxRank();
        }
        return sum;
    }

    /** Отображаемое имя дерева: в текущей модели совпадает с id (arms, fury, …). */
    public String displayName() {
        return id;
    }

    /** Сумма рангов по узлам этого дерева (неизвестные id игнорируются). */
    public int spentInTree(Map<String, Integer> ranks) {
        int sum = 0;
        for (Spec2Node n : nodes) {
            sum += ranks.getOrDefault(n.id(), 0);
        }
        return sum;
    }

    /**
     * 1.14.8-fix (P0-6B): пререквизиты узла выполнены?
     * Пререквизит из стоп-листа (disabledNodes) считается выполненным —
     * он вырезан из дерева, а не «не куплен».
     *
     * @param disabledNodes узлы spec2.disabled-nodes; null/пусто → ничто не отключено
     */
    public boolean prereqsMet(Spec2Node node, Map<String, Integer> ranks,
                              Set<String> disabledNodes) {
        if (node == null) {
            return false;
        }
        for (Map.Entry<String, Integer> e : node.prereqs().entrySet()) {
            if (disabledNodes != null && disabledNodes.contains(e.getKey())) {
                continue; // отключённый пререквизит = выполнен
            }
            if (ranks.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @deprecated используй {@link #prereqsMet(Spec2Node, Map, Set)} со стоп-листом;
     *             оверлод оставлен для внешних вызовов (BalanceSimulator и др.).
     */
    @Deprecated
    public boolean prereqsMet(Spec2Node node, Map<String, Integer> ranks) {
        return prereqsMet(node, ranks, null);
    }

    /**
     * Можно ли купить ранг узла сейчас: не макс-ранг, ряд открыт по очкам дерева,
     * пререквизиты выполнены, есть свободное очко.
     * Принадлежность узла стоп-листу проверяет вызывающий (Spec2Service.purchase).
     *
     * @param disabledNodes стоп-лист для учёта пререквизитов; null → ничто не отключено
     */
    public boolean canBuy(Spec2Node node, Map<String, Integer> ranks,
                          int availablePoints, Set<String> disabledNodes) {
        if (node == null) {
            return false;
        }
        if (ranks.getOrDefault(node.id(), 0) >= node.maxRank()) {
            return false;
        }
        if (!Spec2Points.rowUnlocked(node.row(), spentInTree(ranks))) {
            return false;
        }
        if (!prereqsMet(node, ranks, disabledNodes)) {
            return false;
        }
        return availablePoints >= 1;
    }

    /**
     * @deprecated используй {@link #canBuy(Spec2Node, Map, int, Set)} со стоп-листом.
     */
    @Deprecated
    public boolean canBuy(Spec2Node node, Map<String, Integer> ranks, int availablePoints) {
        return canBuy(node, ranks, availablePoints, null);
    }

    /**
     * 1.14.8-fix (P0-6B): достижим ли включённый узел при текущем стоп-листе.
     * Узел достижим, если сам не отключён и каждый его пререквизит либо
     * отключён (считается выполненным), либо достижим рекурсивно.
     * Используется чеком 122 selftest (волна 6.1, 5/5).
     */
    public boolean isReachable(String nodeId, Set<String> disabledNodes) {
        if (disabledNodes != null && disabledNodes.contains(nodeId)) {
            return false;
        }
        Spec2Node node = find(nodeId);
        if (node == null) {
            return false;
        }
        for (String prereqId : node.prereqs().keySet()) {
            if (disabledNodes != null && disabledNodes.contains(prereqId)) {
                continue; // отключённый пререквизит = выполнен
            }
            if (!isReachable(prereqId, disabledNodes)) {
                return false;
            }
        }
        return true;
    }
}
