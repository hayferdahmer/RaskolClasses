// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

/**
 * Состояние ресурса игрока за сессию (0–100, без БД).
 *
 * FIX 1.7.0.1 (hotfix): consume(amount <= 0) возвращает true без мутации value —
 * бесплатные абилки (Аспект гепарда и т.п.) работают всегда, включая случай
 * value == 0. Ранее условие «value < amount» при amount=0 давало «0 < 0 == false»
 * и отказывало игроку с нулевым ресурсом («не хватает ресурса: нужно 0»).
 *
 * Семантика:
 *   consume(0)  → true, value не меняется (бесплатная абилка, всегда доступна);
 *   consume(N>0) → true, value -= N, если value >= N; иначе false без изменений;
 *   consume(N<0) → true, без мутации (страховка от отрицательной цены в конфиге).
 *
 * TECHDEBT (не блокирует 1.7.0.1): lastGainEventMillis = 0 по дефолту,
 * поэтому первый вызов allowGainEvent() после старта JVM даёт «now - 0 < 1000» = true
 * (ложный отказ) — воин не может получить первую единицу ярости первые ~1000 мс
 * после респавна/релоада. Лечится инициализацией lastGainEventMillis = Long.MIN_VALUE
 * или фиксацией «первый вызов всегда успешен». Оставлено как есть, чтобы не менять
 * поведение в минорном хотфиксе; закрыть в 1.7.1.
 */
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

    /**
     * Положительный amount — набор ресурса (с клампом до MAX_VALUE).
     * Отрицательный или 0 — игнорируется (списание только через consume,
     * чтобы не было двух путей мутации состояния).
     */
    public void add(double amount) {
        if (amount <= 0.0) {
            return;
        }
        setValue(value + amount);
    }

    /**
     * Списывает amount из ресурса.
     *   amount <= 0 → всегда успех без мутации (бесплатные абилки);
     *   amount > 0  → успех, если value >= amount; иначе false и value не меняется.
     */
    public boolean consume(double amount) {
        if (amount <= 0.0) {
            return true; // бесплатная абилка — всегда доступна, даже при value == 0
        }
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
