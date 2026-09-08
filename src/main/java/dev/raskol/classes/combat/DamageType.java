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
 *
 * 1.6.8: средовые причины CRAMMING и DRYOUT перечислены явно, чтобы их тип
 * не менялся молча при будущих сменах дефолтов Bukkit. MELTING намеренно не
 * трогаем: константа deprecated, событие фактически не возникает (CI-гейт
 * по deprecation важнее мёртвой ветки).
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
            case CRAMMING, DRYOUT -> PHYSICAL; // 1.6.8: явное перечисление
            default -> PHYSICAL;
        };
    }
}
