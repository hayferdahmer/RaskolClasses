// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import org.bukkit.Particle;

import java.util.Locale;

/**
 * 1.12.0: ШКОЛА урона — природа урона (визуал, иммунитеты, уязвимости, триггеры).
 * Канал защиты (чем защищаться) даёт toChannel(): PHYSICAL / MAGIC / TRUE.
 * Одна школа всегда маппится на один канал (ТЗ п.1–2); комбинированный урон =
 * несколько школ в SchoolProfile, каждая часть проходит свою защиту.
 * Новые школы (звук/кислота/электричество) добавляются записью в enum + таблицы.
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
