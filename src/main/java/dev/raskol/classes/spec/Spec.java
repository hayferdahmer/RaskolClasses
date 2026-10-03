// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.classsystem.PlayerClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Специализации классов (1.4.0 → 1.14.0).
 * 1.14.0: 18 АКТИВНЫХ спек (3 на класс) по WoW-референсу + 6 LEGACY-констант
 * (TRACKER, LIGHTBEARER, LIQUIDATOR, TRICKSTER, BLACK_MAGE, HELL_CHANNEL).
 *
 * LEGACY-константы существуют ТОЛЬКО чтобы compile-сайты 1.13.0 (ResourceService,
 * SpecEffects, SpecPassives, SpecListener) продолжали собираться; игроку они
 * недоступны: forClass() и activeValues() их не возвращают, fromId() нормализует
 * legacy-id в современную спеку (tracker→SURVIVAL и т.д.), legacy()=true.
 *
 * Обнуление старых билдов: покупки талантов под legacy-ключами становятся
 * сиротами (деревья читаются по современному id) и не дают эффектов;
 * purge очков по сиротским ключам — Батч 2 (1.14.0).
 */
public enum Spec {
    // === ВОИН (3 активные) ===
    GUARDIAN(PlayerClass.WARRIOR, "Страж", "⚔", "Танк: таунт + защита", false),
    BERSERKER(PlayerClass.WARRIOR, "Берсерк", "⚔", "ДД: ярость + flurry", false),
    ARMS(PlayerClass.WARRIOR, "Оружие", "⚔", "ДД: burst + execute", false),

    // === ОХОТНИК (3 активные) ===
    MARKSMAN(PlayerClass.HUNTER, "Стрелок", "➳", "ДД: снайпер, крит с дистанции", false),
    SURVIVAL(PlayerClass.HUNTER, "Выживание", "➳", "ДД: контроль + ловушки", false),
    BEAST_MASTER(PlayerClass.HUNTER, "Повелитель зверей", "➳", "ДД: питомец", false),

    // === ЖРЕЦ (3 активные) ===
    HOLY(PlayerClass.PRIEST, "Свет", "✚", "Лекарь: групповое лечение", false),
    SHADOWWEAVER(PlayerClass.PRIEST, "Тенеплёт", "✚", "ДД: лифстил от урона", false),
    DISCIPLINE(PlayerClass.PRIEST, "Послушание", "✚", "Лекарь: щиты", false),

    // === МАГ (3 активные) ===
    ARCANE(PlayerClass.MAGE, "Тайная магия", "✦", "ДД: burst ARCANE", false),
    FROST(PlayerClass.MAGE, "Лёд", "✦", "ДД: контроль FROST", false),
    FIRE(PlayerClass.MAGE, "Огонь", "✦", "ДД: burst FIRE + DoT", false),

    // === РАЗБОЙНИК (3 активные) ===
    ASSASSIN(PlayerClass.ROGUE, "Ликвидация", "☠", "ДД: яды + казнь", false),
    OUTLAW(PlayerClass.ROGUE, "Головорез", "☠", "ДД: sustained + уклонение", false),
    SUBTLETY(PlayerClass.ROGUE, "Скрытность", "☠", "ДД: опенер-бурст", false),

    // === ЧЕРНОКНИЖНИК (3 активные) ===
    AFFLICTION(PlayerClass.WARLOCK, "Колдовство", "☾", "ДД: DoT SHADOW", false),
    DESTRUCTION(PlayerClass.WARLOCK, "Разрушение", "☾", "ДД: burst FIRE", false),
    DEMONOLOGY(PlayerClass.WARLOCK, "Демонология", "☾", "ДД: питомец + Скверна", false),

    // === LEGACY (1.13.0 и ранее): только для компиляции старых switch-сайтов ===
    TRACKER(PlayerClass.HUNTER, "Следопыт (устар.)", "➳", "legacy", true),
    LIGHTBEARER(PlayerClass.PRIEST, "Светоносец (устар.)", "✚", "legacy", true),
    LIQUIDATOR(PlayerClass.ROGUE, "Ликвидатор (устар.)", "☠", "legacy", true),
    TRICKSTER(PlayerClass.ROGUE, "Трюкач (устар.)", "☠", "legacy", true),
    BLACK_MAGE(PlayerClass.WARLOCK, "Чёрный Маг (устар.)", "☾", "legacy", true),
    HELL_CHANNEL(PlayerClass.WARLOCK, "Адский Канал (устар.)", "☾", "legacy", true);

    private final PlayerClass playerClass;
    private final String displayName;
    private final String symbol;
    private final String role;
    private final boolean legacy;

    Spec(PlayerClass playerClass, String displayName, String symbol, String role, boolean legacy) {
        this.playerClass = playerClass;
        this.displayName = displayName;
        this.symbol = symbol;
        this.role = role;
        this.legacy = legacy;
    }

    public PlayerClass playerClass() { return playerClass; }
    public String displayName() { return displayName; }
    public String symbol() { return symbol; }
    public String role() { return role; }
    public boolean legacy() { return legacy; }

    /** ID для хранения и LP-ноды. */
    public String id() { return name().toLowerCase(); }

    /** Современный аналог legacy-спеки (для себя возвращает this). */
    public Spec modernOf() {
        return switch (this) {
            case TRACKER -> SURVIVAL;
            case LIGHTBEARER -> HOLY;
            case LIQUIDATOR -> ASSASSIN;
            case TRICKSTER -> OUTLAW;
            case BLACK_MAGE -> AFFLICTION;
            case HELL_CHANNEL -> DEMONOLOGY;
            default -> this;
        };
    }

    /**
     * Найти спеку по ID из хранилища: legacy-id нормализуется в современную
     * спеку (обнуление старого билда происходит на уровне талантов-сирот).
     */
    public static Spec fromId(String id) {
        if (id == null || id.isEmpty()) return null;
        String normalized = switch (id.toLowerCase()) {
            case "tracker" -> "SURVIVAL";
            case "lightbearer" -> "HOLY";
            case "liquidator" -> "ASSASSIN";
            case "trickster" -> "OUTLAW";
            case "black_mage" -> "AFFLICTION";
            case "hell_channel" -> "DEMONOLOGY";
            default -> id.toUpperCase();
        };
        try {
            return Spec.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Только активные (не legacy) спеки. */
    public static Spec[] activeValues() {
        List<Spec> out = new ArrayList<>();
        for (Spec s : values()) {
            if (!s.legacy) {
                out.add(s);
            }
        }
        return out.toArray(new Spec[0]);
    }

    /** Три активные спеки класса (legacy не показываются). */
    public static Spec[] forClass(PlayerClass pc) {
        List<Spec> out = new ArrayList<>();
        for (Spec s : activeValues()) {
            if (s.playerClass() == pc) {
                out.add(s);
            }
        }
        return out.toArray(new Spec[0]);
    }
}
