// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import dev.raskol.classes.RaskolClasses;

import java.util.logging.Logger;

/**
 * 1.14.0 «Спек 2.0»: pure-экономика очков (дизайн-док, раздел 1).
 * Старт charLevel 15, +1/уровень, пул 46. Гейты рядов — по очкам ВНУТРИ дерева.
 *
 * 1.14.4 (Волна 4, П9): значения читаются из конфига spec2.* с фолбэком на
 * дефолты (15/46/[0,5,10,15,20,30]). Обновление через configure(plugin) в
 * onEnable — до первого использования (purchase/reconcile/selftest).
 * Поля нефинальны, но обновляются один раз при старте сервера — семантика
 * «константа на время работы сервера» сохранена.
 */
public final class Spec2Points {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    public static int[] ROW_GATES = {0, 5, 10, 15, 20, 30};
    public static int START_LEVEL = 15;
    public static int MAX_POINTS = 46;

    private Spec2Points() {
    }

    /**
     * 1.14.4 (П9): читает spec2.start-level / spec2.max-points / spec2.row-gates
     * из plugin.getConfig() с фолбэком на дефолты. Вызывается из onEnable.
     */
    public static void configure(RaskolClasses plugin) {
        if (plugin == null || plugin.getConfig() == null) {
            return;
        }
        int sl = plugin.getConfig().getInt("spec2.start-level", START_LEVEL);
        if (sl > 0) {
            START_LEVEL = sl;
        }
        int mp = plugin.getConfig().getInt("spec2.max-points", MAX_POINTS);
        if (mp > 0) {
            MAX_POINTS = mp;
        }
        java.util.List<?> raw = plugin.getConfig().getList("spec2.row-gates", null);
        if (raw != null && !raw.isEmpty()) {
            int[] arr = new int[raw.size()];
            boolean valid = true;
            for (int i = 0; i < raw.size(); i++) {
                Object v = raw.get(i);
                if (v instanceof Number n) {
                    int iv = n.intValue();
                    if (iv < 0) {
                        valid = false;
                        break;
                    }
                    arr[i] = iv;
                } else {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                ROW_GATES = arr;
            } else {
                LOGGER.warning("spec2: spec2.row-gates невалиден, оставлен дефолт "
                        + java.util.Arrays.toString(ROW_GATES));
            }
        }
        LOGGER.info("spec2: configure → start=" + START_LEVEL + " max=" + MAX_POINTS
                + " gates=" + java.util.Arrays.toString(ROW_GATES));
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
