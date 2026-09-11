// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import java.util.List;

/**
 * 1.9.0: модель данных дерева талантов спеки.
 * Чистые записи + pure-формулы экономики очков (те же вызывает /rc selftest).
 *
 * Эффект узла — одна запись TalentEffect:
 *  kind   = attr | resist | kit_base | kit_mult | cd | regen | avoid | proc
 *  target = id атрибута (str/agi/int) / резиста (phys/magic/both) / абилки / прока
 *  value  = число (для resist both: value = phys, value2 = magic)
 *  value2 = вторая компонента (resist both), иначе 0
 */
public final class TalentModel {

    private TalentModel() {
    }

    /** Эффект узла таланта. */
    public record TalentEffect(String kind, String target, double value, double value2) {
        public static TalentEffect of(String kind, String target, double value) {
            return new TalentEffect(kind, target, value, 0.0);
        }
    }

    /** Узел дерева. tier 1..4, branch "A"|"B", prereqs — id узлов той же ветки. */
    public record TalentNode(String id, String specId, int tier, String branch,
                             List<String> prereqs, int cost,
                             String name, String lore, TalentEffect effect) {
    }

    /** Дерево спеки = список узлов (9 по шаблону 1.9.0). */
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
                sum += n.cost();
            }
            return sum;
        }
    }

    /** Компактная фабрика для тестов и каталога. */
    public static TalentNode node(String id, String specId, int tier, String branch,
                                  List<String> prereqs, int cost,
                                  String name, String lore, TalentEffect effect) {
        return new TalentNode(id, specId, tier, branch, prereqs, cost, name, lore, effect);
    }

    /** Упрощённая фабрика для selftest. */
    public static TalentNode node(String id, int cost) {
        return new TalentNode(id, "test", 1, "A", List.of(), cost, id, "",
                TalentEffect.of("attr", "str", 0.0));
    }

    /**
     * Очки талантов из уровня персонажа (1.8.0):
     * earned = clamp((charLevel − startLevel + 1) × perLevel, 0, maxPoints).
     * 40 → 1 очко (сразу можно взять T1), 60 → 21 (полное дерево), 39 → 0.
     */
    public static int earnedPoints(int charLevel, int startLevel, int perLevel, int maxPoints) {
        if (charLevel < startLevel || perLevel <= 0 || maxPoints <= 0) {
            return 0;
        }
        long raw = (long) (charLevel - startLevel + 1) * perLevel;
        return (int) Math.min(maxPoints, raw);
    }

    /** Стоимость полного дерева = сумма стоимостей узлов (эталон 21). */
    public static int treeCost(List<TalentNode> nodes) {
        int sum = 0;
        for (TalentNode n : nodes) {
            sum += n.cost();
        }
        return sum;
    }

    /** Гейт тира по уровню персонажа: T1 40 / T2 45 / T3 50 / T4 55 (база startLevel 40). */
    public static int tierGate(int tier, int startLevel) {
        return switch (tier) {
            case 1 -> startLevel;
            case 2 -> startLevel + 5;
            case 3 -> startLevel + 10;
            case 4 -> startLevel + 15;
            default -> Integer.MAX_VALUE;
        };
    }
}
