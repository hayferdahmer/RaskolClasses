// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import net.kyori.adventure.text.format.NamedTextColor;

/** Пять классов сервера «РАСКОЛ». Цвета сообщений и скиллы — по ТЗ. */
public enum PlayerClass {

    WARRIOR("Воин", NamedTextColor.RED, "fighting"),
    HUNTER("Охотник", NamedTextColor.DARK_GREEN, "archery"),
    PRIEST("Жрец", NamedTextColor.WHITE, "healing"),
    MAGE("Маг", NamedTextColor.BLUE, "sorcery"),
    ROGUE("Разбойник", NamedTextColor.DARK_PURPLE, "agility");

    private final String displayName;
    private final NamedTextColor color;
    /** Профильный скилл AuraSkills: его уровень открывает способности. */
    private final String profileSkillName;

    PlayerClass(String displayName, NamedTextColor color, String profileSkillName) {
        this.displayName = displayName;
        this.color = color;
        this.profileSkillName = profileSkillName;
    }

    /** class_warrior → WARRIOR; всё остальное → null. */
    public static PlayerClass fromLuckPermsGroup(String group) {
        if (group == null || !group.startsWith("class_")) {
            return null;
        }
        String suffix = group.substring("class_".length()).toUpperCase();
        for (PlayerClass pc : values()) {
            if (pc.name().equals(suffix)) {
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
        };
    }
}