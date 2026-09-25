// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/**
 * Шесть классов сервера «РАСКОЛ». Цвета сообщений и скиллы — по ТЗ.
 * 1.10.0: добавлен WARLOCK (скрытый класс, переход через Фолиант Раскола с 40 ур.).
 */
public enum PlayerClass {

    WARRIOR("Воин", NamedTextColor.RED, "fighting"),
    HUNTER("Охотник", NamedTextColor.DARK_GREEN, "archery"),
    PRIEST("Жрец", NamedTextColor.WHITE, "healing"),
    MAGE("Маг", NamedTextColor.BLUE, "sorcery"),
    ROGUE("Разбойник", NamedTextColor.DARK_PURPLE, "agility"),
    WARLOCK("Чернокнижник", NamedTextColor.LIGHT_PURPLE, "occult");

    private final String displayName;
    private final NamedTextColor color;
    /** Профильный скилл AuraSkills: его уровень открывает способности. */
    private final String profileSkillName;

    PlayerClass(String displayName, NamedTextColor color, String profileSkillName) {
        this.displayName = displayName;
        this.color = color;
        this.profileSkillName = profileSkillName;
    }

    /** class_warrior → WARRIOR; class_warlock → WARLOCK; всё остальное → null. */
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

    /** 1.3.2: "warrior" / "mage" / "warlock" / ... → enum; пусто/unknown → null. */
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

    public String getResourceName() {
        return switch (this) {
            case WARRIOR -> "Ярость";
            case HUNTER -> "Концентрация";
            case PRIEST -> "Свет";
            case MAGE -> "Мана";
            case ROGUE -> "Энергия";
            case WARLOCK -> "Скверна";
        };
    }
}
