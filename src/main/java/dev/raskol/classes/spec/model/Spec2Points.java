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
 * 1.14.7 (Sprint 2, P1-4): валидация конфига (start ≥ 1, max ≥ 0, gates.length == 6,
 * неубывающая последовательность) + вызов configure из reloadPlugin.
 * 1.14.7 (Sprint 4, P1-4): НЕИЗМЕНЯЕМЫЙ СНИМОК. Источник истины — record Snapshot
 * за private static volatile ссылкой; configure() publishes новый snapshot атомарно.
 * Публичные static-поля START_LEVEL/MAX_POINTS/ROW_GATES оставлены как
 * @Deprecated read-only копии (единственный писатель — configure()), удаление в
 * 1.15.0 после миграции последних читателей (SelftestRunner чек 107).
 * Новый код обязан использовать аксессоры startLevel()/maxPoints()/rowGate(row)/rowGates().
 */
public final class Spec2Points {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    /**
     * Неизменяемый снимок настроек экономики очков.
     * rowGates клонируется на входе и на выходе — снаружи массив не_mutabel.
     */
    public record Snapshot(int startLevel, int maxPoints, int[] rowGates) {

        public Snapshot {
            rowGates = rowGates == null ? new int[0] : rowGates.clone();
        }

        public int[] rowGates() {
            return rowGates.clone();
        }

        /** Гейт ряда (1..6); для невалидного ряда — Integer.MAX_VALUE (ряд закрыт). */
        public int rowGate(int row) {
            if (row < 1 || row > rowGates.length) {
                return Integer.MAX_VALUE;
            }
            return rowGates[row - 1];
        }
    }

    private static final Snapshot DEFAULTS = new Snapshot(15, 46, new int[]{0, 5, 10, 15, 20, 30});

    /** 1.14.7 (Sprint 4, P1-4): единственный источник истины; publishes атомарно. */
    private static volatile Snapshot snap = DEFAULTS;

    /**
     * @deprecated read-only копия снимка для legacy-читателей (чек 107 selftest).
     *             Пишется ТОЛЬКО внутри configure(); не использовать в новом коде.
     *             Удаление в 1.15.0.
     */
    @Deprecated
    public static int START_LEVEL = DEFAULTS.startLevel();

    /** @deprecated см. START_LEVEL. */
    @Deprecated
    public static int MAX_POINTS = DEFAULTS.maxPoints();

    /** @deprecated см. START_LEVEL. */
    @Deprecated
    public static int[] ROW_GATES = DEFAULTS.rowGates();

    private Spec2Points() {
    }

    /* ------------------------------ аксессоры снимка ------------------------------ */

    public static Snapshot snapshot() {
        return snap;
    }

    public static int startLevel() {
        return snap.startLevel();
    }

    public static int maxPoints() {
        return snap.maxPoints();
    }

    public static int[] rowGates() {
        return snap.rowGates();
    }

    public static int rowGate(int row) {
        return snap.rowGate(row);
    }

    /**
     * 1.14.7 (P1-4): читает spec2.start-level / spec2.max-points / spec2.row-gates,
     * валидирует и атомарно publishes новый Snapshot. Вызывается в onEnable и reloadPlugin.
     * Невалидные значения → warning + сохранение текущего снимка (или дефолта для поля).
     */
    public static void configure(RaskolClasses plugin) {
        if (plugin == null || plugin.getConfig() == null) {
            LOGGER.warning("spec2: configure() вызван с null plugin/config, оставлен текущий снимок");
            return;
        }

        Snapshot cur = snap;
        int sl = plugin.getConfig().getInt("spec2.start-level", cur.startLevel());
        if (sl < 1) {
            LOGGER.warning("spec2.start-level=" + sl + " недопустим (<1), оставлен " + cur.startLevel());
            sl = cur.startLevel();
        }
        int mp = plugin.getConfig().getInt("spec2.max-points", cur.maxPoints());
        if (mp < 0) {
            LOGGER.warning("spec2.max-points=" + mp + " недопустим (<0), оставлен " + cur.maxPoints());
            mp = cur.maxPoints();
        }

        int[] gates = cur.rowGates();
        java.util.List<?> raw = plugin.getConfig().getList("spec2.row-gates", null);
        if (raw == null) {
            LOGGER.info("spec2.row-gates не указан в конфиге, оставлен текущий снимок гейтов");
        } else if (raw.size() != 6) {
            LOGGER.warning("spec2.row-gates имеет длину " + raw.size()
                    + " (ожидалось 6 рядов), оставлен текущий снимок " + Arrays.toString(gates));
        } else {
            int[] cand = new int[6];
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
                cand[i] = iv;
                prev = iv;
            }
            if (valid) {
                gates = cand;
            } else {
                LOGGER.warning("spec2.row-gates невалиден, оставлен текущий снимок "
                        + Arrays.toString(gates));
            }
        }

        // атомарная публикация снимка + синхронизация deprecated-копий
        snap = new Snapshot(sl, mp, gates);
        START_LEVEL = sl;
        MAX_POINTS = mp;
        ROW_GATES = snap.rowGates();
        LOGGER.info("spec2: configure → start=" + sl + " max=" + mp
                + " gates=" + Arrays.toString(snap.rowGates()));
    }

    public static int earnedPoints(int charLevel) {
        Snapshot s = snap;
        if (charLevel < s.startLevel()) {
            return 0;
        }
        return Math.min(s.maxPoints(), charLevel - s.startLevel() + 1);
    }

    public static boolean rowUnlocked(int row, int spentInTree) {
        return spentInTree >= snap.rowGate(row);
    }
}
