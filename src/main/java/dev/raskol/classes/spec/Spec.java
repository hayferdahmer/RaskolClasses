// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.classsystem.PlayerClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Специализации классов (1.4.0 → 1.14.0 «Спек 2.0»).
 * 1.14.0: ЧИСТЫЕ 18 спеков (3 на класс), legacy-константы удалены вместе
 * с talent-слоем (Б8). Выбор основной спеки — Spec2Service.chooseMain с 15 уровня;
 * роли — SpecRoles (FIGHTER/TANK/HEALER); деревья — spec/registry/trees/*.
 * id() = lowercase name = ключи spec2-деревьев и storage.
 */
public enum Spec {
    // Воин
    ARMS(PlayerClass.WARRIOR, "Оружие", "⚔", "ДД: burst + execute"),
    FURY(PlayerClass.WARRIOR, "Неистовство", "⚔", "ДД: ярость + flurry"),
    GUARD(PlayerClass.WARRIOR, "Защита", "⚔", "Танк: таунт + защита"),
    // Охотник
    MARKSMANSHIP(PlayerClass.HUNTER, "Стрельба", "➳", "ДД: снайпер, крит с дистанции"),
    SURVIVAL(PlayerClass.HUNTER, "Выживание", "➳", "ДД: контроль + ловушки"),
    BEASTMASTER(PlayerClass.HUNTER, "Повелитель зверей", "➳", "ДД: питомец"),
    // Жрец
    DISCIPLINE(PlayerClass.PRIEST, "Послушание", "✚", "Лекарь: щиты"),
    HOLY(PlayerClass.PRIEST, "Свет", "✚", "Лекарь: групповое лечение"),
    SHADOW(PlayerClass.PRIEST, "Тьма", "✚", "ДД: DoT SHADOW"),
    // Маг
    ARCANE(PlayerClass.MAGE, "Тайная магия", "✦", "ДД: burst ARCANE"),
    FIRE(PlayerClass.MAGE, "Огонь", "✦", "ДД: burst FIRE + DoT"),
    FROST(PlayerClass.MAGE, "Лёд", "✦", "ДД: контроль FROST"),
    // Разбойник
    ASSASSINATION(PlayerClass.ROGUE, "Ликвидация", "☠", "ДД: яды + казнь"),
    OUTLAW(PlayerClass.ROGUE, "Головорез", "☠", "ДД: sustained + уклонение"),
    SUBTLETY(PlayerClass.ROGUE, "Скрытность", "☠", "ДД: опенер-бурст"),
    // Чернокнижник
    AFFLICTION(PlayerClass.WARLOCK, "Колдовство", "☾", "ДД: DoT wither"),
    DESTRUCTION(PlayerClass.WARLOCK, "Разрушение", "☾", "ДД: burst FIRE"),
    DEMONOLOGY(PlayerClass.WARLOCK, "Демонология", "☾", "ДД: питомец + Скверна");

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

    /** ID для хранения и LP-нод; совпадает с id дерева в Spec2Registry. */
    public String id() { return name().toLowerCase(); }

    /** Строгий резолв: неизвестный/legacy id → null (миграций больше нет). */
    public static Spec fromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return Spec.valueOf(id.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Три спеки класса. */
    public static Spec[] forClass(PlayerClass pc) {
        List<Spec> out = new ArrayList<>();
        for (Spec s : values()) {
            if (s.playerClass == pc) {
                out.add(s);
            }
        }
        return out.toArray(new Spec[0]);
    }
}
