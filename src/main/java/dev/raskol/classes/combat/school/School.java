// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat.school;

import java.util.Locale;

import org.bukkit.Particle;

import dev.raskol.classes.combat.DamageType;

/**
 * 1.12.0: ШКОЛА урона — природа урона (визуал, иммунитеты, уязвимости, триггеры).
 * Канал защиты (чем защищаться) даёт toChannel(): PHYSICAL / MAGIC / TRUE.
 * Одна школа всегда маппится на один канал (ТЗ п.1–2); комбинированный урон =
 * несколько школ в SchoolProfile, каждая часть проходит свою защиту.
 * Новые школы (звук/кислота/электричество) добавляются записью в enum + таблицы.
 * 1.12.0-reorg: пакет combat.school (подсистема школ).
 */
public enum School {
    PHYSICAL(DamageType.PHYSICAL, Particle.SWEEP_ATTACK),
    FIRE(DamageType.MAGIC, Particle.FLAME),
    FROST(DamageType.MAGIC, Particle.SNOWFLAKE),
    NATURE(DamageType.MAGIC, Particle.COMPOSTER),
    SHADOW(DamageType.MAGIC, Particle.SOUL),
    HOLY(DamageType.MAGIC, Particle.ENCHANTED_HIT),
    ARCANE(DamageType.MAGIC, Particle.PORTAL),
    TRUE(DamageType.TRUE, Particle.CLOUD);

    private final DamageType channel;
    private final Particle defaultParticle;

    School(DamageType channel, Particle defaultParticle) {
        this.channel = channel;
        this.defaultParticle = defaultParticle;
    }

    /** Канал защиты: определяет, какой резист/броня поглощает урон школы. */
    public DamageType channel() {
        return channel;
    }

    /** Дефолтный партикл школы (vfx-фолбэк, используется с 1.12.6). */
    public Particle defaultParticle() {
        return defaultParticle;
    }

    /** id для конфига: schools.multiplier.<id>, schools.vanilla-school: <CAUSE>: <id>. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Парсинг из конфига; null если неизвестно. */
    public static School fromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
