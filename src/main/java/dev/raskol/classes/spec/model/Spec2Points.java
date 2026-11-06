// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.model;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Новый код обязан использовать аксессоры startLevel()/maxPoints()/rowGate(row)/rowGates().
 * 1.14.13 (Sprint 9, Волна 13C, P0-2-06): @Deprecated публичные static-поля
 *         START_LEVEL/MAX_POINTS/ROW_GATES УДАЛЕНЫ (были помечены на удаление в 1.15.0).
 *         Единственный legacy-читатель (selftest чек 107) переведён на snapshot().
 *         Поля были read-only копиями снимка, писались ТОЛЬКО внутри configure();
 *         удаление не меняет поведение, только чистит публичный API.
 * 1.15.6 (Волна 4, аудит 2.6-89): PER-TREE ROW-GATES. config spec2.row-gates-by-tree
 *         (map treeId → 6 ints) переопределяет глобальные гейты для конкретного дерева.
 *         Кейс: beastmaster после стоп-листа имеет 27 доступных рангов в рядах 1–5,
 *         глобальный гейт ряда 6 = 30 делал ульт bm_bestial_wrath НЕДОСТИЖИМЫМ.
 *         Override [0,5,10,15,20,27] открывает ряд 6 только этому дереву.
 *         Контекст дерева передаётся ThreadLocal (beginTree/endTree) — Spec2Tree.canBuy/
 *         prereqsMet вызывают rowUnlocked(row, spent) БЕЗ treeId, контекст ставит
 *         Spec2Service вокруг purchase/validate/hasDependents/reconcile.
 *         Прямые вызовы rowUnlocked(row, spent) без контекста (selftest 46/107)
 *         читают глобальные гейты — поведение прежних чеков не меняется.
 */
public final class Spec2Points {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    /**
     * Неизменяемый снимок настроек экономики очков.
     * rowGates клонируется на входе и на выходе — снаружи массив не mutabel.
     * treeGates — иммутабельная map treeId → 6 гейтов (clone массивов на входе).
     */
    public record Snapshot(int startLevel, int maxPoints, int[] rowGates,
                           Map<String, int[]> treeGates) {

        public Snapshot {
            rowGates = rowGates == null ? new int[0] : rowGates.clone();
            if (treeGates != null) {
                Map<String, int[]> copy = new HashMap<>();
                treeGates.forEach((k, v) -> copy.put(k, v == null ? new int[0] : v.clone()));
                treeGates = Map.copyOf(copy);
            } else {
                treeGates = Map.of();
            }
        }

        /** Convenience-конструктор без per-tree overrides (дефолты, миграция). */
        public Snapshot(int startLevel, int maxPoints, int[] rowGates) {
            this(startLevel, maxPoints, rowGates, Map.of());
        }

        public int[] rowGates() {
            return rowGates.clone();
        }

        /** Гейт ряда (1..6) глобально; для невалидного ряда — Integer.MAX_VALUE (ряд закрыт). */
        public int rowGate(int row) {
            if (row < 1 || row > rowGates.length) {
                return Integer.MAX_VALUE;
            }
            return rowGates[row - 1];
        }

        /** Гейт ряда с учётом per-tree override; override отсутствует → глобальный. */
        public int rowGate(int row, String treeId) {
            if (treeId != null) {
                int[] override = treeGates.get(treeId);
                if (override != null && row >= 1 && row <= override.length) {
                    return override[row - 1];
                }
            }
            return rowGate(row);
        }
    }

    private static final Snapshot DEFAULTS = new Snapshot(15, 46, new int[]{0, 5, 10, 15, 20, 30});

    /** 1.14.7 (Sprint 4, P1-4): единственный источник истины; publishes атомарно. */
    private static volatile Snapshot snap = DEFAULTS;

    /**
     * 1.15.6 (2.6-89): контекст дерева для per-tree гейтов. Spec2Tree.canBuy и
     * prereqsMet вызывают rowUnlocked(row, spent) без treeId — контекст подаёт
     * treeId из вызывающего Scope Spec2Service. Main-thread + scheduler-потоки
     * reconcile ставят/снимают контекст парно (finally).
     */
    private static final ThreadLocal<String> CTX_TREE = new ThreadLocal<>();

    private Spec2Points() {
    }

    /* ------------------------------ контекст дерева (1.15.6) ------------------------------ */

    public static void beginTree(String treeId) {
        CTX_TREE.set(treeId);
    }

    public static void endTree() {
        CTX_TREE.remove();
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
     * 1.15.6: + spec2.row-gates-by-tree; валидирует и атомарно publishes новый Snapshot.
     * Вызывается в onEnable и reloadPlugin.
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
        List<?> raw = plugin.getConfig().getList("spec2.row-gates", null);
        if (raw == null) {
            LOGGER.info("spec2.row-gates не указан в конфиге, оставлен текущий снимок гейтов");
        } else if (raw.size() != 6) {
            LOGGER.warning("spec2.row-gates имеет длину " + raw.size()
                    + " (ожидалось 6 рядов), оставлен текущий снимок " + Arrays.toString(gates));
        } else {
            int[] cand = parseGates(raw, "spec2.row-gates");
            if (cand != null) {
                gates = cand;
            }
        }

        // 1.15.6 (2.6-89): per-tree overrides
        Map<String, int[]> overrides = new HashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("spec2.row-gates-by-tree");
        if (sec != null) {
            for (String treeId : sec.getKeys(false)) {
                List<?> treeRaw = sec.getList(treeId, null);
                if (treeRaw == null || treeRaw.size() != 6) {
                    LOGGER.warning("spec2.row-gates-by-tree." + treeId
                            + " должен содержать 6 чисел — override пропущен");
                    continue;
                }
                int[] cand = parseGates(treeRaw, "spec2.row-gates-by-tree." + treeId);
                if (cand != null) {
                    overrides.put(treeId, cand);
                }
            }
        }

        // атомарная публикация снимка
        snap = new Snapshot(sl, mp, gates, Map.copyOf(overrides));
        LOGGER.info("spec2: configure → start=" + sl + " max=" + mp
                + " gates=" + Arrays.toString(snap.rowGates())
                + " tree-overrides=" + overrides.keySet());
    }

    /** Парсер 6 гейтов: числа, ≥0, неубывающие; null = невалидно (warning уже внутри). */
    private static int[] parseGates(List<?> raw, String path) {
        int[] cand = new int[6];
        int prev = -1;
        for (int i = 0; i < 6; i++) {
            Object v = raw.get(i);
            if (!(v instanceof Number n)) {
                LOGGER.warning(path + "[" + i + "] не число: " + v);
                return null;
            }
            int iv = n.intValue();
            if (iv < 0) {
                LOGGER.warning(path + "[" + i + "]=" + iv + " недопустим (<0)");
                return null;
            }
            if (iv < prev) {
                LOGGER.warning(path + "[" + i + "]=" + iv
                        + " < предыдущего " + prev + " (должна быть неубывающая)");
                return null;
            }
            cand[i] = iv;
            prev = iv;
        }
        return cand;
    }

    public static int earnedPoints(int charLevel) {
        Snapshot s = snap;
        if (charLevel < s.startLevel()) {
            return 0;
        }
        return Math.min(s.maxPoints(), charLevel - s.startLevel() + 1);
    }

    /** Глобальный гейт с учётом ThreadLocal-контекста дерева (1.15.6). */
    public static boolean rowUnlocked(int row, int spentInTree) {
        return rowUnlocked(row, spentInTree, CTX_TREE.get());
    }

    /** Явный treeId: per-tree override → глобальный гейт. */
    public static boolean rowUnlocked(int row, int spentInTree, String treeId) {
        return spentInTree >= snap.rowGate(row, treeId);
    }
}
