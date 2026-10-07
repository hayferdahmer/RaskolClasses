// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

/**
 * Константы и утилиты для слотов инвентаря Книги Талантов (54 слота, 6 рядов).
 * Сетка талантов занимает центральную часть, табы и навигация — по краям.
 */
public final class BookSlots {

    private BookSlots() {
        // Утилитарный класс
    }

    // --- Верхний ряд (Табы классов и информация) ---
    public static final int TAB_WARRIOR = 0;
    public static final int TAB_HUNTER  = 1;
    public static final int TAB_PRIEST  = 2;
    public static final int TAB_MAGE    = 3;
    public static final int TAB_ROGUE   = 4;
    public static final int TAB_INFO    = 8;

    // --- Нижний ряд (Навигация и закрытие) ---
    public static final int NAV_PREV  = 45;
    public static final int NAV_CLOSE = 49;
    public static final int NAV_NEXT  = 53;

    // --- Границы сетки талантов (Ряды 2-5, Колонки 2-8) ---
    public static final int GRID_START_ROW = 1;
    public static final int GRID_END_ROW   = 4;
    public static final int GRID_START_COL = 1;
    public static final int GRID_END_COL   = 7;

    /**
     * Конвертирует координаты сетки (row, col) в плоский индекс слота инвентаря.
     *
     * @param row строка (0-5)
     * @param col колонка (0-8)
     * @return индекс слота (0-53)
     */
    public static int toSlot(int row, int col) {
        return (row * 9) + col;
    }

    /**
     * Проверяет, является ли слот частью сетки для размещения узлов талантов.
     */
    public static boolean isGridSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= GRID_START_ROW && row <= GRID_END_ROW 
            && col >= GRID_START_COL && col <= GRID_END_COL;
    }
}
