// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/**
 * Шесть классов сервера «РАСКОЛ». Цвета сообщений и скиллы — по ТЗ.
 * 1.10.0: добавлен WARLOCK (скрытый класс, переход через Фолиант Душ).
 * 1.10.3-fix: профильный скилл WARLOCK = sorcery (как у мага); дерево occult
 *         удалено из AuraSkills, и ссылки на него блокировали все способности.
 * 1.14.13 (Sprint 9, Волна 13D, P0-10-05): switch(pc) заменены на метаданные —
 *         resourceName/resourceSymbol/combatWindowSeconds/resourceRegenPerTick
 *         теперь поля enum-значений, а не результат switch. Это устраняет
 *         дублирование switch-блоков в HpBarService/ResourceService и делает
 *         добавление нового класса (если потребуется) одноточечной правкой.
 */
public enum PlayerClass {

    WARRIOR("Воин", NamedTextColor.RED, "fighting",
            "Ярость", "⚔", 8, 2.0),
    HUNTER("Охотник", NamedTextColor.DARK_GREEN, "archery",
            "Концентрация", "➳", 6, 1.5),
    PRIEST("Жрец", NamedTextColor.WHITE, "healing",
            "Свет", "✚", 5, 1.8),
    MAGE("Маг", NamedTextColor.BLUE, "sorcery",
            "Мана", "✦", 5, 2.0),
    ROGUE("Разбойник", NamedTextColor.DARK_PURPLE, "agility",
            "Энергия", "☠", 4, 2.5),
    WARLOCK("Чернокнижник", NamedTextColor.LIGHT_PURPLE, "sorcery",
            "Скверна", "☾", 5, 1.5);

    private final String displayName;
    private final NamedTextColor color;
    /** Профильный скилл AuraSkills: его уровень открывает способности. */
    private final String profileSkillName;

    /** 1.14.13 (Волна 13D, 10-05): метаданные ресурса (вместо switch в HpBarService). */
    private final String resourceName;
    /** 1.14.13 (Волна 13D, 10-05): символ ресурса для HUD (вместо switch в HpBarService.symbolOf). */
    private final String resourceSymbol;
    /** 1.14.13 (Волна 13D, 10-05): боевое окно в секундах (вместо switch в ResourceService). */
    private final int combatWindowSeconds;
    /** 1.14.13 (Волна 13D, 10-05): базовый реген ресурса за тик (вместо switch в ResourceService). */
    private final double resourceRegenPerTick;

    PlayerClass(String displayName, NamedTextColor color, String profileSkillName,
                String resourceName, String resourceSymbol,
                int combatWindowSeconds, double resourceRegenPerTick) {
        this.displayName = displayName;
        this.color = color;
        this.profileSkillName = profileSkillName;
        this.resourceName = resourceName;
        this.resourceSymbol = resourceSymbol;
        this.combatWindowSeconds = combatWindowSeconds;
        this.resourceRegenPerTick = resourceRegenPerTick;
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

    /**
     * 1.14.13 (Волна 13D, 10-05): делегирует в поле resourceName (вместо switch).
     * Public-метод сохранён для обратной совместимости.
     */
    public String getResourceName() {
        return resourceName;
    }

    /** 1.14.13 (Волна 13D, 10-05): символ ресурса для HUD (⚔/➳/✚/✦/☠/☾). */
    public String getResourceSymbol() {
        return resourceSymbol;
    }

    /** 1.14.13 (Волна 13D, 10-05): боевое окно в секундах (для ResourceService). */
    public int getCombatWindowSeconds() {
        return combatWindowSeconds;
    }

    /** 1.14.13 (Волна 13D, 10-05): базовый реген ресурса за тик (для ResourceService). */
    public double getResourceRegenPerTick() {
        return resourceRegenPerTick;
    }
}
