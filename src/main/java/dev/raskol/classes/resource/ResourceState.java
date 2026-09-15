// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

/**
 * Состояние ресурса игрока за сессию (0–100, без БД).
 *
 * Семантика consume (регресс заперт чеком 30 selftest):
 *   consume(0)   → true, value не меняется (бесплатная абилка, всегда доступна);
 *   consume(N>0) → true, value -= N, если value >= N; иначе false без изменений;
 *   add(N>0)     → набор с клампом до MAX_VALUE; add(N<=0) игнорируется
 *                   (списание идёт ТОЛЬКО через consume — один путь мутации).
 *
 * 1.9.1: удалён мёртвый allowGainEvent()/lastGainEventMillis (кап «1 событие урона
 * в секунду» живёт в ResourceService.lastGainMs; метод не вызывался нигде с 1.7.x).
 */
public final class ResourceState {

    public static final double MAX_VALUE = 100.0;

    private double value;
    /** Время последнего события урона (нанесён или получен) — окно «в бою». */
    private long lastCombatMillis;

    public ResourceState() {
        this.value = 0.0;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = Math.max(0.0, Math.min(MAX_VALUE, value));
    }

    /** Положительный amount — набор ресурса (с клампом). Отрицательный/0 игнорируется. */
    public void add(double amount) {
        if (amount <= 0.0) {
            return;
        }
        setValue(value + amount);
    }

    /** Списывает amount. amount <= 0 → всегда успех без мутации. */
    public boolean consume(double amount) {
        if (amount <= 0.0) {
            return true;
        }
        if (value < amount) {
            return false;
        }
        value -= amount;
        return true;
    }

    /** 1.9.1: вход в боевое окно (вызывается ResourceService на событии урона). */
    public void markCombat() {
        this.lastCombatMillis = System.currentTimeMillis();
    }

    public boolean isInCombat(long windowMillis) {
        return System.currentTimeMillis() - lastCombatMillis < windowMillis;
    }
}
