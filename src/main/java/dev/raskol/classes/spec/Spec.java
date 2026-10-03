// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.classsystem.PlayerClass;

/**
 * Специализации классов (1.4.0 → 1.14.0).
 * 1.4.0: 12 спеков (2 на класс), выбор на 40 уровне, один раз, бесплатно.
 * 1.10.0: +BLACK_MAGE, HELL_CHANNEL (чернокнижник, общее дерево occult).
 * 1.14.0: расширение до 18 спеков (3 на класс) по WoW-референсу:
 *   - Воин: Guardian (танк), Berserker (ДД flurry), Arms (ДД burst)
 *   - Охотник: Marksman (снайпер), Survival (ДД+контроль), Beast Master (пет-танк)
 *   - Чернокнижник: Affliction (DoT), Destruction (burst FIRE), Demonology (петы)
 *   - Жрец: Holy (групповой хил), Shadowweaver (ДД лифстил), Discipline (щиты)
 *   - Маг: Arcane (burst ARCANE), Frost (контроль FROST), Fire (burst FIRE)
 *   - Разбойник: Assassination (яды), Outlaw (sustained), Subtlety (опенер-бурст)
 *
 * Миграция: старые specId (TRACKER, BLACK_MAGE, HELL_CHANNEL, LIGHTBEARER,
 * LIQUIDATOR, TRICKSTER) остаются валидными через алиасы в fromId().
 * Игроки со старыми спеками продолжают играть; для перехода на 18 спеков — respec.
 */
public enum Spec {
    // === ВОИН (3 спеки) ===
    GUARDIAN(PlayerClass.WARRIOR, "Страж", "⚔", "Танк: таунт + защита"),
    BERSERKER(PlayerClass.WARRIOR, "Берсерк", "⚔", "ДД: ярость + flurry"),
    ARMS(PlayerClass.WARRIOR, "Оружие", "⚔", "ДД: burst + execute"),

    // === ОХОТНИК (3 спеки) ===
    MARKSMAN(PlayerClass.HUNTER, "Стрелок", "➳", "ДД: снайпер, крит с дистанции"),
    SURVIVAL(PlayerClass.HUNTER, "Выживание", "➳", "ДД: контроль + пет ближний"),
    BEAST_MASTER(PlayerClass.HUNTER, "Повелитель зверей", "➳", "ДД: пет-танк"),

    // === ЖРЕЦ (3 спеки) ===
    HOLY(PlayerClass.PRIEST, "Свет", "✚", "Лекарь: групповое лечение"),
    SHADOWWEAVER(PlayerClass.PRIEST, "Тенеплёт", "✚", "ДД: лифстил от урона"),
    DISCIPLINE(PlayerClass.PRIEST, "Послушание", "✚", "Лекарь: щиты + Atonement"),

    // === МАГ (3 спеки) ===
    ARCANE(PlayerClass.MAGE, "Тайная магия", "✦", "ДД: burst ARCANE"),
    FROST(PlayerClass.MAGE, "Лёд", "✦", "ДД: контроль FROST"),
    FIRE(PlayerClass.MAGE, "Огонь", "✦", "ДД: burst FIRE + DoT"),

    // === РАЗБОЙНИК (3 спеки) ===
    ASSASSIN(PlayerClass.ROGUE, "Ликвидация", "☠", "ДД: яды + казнь"),
    OUTLAW(PlayerClass.ROGUE, "Головорез", "☠", "ДД: sustained + уклонение"),
    SUBTLETY(PlayerClass.ROGUE, "Скрытность", "☠", "ДД: опенер-бурст из стелса"),

    // === ЧЕРНОКНИЖНИК (3 спеки, 1.14.0: разделение общего occult на 3 дерева) ===
    AFFLICTION(PlayerClass.WARLOCK, "Колдовство", "☾", "ДД: DoT SHADOW"),
    DESTRUCTION(PlayerClass.WARLOCK, "Разрушение", "☾", "ДД: burst FIRE"),
    DEMONOLOGY(PlayerClass.WARLOCK, "Демонология", "☾", "ДД: петы + SHADOW");

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

    /**
     * Найти спеку по ID (null если не найдена).
     * 1.14.0: алиасы для старых specId (обратная совместимость с 1.13.0 и ранее):
     *   tracker → survival
     *   lightbearer → holy
     *   liquidator → assassin
     *   trickster → outlaw
     *   black_mage → affliction (ближайшая по лору, но игрок со старой спекой продолжает играть)
     *   hell_channel → demonology (ближайшая по лору)
     */
    public static Spec fromId(String id) {
        if (id == null || id.isEmpty()) return null;
        
        // Алиасы для старых specId (1.13.0 и ранее)
        String normalized = switch (id.toLowerCase()) {
            case "tracker" -> "survival";
            case "lightbearer" -> "holy";
            case "liquidator" -> "assassin";
            case "trickster" -> "outlaw";
            case "black_mage" -> "affliction"; // legacy → ближайшая новая
            case "hell_channel" -> "demonology"; // legacy → ближайшая новая
            default -> id.toUpperCase();
        };
        
        try {
            return Spec.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Три спеки для класса (1.14.0: было 2, стало 3). */
    public static Spec[] forClass(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> new Spec[]{GUARDIAN, BERSERKER, ARMS};
            case HUNTER -> new Spec[]{MARKSMAN, SURVIVAL, BEAST_MASTER};
            case PRIEST -> new Spec[]{HOLY, SHADOWWEAVER, DISCIPLINE};
            case MAGE -> new Spec[]{ARCANE, FROST, FIRE};
            case ROGUE -> new Spec[]{ASSASSIN, OUTLAW, SUBTLETY};
            case WARLOCK -> new Spec[]{AFFLICTION, DESTRUCTION, DEMONOLOGY};
        };
    }
}
