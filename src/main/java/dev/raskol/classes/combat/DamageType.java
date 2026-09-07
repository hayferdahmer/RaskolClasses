// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * 1.6.0: типы урона.
 * PHYSICAL — оружие, руки, падение, утопление, взрывы и т.п.
 * MAGIC    — способности, яды, огонь, иссушение и т.п.
 * TRUE     — чистый урон: игнорирует резисты и броню целиком.
 * «Гибрид» — это профиль с двумя ненулевыми компонентами (DamageProfile),
 * отдельным типом не хранится.
 * Карта причин vanilla → тип может переопределяться конфигом
 * (damage-types.vanilla-map.<CAUSE> = physical|magic|true).
 * FIX 1.6.0.2: причина порошкового снега в Bukkit — FREEZE (не FREEZING).
 */
public enum DamageType {

    PHYSICAL,
    MAGIC,
    TRUE;

    /** Дефолтная типизация ванильных причин урона. */
    public static DamageType defaultFor(DamageCause cause) {
        return switch (cause) {
            case MAGIC, POISON, WITHER, FIRE, FIRE_TICK, LAVA, HOT_FLOOR,
                 DRAGON_BREATH, FREEZE, LIGHTNING -> MAGIC;
            case VOID, SONIC_BOOM -> TRUE;
            default -> PHYSICAL;
        };
    }
}
