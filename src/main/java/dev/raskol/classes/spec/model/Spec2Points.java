// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.model;

import dev.raskol.classes.RaskolClasses;

import java.util.Arrays;
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
 *
 * 1.14.7 (Спринт 2, P1-4): усилена валидация конфига:
 *   - row-gates.length ДОЛЖЕН быть == 6 (ровно 6 рядов в деревьях);
 *   - последовательность НЕУБЫВАЮЩАЯ (ряд N+1 открывается при ≥ очков, чем ряд N);
 *   - max-points ≥ 0 (разрешён 0 для тестовых билдов);
 *   - start-level ≥ 1;
 *   - при невалидных значениях — warning + оставлен дефолт/предыдущее значение.
 *   configure() вызывается в onEnable И в reloadPlugin (P1-4: reload без рестарта).
 */
public final class Spec2Points {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    /** 1.14.4 (П9): snapshot — значения из конфига, обновляются configure(). */
    public static int[] ROW_GATES = {0, 5, 10, 15, 20, 30};
    public static int START_LEVEL = 15;
    public static int MAX_POINTS = 46;

    private Spec2Points() {
    }

    /**
     * 1.14.7 (P1-4): читает spec2.start-level / spec2.max-points / spec2.row-gates
     * из plugin.getConfig() с валидацией и фолбэком на дефолты.
     * Вызывается из onEnable (старт сервера) И из reloadPlugin (hot-reload).
     */
    public static void configure(RaskolClasses plugin) {
        if (plugin == null || plugin.getConfig() == null) {
            LOGGER.warning("spec2: configure() вызван с null plugin/config, оставлены дефолты");
            return;
        }

        // === start-level ===
        int sl = plugin.getConfig().getInt("spec2.start-level", START_LEVEL);
        if (sl < 1) {
            LOGGER.warning("spec2.start-level=" + sl + " недопустим (<1), оставлен " + START_LEVEL);
        } else {
            START_LEVEL = sl;
        }

        // === max-points ===
        int mp = plugin.getConfig().getInt("spec2.max-points", MAX_POINTS);
        if (mp < 0) {
            LOGGER.warning("spec2.max-points=" + mp + " недопустим (<0), оставлен " + MAX_POINTS);
        } else {
            MAX_POINTS = mp;
        }

        // === row-gates ===
        java.util.List<?> raw = plugin.getConfig().getList("spec2.row-gates", null);
        if (raw == null) {
            LOGGER.info("spec2.row-gates не указан в конфиге, оставлен дефолт "
                    + Arrays.toString(ROW_GATES));
        } else if (raw.size() != 6) {
            LOGGER.warning("spec2.row-gates имеет длину " + raw.size()
                    + " (ожидалось 6 рядов), оставлен дефолт " + Arrays.toString(ROW_GATES));
        } else {
            int[] arr = new int[6];
            boolean valid = true;
            int prev = -1;
            for (int i = 0; i < 6; i++) {
                Object v = raw.get(i);
                if (!(v instanceof Number n)) {
                    valid = false;
                    LOGGER.warning("spec2.row-gates[" + i + "] не число: " + v);
                    break;
                }
                int iv = n.intValue();
                if (iv < 0) {
                    valid = false;
                    LOGGER.warning("spec2.row-gates[" + i + "]=" + iv + " недопустим (<0)");
                    break;
                }
                if (iv < prev) {
                    valid = false;
                    LOGGER.warning("spec2.row-gates[" + i + "]=" + iv
                            + " < предыдущего " + prev + " (должна быть неубывающая)");
                    break;
                }
                arr[i] = iv;
                prev = iv;
            }
            if (valid) {
                ROW_GATES = arr;
            } else {
                LOGGER.warning("spec2.row-gates невалиден, оставлен дефолт "
                        + Arrays.toString(ROW_GATES));
            }
        }

        LOGGER.info("spec2: configure → start=" + START_LEVEL + " max=" + MAX_POINTS
                + " gates=" + Arrays.toString(ROW_GATES));
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
