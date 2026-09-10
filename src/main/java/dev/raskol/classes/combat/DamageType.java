// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * 1.6.0: типы урона.
 * PHYSICAL — оружие, руки, взрывы и т.п.
 * MAGIC    — способности, яды, огонь, иссушение и т.п.
 * TRUE     — чистый урон: игнорирует резисты и броню целиком.
 * «Гибрид» — это профиль с двумя ненулевыми компонентами (DamageProfile),
 * отдельным типом не хранится.
 * Карта причин vanilla → тип может переопределяться конфигом
 * (damage-types.vanilla-map.<CAUSE> = physical|magic|true).
 *
 * 1.6.8: CRAMMING/DRYOUT явно PHYSICAL.
 * 1.7.0.2: НЕОТВРАТИМАЯ СРЕДА = TRUE: FALL, DROWNING, SUFFOCATION, STARVATION —
 * падение/утопление/удушение/голод игнорируют резисты и dodge/parry целиком
 * (жалоба: «упал с огромной высоты и выжил»). Летальность восстанавливается
 * масштабом env-lethal-scale в CombatService (урон среды × maxHP/20).
 * FREEZE остаётся MAGIC (магический холод, тематически резистируется).
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
            case VOID, SONIC_BOOM,
                 FALL, DROWNING, SUFFOCATION, STARVATION -> TRUE; // 1.7.0.2: неотвратимая среда
            case CRAMMING, DRYOUT -> PHYSICAL; // 1.6.8: явное перечисление
            default -> PHYSICAL;
        };
    }
}
