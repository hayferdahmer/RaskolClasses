// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 1.14.0: дерево специализации Спека 2.0 — узлы, ряды, ёмкость, пререквизиты.
 * 1.14.8 (Sprint 5, P2-4): stopListLive защищает от регресса.
 * 1.14.8-fix (Волна 6.1, P0-6B): prereqsMet и isReachable учитывают disabled-nodes:
 *   отключённый пререквизит считается выполненным (как будто макс. ранг),
 *   чтобы узлы с disabled-пререком оставались достижимыми (фикс регрессии,
 *   когда 25 включённых узлов, включая 6 ультов, стали недостижимы).
 */
public record Spec2Tree(
        String id,
        String displayName,
        int capacity,
        List<Spec2Node> nodes
) {

    public Spec2Node find(String nodeId) {
        for (Spec2Node n : nodes) {
            if (n.id().equals(nodeId)) {
                return n;
            }
        }
        return null;
    }

    /**
     * Проверка прямых пререквизитов узла.
     * 1.14.8-fix (Волна 6.1): disabled-пререквизит считается выполненным
     * (вырезан из дерева, как будто у игрока макс. ранг).
     *
     * @param uuid          игрок (для логирования, не используется в логике)
     * @param nodeId        проверяемый узел
     * @param ranks         текущие ранги игрока в этом дереве
     * @param disabledNodes узлы в стоп-листе (считаются выполненными)
     * @return true, если все пререквизиты выполнены или отключены
     */
    public boolean prereqsMet(UUID uuid, String nodeId,
                              Map<String, Integer> ranks,
                              Set<String> disabledNodes) {
        Spec2Node node = find(nodeId);
        if (node == null) {
            return false;
        }
        for (Map.Entry<String, Integer> prereq : node.prereqs().entrySet()) {
            String prereqId = prereq.getKey();
            int requiredRank = prereq.getValue();
            // 1.14.8-fix (P0-6B): disabled prereq = выполнен
            if (disabledNodes != null && disabledNodes.contains(prereqId)) {
                continue;
            }
            int actualRank = ranks.getOrDefault(prereqId, 0);
            if (actualRank < requiredRank) {
                return false;
            }
        }
        return true;
    }

    /**
     * 1.14.8-fix (Волна 6.1): проверка достижимости узла с учётом стоп-листа.
     * Узел достижим, если:
     *   (a) он сам не в стоп-листе;
     *   (b) все его пререквизиты либо достигнуты рекурсивно, либо в стоп-листе.
     * Используется чеком 122 selftest для верификации целостности дерева.
     *
     * @param nodeId        проверяемый узел
     * @param disabledNodes узлы в стоп-листе
     * @return true, если узел достижим при текущем стоп-листе
     */
    public boolean isReachable(String nodeId, Set<String> disabledNodes) {
        // сам узел в стоп-листе — недостижим по определению
        if (disabledNodes != null && disabledNodes.contains(nodeId)) {
            return false;
        }
        Spec2Node node = find(nodeId);
        if (node == null) {
            return false;
        }
        // нет пререквизитов — корневой узел, всегда достижим
        if (node.prereqs().isEmpty()) {
            return true;
        }
        // проверяем все пререквизиты рекурсивно
        for (String prereqId : node.prereqs().keySet()) {
            // disabled prereq считаем выполненным (вырезан из дерева)
            if (disabledNodes != null && disabledNodes.contains(prereqId)) {
                continue;
            }
            if (!isReachable(prereqId, disabledNodes)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Совместимость: старая сигнатура без disabledNodes (для кода, который
     * ещё не обновлён). Считает все пререквизиты активными.
     * @deprecated использовать {@link #prereqsMet(UUID, String, Map, Set)}
     */
    @Deprecated
    public boolean prereqsMet(UUID uuid, String nodeId, Map<String, Integer> ranks) {
        return prereqsMet(uuid, nodeId, ranks, null);
    }
}
