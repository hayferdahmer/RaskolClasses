// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.effect;

/**
 * Флаги активных способностей: timed и one-shot. Длительности из config (D2).
 */
public enum EffectType {
    /** Стальная кожа: −80% входящего урона. */
    SHIELD_WALL,
    /** Кровавое безумие: 20% входящего урона возвращается агрессору. */
    BLOOD_FURY,
    /** Legacy-флаг старой Казни: более не выдаётся. */
    EXECUTE,
    /** Прицельный выстрел: следующая стрела ×2 + Slowness. */
    AIMED_SHOT,
    /** Скрытность: невидимость до первой атаки/урона. */
    STEALTH,
    /** Уклонение: полная отмена входящего урона. */
    EVASION,
    /** Аспект гепарда: иммунитет к урону от падения. */
    NO_FALL_DAMAGE
}
