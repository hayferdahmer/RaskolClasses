// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * 1.13.0 (Б3): обратная связь CC — звуки, партиклы, сообщения (messages.cc.*).
 * Дефолтные звуки/партиклы по типам — кодовая карта; конфиг-оверрайды:
 * cc.sounds.<TYPE>.apply / cc.sounds.<TYPE>.expire / cc.types.<TYPE>.particle.
 * Все сообщения проходят через RaskolConfig.message(...) → секция messages.cc.*.
 */
public final class CCFeedback {

    private CCFeedback() {
    }

    /* ------------------------------ дефолты VFX ------------------------------ */

    public static String defaultApplySound(CCType type) {
        return switch (type) {
            case STUN -> "BLOCK_ANVIL_LAND";
            case ROOT -> "BLOCK_GRASS_BREAK";
            case SILENCE -> "ENTITY_ENDERMAN_TELEPORT";
            case DISARM -> "ENTITY_ITEM_BREAK";
            case FEAR -> "ENTITY_WITHER_SPAWN";
            case CHARM -> "ENTITY_PLAYER_LEVELUP";
            case SLOW -> "BLOCK_SNOW_BREAK";
            case BLIND -> "ENTITY_BAT_TAKEOFF";
            case KNOCKBACK -> "ENTITY_GENERIC_EXPLODE";
        };
    }

    public static String defaultExpireSound(CCType type) {
        return switch (type) {
            case STUN -> "BLOCK_NOTE_BLOCK_PLING";
            case ROOT -> "BLOCK_GRASS_PLACE";
            case SILENCE -> "BLOCK_NOTE_BLOCK_HARP";
            case DISARM -> "ENTITY_ITEM_PICKUP";
            case FEAR -> "ENTITY_ENDERMAN_STARE";
            case CHARM -> "BLOCK_NOTE_BLOCK_CHIME";
            case SLOW -> "BLOCK_SNOW_PLACE";
            case BLIND -> "ENTITY_BAT_AMBIENT";
            case KNOCKBACK -> "BLOCK_NOTE_BLOCK_PLING";
        };
    }

    public static Particle defaultParticle(CCType type) {
        return switch (type) {
            case STUN -> Particle.CRIT;
            case ROOT -> Particle.HAPPY_VILLAGER;
            case SILENCE -> Particle.SMOKE;
            case DISARM -> null;
            case FEAR -> Particle.SCULK_SOUL;
            case CHARM -> Particle.HEART;
            case SLOW -> Particle.SNOWFLAKE;
            case BLIND -> Particle.SMOKE;
            case KNOCKBACK -> Particle.POOF;
        };
    }

    private static Particle particleOf(RaskolClasses plugin, CCType type) {
        String name = plugin.getConfig().getString("cc.types." + type.id() + ".particle", null);
        if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name)) {
            return defaultParticle(type);
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return defaultParticle(type);
        }
    }

    private static Sound soundOf(RaskolClasses plugin, String path, String defName) {
        String name = plugin.getConfig().getString(path, defName);
        return plugin.getFx().resolveSound(name != null ? name : defName);
    }

    private static void playAt(RaskolClasses plugin, LivingEntity entity, Sound sound,
                               float volume, float pitch) {
        if (sound == null) {
            return;
        }
        plugin.getFx().playSound(entity.getLocation().add(0.0, 1.0, 0.0), sound, volume, pitch);
    }

    private static void burstAt(RaskolClasses plugin, LivingEntity entity, Particle particle, int count) {
        if (particle == null) {
            return;
        }
        Location loc = entity.getLocation().add(0.0, 1.0, 0.0);
        entity.getWorld().spawnParticle(particle, loc, count, 0.3, 0.4, 0.3, 0.02);
    }

    /* ------------------------------ сообщения ------------------------------ */

    /** «§cОцепенение §7на 3.0с (DR 50%×)» — формат messages.cc.applied. */
    public static String describeApply(RaskolClasses plugin, CCType type, int ticks, double drMult) {
        double seconds = ticks / 20.0;
        return plugin.getRaskolConfig().message("cc.applied",
                        "§c{type} §7на {duration}с (DR {dr-mult}×)")
                .replace("{type}", type.ruName())
                .replace("{duration}", String.format(Locale.ROOT, "%.1f", seconds))
                .replace("{dr-mult}", String.valueOf((int) Math.round(drMult * 100.0)));
    }

    /* ------------------------------ события фидбека ------------------------------ */

    public static void onApply(RaskolClasses plugin, Player caster, LivingEntity target,
                               CCType type, int ticks, double drMult) {
        playAt(plugin, target,
                soundOf(plugin, "cc.sounds." + type.id() + ".apply", defaultApplySound(type)),
                0.7f, 1.0f);
        burstAt(plugin, target, particleOf(plugin, type), 14);
        if (target instanceof Player tp) {
            tp.sendMessage(describeApply(plugin, type, ticks, drMult));
        }
        if (caster != null && !caster.getUniqueId().equals(target.getUniqueId())) {
            caster.sendMessage(plugin.getRaskolConfig().message("cc.applied-to-caster",
                            "§a{target}: {type} §7на {duration}с")
                    .replace("{target}", target.getName())
                    .replace("{type}", type.ruName())
                    .replace("{duration}", String.format(Locale.ROOT, "%.1f", ticks / 20.0)));
        }
    }

    public static void onResist(RaskolClasses plugin, Player caster, LivingEntity target, CCType type) {
        playAt(plugin, target,
                soundOf(plugin, "cc.sounds." + type.id() + ".resist", "ITEM_SHIELD_BLOCK"),
                0.6f, 1.1f);
        if (caster != null) {
            caster.sendMessage(plugin.getRaskolConfig().message("cc.resisted",
                    "§7{target} §eсопротивляется: {type}")
                    .replace("{target}", target.getName())
                    .replace("{type}", type.ruName()));
        }
    }

    public static void onImmune(RaskolClasses plugin, Player caster, LivingEntity target, CCType type) {
        playAt(plugin, target,
                soundOf(plugin, "cc.sounds." + type.id() + ".immune", "BLOCK_NOTE_BLOCK_BASS"),
                0.6f, 0.8f);
        if (caster != null) {
            caster.sendMessage(plugin.getRaskolConfig().message("cc.immune",
                    "§7{target} §cневосприимчив к контролю")
                    .replace("{target}", target.getName()));
        }
    }

    public static void onDrImmune(RaskolClasses plugin, Player caster, LivingEntity target, CCType type) {
        playAt(plugin, target,
                soundOf(plugin, "cc.sounds." + type.id() + ".immune", "BLOCK_NOTE_BLOCK_BASS"),
                0.5f, 0.7f);
        if (caster != null) {
            caster.sendMessage(plugin.getRaskolConfig().message("cc.dr-immune",
                    "§7{target} §cнечувствителен (DR-иммунитет)")
                    .replace("{target}", target.getName()));
        }
    }

    public static void onExpire(RaskolClasses plugin, LivingEntity target, CCType type) {
        playAt(plugin, target,
                soundOf(plugin, "cc.sounds." + type.id() + ".expire", defaultExpireSound(type)),
                0.35f, 1.2f);
        if (target instanceof Player tp) {
            tp.sendMessage(plugin.getRaskolConfig().message("cc.expired", "§7{type} §8рассеялся")
                    .replace("{type}", type.ruName()));
        }
    }

    public static void onInterruptCast(RaskolClasses plugin, LivingEntity target, CCType type) {
        if (target instanceof Player tp) {
            tp.sendMessage(plugin.getRaskolConfig().message("cc.cast-interrupted",
                    "§cКаст прерван: {type}")
                    .replace("{type}", type.ruName()));
        }
    }

    /** Тик-партикл активного CC (раз в 10 тиков из CCService.tick). */
    public static void tickParticle(RaskolClasses plugin, LivingEntity target, CCType type) {
        Particle p = particleOf(plugin, type);
        if (p == null) {
            return;
        }
        target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.2, 0.0),
                1, 0.25, 0.2, 0.25, 0.005);
    }
}
