// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

/**
 * 1.11.4 (P4b): карта слотов Книги класса (единый источник для всех таб-вью).
 * Каркас 54 слота: row0 рамка + эмблема (4); row1-4 контент; row5 навигация.
 */
public final class BookSlots {

    private BookSlots() {
    }

    public static final int SIZE = 54;
    public static final int SLOT_EMBLEM = 4;

    public static final int SLOT_TAB_ABILITIES = 45;
    public static final int SLOT_TAB_SPECS = 46;
    public static final int SLOT_TAB_CLASS = 47;
    public static final int SLOT_TAB_TALENTS = 48;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_TAB_GEAR = 50;

    public static final int[] ABILITY_SLOTS = {11, 12, 13, 14, 15};
    public static final int SLOT_INSTALL = 22;

    public static final int[] SPEC_SLOTS = {20, 24};
    public static final int SLOT_RESPEC = 40;

    public static final int[] PASSIVE_SLOTS = {29, 30, 31, 32};
    public static final int SLOT_ATTRIBUTES = 20;
    public static final int SLOT_RESIST = 22;
    public static final int SLOT_CROWN = 24;

    public static final int[] TALENT_NODE_SLOTS = {2, 6, 10, 12, 14, 16, 20, 24, 31};
    public static final int SLOT_TALENT_INFO = 22;
    public static final int SLOT_TALENT_RESET = 40;
    public static final long RESET_ARM_MILLIS = 30_000L;

    public static final int SLOT_GEAR_WEAPON = 10;
    public static final int SLOT_GEAR_STATS = 13;
    public static final int[] GEAR_ARMOR_SLOTS = {19, 20, 21, 22};
    public static final int[] GEAR_SET_SLOTS = {28, 29, 30, 31};

    /** Индекс слота в массиве или -1. */
    public static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
