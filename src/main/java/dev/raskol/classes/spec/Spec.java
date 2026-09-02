// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.classsystem.PlayerClass;

/**
 * Специализации классов (1.4.0, Пакет 1).
 * Выбор на 40 уровне профильного скилла AuraSkills, один раз, бесплатно.
 * Респец — платный (Пакет 3).
 */
public enum Spec {
    // Воин
    GUARDIAN(PlayerClass.WARRIOR, "Страж", "⚔", "Таунт + защита"),
    BERSERKER(PlayerClass.WARRIOR, "Берсерк", "⚔", "Урон при ярости"),
    
    // Охотник
    MARKSMAN(PlayerClass.HUNTER, "Стрелок", "➳", "Крит с дистанции"),
    TRACKER(PlayerClass.HUNTER, "Следопыт", "➳", "Контроль"),
    
    // Жрец
    LIGHTBEARER(PlayerClass.PRIEST, "Светоносец", "✚", "Усиленное лечение"),
    SHADOWWEAVER(PlayerClass.PRIEST, "Тенеплёт", "✚", "Лечение от урона"),
    
    // Маг
    ARCANE(PlayerClass.MAGE, "Аркана", "✦", "Усиленные заклинания"),
    FROST(PlayerClass.MAGE, "Мороз", "✦", "Контроль льдом"),
    
    // Разбойник
    LIQUIDATOR(PlayerClass.ROGUE, "Ликвидатор", "☠", "Критические удары"),
    TRICKSTER(PlayerClass.ROGUE, "Трюкач", "☠", "Уклонение");

    private final PlayerClass playerClass;
    private final String displayName;
    private final String symbol;
    private final String role;

    Spec(PlayerClass playerClass, String displayName, String symbol, String role) {
        this.playerClass = playerClass;
        this.displayName = displayName;
        this.symbol = symbol;
        this.role = role;
    }

    public PlayerClass playerClass() { return playerClass; }
    public String displayName() { return displayName; }
    public String symbol() { return symbol; }
    public String role() { return role; }

    /** ID для хранения и LP-ноды. */
    public String id() { return name().toLowerCase(); }

    /** Найти спеку по ID (null если не найдена). */
    public static Spec fromId(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return Spec.valueOf(id.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Две спеки для класса (A и B). */
    public static Spec[] forClass(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> new Spec[]{GUARDIAN, BERSERKER};
            case HUNTER -> new Spec[]{MARKSMAN, TRACKER};
            case PRIEST -> new Spec[]{LIGHTBEARER, SHADOWWEAVER};
            case MAGE -> new Spec[]{ARCANE, FROST};
            case ROGUE -> new Spec[]{LIQUIDATOR, TRICKSTER};
        };
    }
}
