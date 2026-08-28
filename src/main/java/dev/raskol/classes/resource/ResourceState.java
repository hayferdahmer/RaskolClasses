// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

/** Состояние ресурса игрока за сессию (0–100, без БД). */
public final class ResourceState {

    public static final double MAX_VALUE = 100.0;

    private double value;
    /** Время последнего события урона (нанесён или получен) — окно «в бою». */
    private long lastCombatMillis;
    /** Кап «1 событие урона в секунду» для ярости воина. */
    private long lastGainEventMillis;

    public ResourceState() {
        this.value = 0.0;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = Math.max(0.0, Math.min(MAX_VALUE, value));
    }

    public void add(double amount) {
        setValue(value + amount);
    }

    /** true и списание, если хватает; иначе false без изменений. */
    public boolean consume(double amount) {
        if (value < amount) {
            return false;
        }
        value -= amount;
        return true;
    }

    public void markCombat() {
        this.lastCombatMillis = System.currentTimeMillis();
    }

    public boolean isInCombat(long windowMillis) {
        return System.currentTimeMillis() - lastCombatMillis < windowMillis;
    }

    /** true не чаще раза в секунду (кап событий набора ярости). */
    public boolean allowGainEvent() {
        long now = System.currentTimeMillis();
        if (now - lastGainEventMillis < 1_000L) {
            return false;
        }
        lastGainEventMillis = now;
        return true;
    }
}