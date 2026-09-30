// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

/**
 * 1.13.0: состояние убывающей отдачи по категории для одной сущности.
 * Живёт в памяти CCService (не персистится): окно DR — 15 с, персист не нужен.
 *  - stackCount — сколько применений было в текущем окне;
 *  - lastAppliedAt — timestamp последнего применения (мс);
 *  - windowStart — timestamp начала окна (для отладки/команд).
 */
public record DRState(int stackCount, long lastAppliedAt, long windowStart) {

    public static final DRState EMPTY = new DRState(0, 0L, 0L);

    /** Применение: стек +1, окно освежается. */
    public DRState applied(long nowMillis) {
        return new DRState(stackCount + 1, nowMillis, windowStart);
    }

    /** Стек с учётом истечения окна (pure, selftest-чек 77). */
    public int stackAt(long nowMillis, long windowMillis) {
        return (nowMillis - lastAppliedAt) > windowMillis ? 0 : stackCount;
    }
}
