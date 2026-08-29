// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.effect;

/**
 * Флаги активных способностей: timed и one-shot. Длительности из config (D2).
 */
public enum EffectType {
    /** Стальная кожа: −80% входящего урона. */
    SHIELD_WALL("Стальная кожа"),
    /** Кровавое безумие: 20% входящего урона возвращается агрессору. */
    BLOOD_FURY("Кровавое безумие"),
    /** Legacy-флаг старой Казни: более не выдаётся. displayName = null → фильтр босс-бара. */
    EXECUTE(null),
    /** Прицельный выстрел: следующая стрела ×2 + Slowness. */
    AIMED_SHOT("Прицельный выстрел"),
    /** Скрытность: невидимость до первой атаки/урона. */
    STEALTH("Скрытность"),
    /** Уклонение: полная отмена входящего урона. */
    EVASION("Уклонение"),
    /** Аспект гепарда: иммунитет к урону от падения. */
    NO_FALL_DAMAGE("Аспект гепарда");

    private final String displayName;

    EffectType(String displayName) {
        this.displayName = displayName;
    }

    /** Пакет 5: человекочитаемое имя для босс-бара и отладки. null = legacy (скрыть). */
    public String displayName() {
        return displayName;
    }
}
