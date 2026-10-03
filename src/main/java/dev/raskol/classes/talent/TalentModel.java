// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import java.util.List;

/**
 * 1.9.0 → 1.14.0: модель данных дерева талантов спеки.
 * 1.14.0 (Б4): WoW-стиль — узлы с рангами 1–5, costPerRank=1, total=45 очков;
 *         старт с charLevel 15, пул 46 очков (запас 1);
 *         гейты тиров: T1=15, T2=25, T3=35, T4=45;
 *         пререквизиты: узел доступен, если все пререквизиты прокачаны до maxRank.
 */
public final class TalentModel {

    private TalentModel() {
    }

    /** Эффект узла таланта. */
    public record TalentEffect(String kind, String target, double value, double value2) {
        public static TalentEffect of(String kind, String target, double value) {
            return new TalentEffect(kind, target, value, 0.0);
        }

        /** 1.14.0: масштабирование эффекта рангом (линейное). */
        public TalentEffect scaledBy(int rank) {
            if (rank <= 0) {
                return new TalentEffect(kind, target, 0.0, 0.0);
            }
            return new TalentEffect(kind, target, value * rank, value2 * rank);
        }
    }

    /**
     * 1.14.0: узел дерева с рангом.
     * tier 1..4, branch "A"|"B", prereqs — id узлов той же ветки;
     * costPerRank — стоимость за 1 ранг (обычно 1);
     * maxRank — максимальный ранг узла (обычно 5).
     */
    public record TalentNode(String id, String specId, int tier, String branch,
                             List<String> prereqs, int costPerRank, int maxRank,
                             String name, String lore, TalentEffect effect) {
    }

    /** Дерево спеки = список узлов (9 по шаблону 1.14.0). */
    public record TalentTree(String specId, List<TalentNode> nodes) {
        public TalentNode find(String nodeId) {
            for (TalentNode n : nodes) {
                if (n.id().equals(nodeId)) {
                    return n;
                }
            }
            return null;
        }

        public int totalCost() {
            int sum = 0;
            for (TalentNode n : nodes) {
                sum += n.costPerRank() * n.maxRank();
            }
            return sum;
        }
    }

    /** 1.14.0: фабрика для каталога (с maxRank/costPerRank). */
    public static TalentNode node(String id, String specId, int tier, String branch,
                                  List<String> prereqs, int costPerRank, int maxRank,
                                  String name, String lore, TalentEffect effect) {
        return new TalentNode(id, specId, tier, branch, prereqs, costPerRank, maxRank, name, lore, effect);
    }

    /** Упрощённая фабрика для selftest (без maxRank, дефолт 5/1). */
    public static TalentNode node(String id, int cost) {
        return new TalentNode(id, "test", 1, "A", List.of(), 1, 5, id, "",
                TalentEffect.of("attr", "str", 0.0));
    }

    /**
     * 1.14.0: очки талантов из уровня персонажа.
     * startLevel=15, maxPoints=46, perLevel=1 → 15→1, 60→46.
     */
    public static int earnedPoints(int charLevel, int startLevel, int perLevel, int maxPoints) {
        if (charLevel < startLevel || perLevel <= 0 || maxPoints <= 0) {
            return 0;
        }
        long raw = (long) (charLevel - startLevel + 1) * perLevel;
        return (int) Math.min(maxPoints, raw);
    }

    /** 1.14.0: стоимость полного дерева = сумма (costPerRank × maxRank) по всем узлам. */
    public static int treeCost(List<TalentNode> nodes) {
        int sum = 0;
        for (TalentNode n : nodes) {
            sum += n.costPerRank() * n.maxRank();
        }
        return sum;
    }

    /**
     * 1.14.0: гейт тира по уровню персонажа.
     * T1=15, T2=25, T3=35, T4=45 (база startLevel 15).
     */
    public static int tierGate(int tier, int startLevel) {
        return switch (tier) {
            case 1 -> startLevel;
            case 2 -> startLevel + 10;
            case 3 -> startLevel + 20;
            case 4 -> startLevel + 30;
            default -> Integer.MAX_VALUE;
        };
    }
}
