// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

/**
 * 1.14.0 «Спек 2.0»: pure-экономика очков (дизайн-док, раздел 1).
 * Старт charLevel 15, +1/уровень, пул 46. Гейты рядов — по очкам ВНУТРИ дерева.
 */
public final class Spec2Points {

    public static final int[] ROW_GATES = {0, 5, 10, 15, 20, 30};
    public static final int START_LEVEL = 15;
    public static final int MAX_POINTS = 46;

    private Spec2Points() {
    }

    public static int earnedPoints(int charLevel) {
        if (charLevel < START_LEVEL) {
            return 0;
        }
        return Math.min(MAX_POINTS, charLevel - START_LEVEL + 1);
    }

    public static boolean rowUnlocked(int row, int spentInTree) {
        if (row < 1 || row > ROW_GATES.length) {
            return false;
        }
        return spentInTree >= ROW_GATES[row - 1];
    }
}
