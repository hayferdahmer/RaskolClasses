// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/**
 * Шесть классов сервера «РАСКОЛ». Цвета сообщений и скиллы — по ТЗ.
 * 1.10.0: добавлен WARLOCK (скрытый класс, переход через Фолиант Душ).
 * 1.10.3-fix: профильный скилл WARLOCK = sorcery (как у мага); дерево occult
 *         удалено из AuraSkills, и ссылки на него блокировали все способности.
 * 1.14.13 (Sprint 9, Волна 13D, P0-4-05): switch(pc) заменены на метаданные —
 *         resourceName/resourceSymbol стали полями enum-значений; HpBarService.symbolOf
 *         читает pc.getResourceSymbol() вместо switch.
 * 1.14.13-fix (Волна 13D, финал): УДАЛЕНЫ захардкоженные combatWindowSeconds/
 *         resourceRegenPerTick — они дублировали конфиг-ключи
 *         (RaskolConfig.combatWindowSeconds / resourceRegen) и не имели читателей
 *         (dead metadata). Взамен добавлена метаданная RegenModel: модель регена
 *         ресурса класса. ResourceService ключует switch по модели, а не по классу:
 *         новый класс = выбор существующей модели без правки switch (OCP, аудит 4-05).
 *         Числовые значения регена/окна остаются в конфиге — админ тюнит без сборки.
 */
public enum PlayerClass {

    WARRIOR("Воин", NamedTextColor.RED, "fighting",
            "Ярость", "⚔", RegenModel.COMBAT_PAUSE),
    HUNTER("Охотник", NamedTextColor.DARK_GREEN, "archery",
            "Концентрация", "➳", RegenModel.COMBAT_PAUSE),
    PRIEST("Жрец", NamedTextColor.WHITE, "healing",
            "Свет", "✚", RegenModel.PLAIN),
    MAGE("Маг", NamedTextColor.BLUE, "sorcery",
            "Мана", "✦", RegenModel.TIERED),
    ROGUE("Разбойник", NamedTextColor.DARK_PURPLE, "agility",
            "Энергия", "☠", RegenModel.PLAIN),
    WARLOCK("Чернокнижник", NamedTextColor.LIGHT_PURPLE, "sorcery",
            "Скверна", "☾", RegenModel.FLOOR_DECAY);

    /**
     * 1.14.13 (Волна 13D): модель регена ресурса класса — ключ switch в ResourceService.
     *  - COMBAT_PAUSE: реген только вне боевого окна, rate = config.resourceRegen(pc);
     *  - PLAIN:        реген всегда, rate = config.resourceRegen(pc);
     *  - TIERED:       4 тира по текущему значению ресурса (config.mageRegenTier1..4);
     *  - FLOOR_DECAY:  пол (floor-regen ниже порога) + декэй вне боя (config.warlock*),
     *                  клампы перехода через пол и overflow-тик Скверны.
     * Числа моделей читаются из СООТВЕТСТВУЮЩЕГО семейства конфиг-ключей; сегодня
     * соответствие модель↔класс 1:1, поэтому поведение идентично pre-13D.
     */
    public enum RegenModel {
        COMBAT_PAUSE,
        PLAIN,
        TIERED,
        FLOOR_DECAY
    }

    private final String displayName;
    private final NamedTextColor color;
    /** Профильный скилл AuraSkills: его уровень открывает способности. */
    private final String profileSkillName;

    /** 1.14.13 (Волна 13D, 4-05): имя ресурса (вместо switch в getResourceName). */
    private final String resourceName;
    /** 1.14.13 (Волна 13D, 4-05): символ ресурса для HUD (вместо switch в HpBarService.symbolOf). */
    private final String resourceSymbol;
    /** 1.14.13 (Волна 13D, финал): модель регена ресурса (вместо switch по классу). */
    private final RegenModel regenModel;

    PlayerClass(String displayName, NamedTextColor color, String profileSkillName,
                String resourceName, String resourceSymbol, RegenModel regenModel) {
        this.displayName = displayName;
        this.color = color;
        this.profileSkillName = profileSkillName;
        this.resourceName = resourceName;
        this.resourceSymbol = resourceSymbol;
        this.regenModel = regenModel;
    }

    /** class_warrior → WARRIOR; всё остальное → null. */
    public static PlayerClass fromLuckPermsGroup(String group) {
        if (group == null || !group.startsWith("class_")) {
            return null;
        }
        String suffix = group.substring("class_".length()).toUpperCase(Locale.ROOT);
        for (PlayerClass pc : values()) {
            if (pc.name().equals(suffix)) {
                return pc;
            }
        }
        return null;
    }

    /** 1.3.2: "warrior" / "mage" / ... → WARRIOR / MAGE / ...; пусто/unknown → null. */
    public static PlayerClass fromCoreId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String upper = id.toUpperCase(Locale.ROOT);
        for (PlayerClass pc : values()) {
            if (pc.name().equals(upper)) {
                return pc;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public String profileSkillName() {
        return profileSkillName;
    }

    /** 1.14.13 (Волна 13D, 4-05): имя ресурса из метаданных (вместо switch). */
    public String getResourceName() {
        return resourceName;
    }

    /** 1.14.13 (Волна 13D, 4-05): символ ресурса для HUD (⚔//✚/✦/☠/☾). */
    public String getResourceSymbol() {
        return resourceSymbol;
    }

    /** 1.14.13 (Волна 13D, финал): модель регена ресурса — ключ switch в ResourceService. */
    public RegenModel regenModel() {
        return regenModel;
    }
}
