// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

/**
 * 1.7.0: классовые атрибуты.
 * STR — СИЛА (основной: Воин): здоровье и физический урон.
 * AGI — ЛОВКОСТЬ (основной: Охотник, Разбойник): крит мили, уклонение, микро-парирование.
 * INT — ИНТЕЛЛЕКТ (основной: Маг, Жрец): магический урон и крит магии.
 */
public enum AttributeType {

    STR("СИЛА"),
    AGI("ЛОВКОСТЬ"),
    INT("ИНТЕЛЛЕКТ");

    private final String displayName;

    AttributeType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Безопасный парсинг из конфига; null при неизвестном имени. */
    public static AttributeType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (AttributeType t : values()) {
            if (t.name().equalsIgnoreCase(id.trim())) {
                return t;
            }
        }
        return null;
    }
}
