// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.13.0 (Б3): обратная связь CC — звуки, партиклы, сообщения (messages.cc.*).
 * 1.14.0-fix: голограмма TextDisplay над целью при наложении.
 * Дефолтные звуки/партиклы по типам — кодовая карта; оверрайды:
 * cc.sounds.<TYPE>.apply / cc.sounds.<TYPE>.expire / cc.types.<TYPE>.particle.
 *
 * 1.15.6 (Волна 7b, аудит 2.6-46): переработка голограмм:
 *   - ВИДИМОСТЬ: глобальная голограмма над целью для всех КРОМЕ жертвы;
 *     личная голограмма ПЕРЕД жертвой-игроком, видна ТОЛЬКО жертве
 *     (через Paper API: hideEntity/showEntity + setVisibleByDefault);
 *   - ПРИВЯЗКА: оба TextDisplay — passenger цели (Display.addPassenger),
 *     автоматически следуют за целью при движении/телепортах;
 *   - ОЧИСТКА: статический Map activeHologram; clearHologram вызывается из
 *     CCService.clearBehavior при снятии CC (включая breakOnDamage, expiry,
 *     removeAll). При повторном CC на цель старая голограмма перезаписывается.
 *
 * 1.15.7 (compile-fix #1952): addViewer/removeViewer НЕ СУЩЕСТВУЕТ в Paper 1.21.4.
 * Пер-плеер видимость через:
 *   - Player.hideEntity(plugin, entity) — скрыть от конкретного игрока;
 *   - Player.showEntity(plugin, entity) — показать скрытое конкретному игроку;
 *   - Entity.setVisibleByDefault(false) — энтити не видна никому по умолчанию.
 */
public final class CCFeedback {

    private CCFeedback() {
    }

    /** Состояние голограмм цели: глобальная + личная (для Player-жертвы). */
    private record HologramBundle(TextDisplay global, TextDisplay local) { }

    /** Активные голограммы: ключ — UUID цели. Очистка через clearHologram. */
    private static final Map<UUID, HologramBundle> ACTIVE = new ConcurrentHashMap<>();

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

    /* ------------------------------ 2.6-46: голограммы ------------------------------ */

    /**
     * 1.15.6 (2.6-46) + compile-fix #1952: пара TextDisplay — глобальная над целью
     * и личная перед целью. Пер-плеер видимость через Paper API:
     *  - глобальная: видна всем по умолчанию; ЖЕРТВЕ скрываем через
     *    Player.hideEntity(plugin, display);
     *  - личная: setVisibleByDefault(false) в spawn-консьюмере + Player.showEntity —
     *    видна ТОЛЬКО жертве, включая игроков, зашедших позже.
     * Оба дисплея — passenger цели: едут за ней при движении/телепортах.
     */
    private static void hologram(RaskolClasses plugin, LivingEntity target, CCType type, int ticks) {
        if (!plugin.getConfig().getBoolean("cc.feedback-hologram", true)) {
            return;
        }
        if (ticks <= 0 || target == null || target.isDead()) {
            return;
        }
        UUID targetUuid = target.getUniqueId();
        // Очистка предыдущей голограммы (если CC наложили повторно)
        clearHologram(targetUuid);

        double seconds = ticks / 20.0;
        Component text = Component.text(iconOf(type) + " " + type.ruName()
                + " · " + String.format(Locale.ROOT, "%.1f", seconds) + "с",
                NamedTextColor.RED);

        // Глобальная голограмма: над целью, видна всем КРОМЕ жертвы
        TextDisplay global = target.getWorld().spawn(
                target.getLocation().add(0.0, 2.2, 0.0), TextDisplay.class, td -> {
                    td.text(text);
                    td.setBillboard(Display.Billboard.CENTER);
                    td.setSeeThrough(true);
                    td.setShadowed(true);
                });
        if (target instanceof Player tp) {
            tp.hideEntity(plugin, global);
        }
        target.addPassenger(global);

        // Личная голограмма: перед целью, видна ТОЛЬКО жертве
        TextDisplay local = null;
        if (target instanceof Player tp) {
            Location personalLoc = target.getLocation().clone()
                    .add(target.getLocation().getDirection().normalize().multiply(1.5))
                    .add(0.0, 1.8, 0.0);
            local = target.getWorld().spawn(personalLoc, TextDisplay.class, td -> {
                td.text(Component.text(iconOf(type) + " " + type.ruName()
                        + " · " + String.format(Locale.ROOT, "%.1f", seconds) + "с",
                        NamedTextColor.GOLD));
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(true);
                // Paper per-player visibility: по умолчанию не видна никому
                td.setVisibleByDefault(false);
            });
            tp.showEntity(plugin, local);
            target.addPassenger(local);
        }

        ACTIVE.put(targetUuid, new HologramBundle(global, local));

        // Автоочистка по истечении CC (страховка; основная очистка в clearBehavior)
        final TextDisplay globalRef = global;
        final TextDisplay localRef = local;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeDisplay(globalRef);
            removeDisplay(localRef);
            ACTIVE.remove(targetUuid);
        }, Math.max(1L, ticks));
    }

    /**
     * 1.15.6 (2.6-46): удаление голограмм цели. Вызывается из CCService.clearBehavior
     * при снятии CC (включая breakOnDamage, expiry, removeAll).
     */
    public static void clearHologram(UUID targetUuid) {
        if (targetUuid == null) {
            return;
        }
        HologramBundle bundle = ACTIVE.remove(targetUuid);
        if (bundle == null) {
            return;
        }
        removeDisplay(bundle.global());
        removeDisplay(bundle.local());
    }

    private static void removeDisplay(TextDisplay display) {
        if (display == null || display.isDead()) {
            return;
        }
        // Снимаем passenger перед удалением
        if (display.getVehicle() != null) {
            display.getVehicle().removePassenger(display);
        }
        try {
            display.remove();
        } catch (IllegalStateException ignored) {
            // Display уже удалён миром
        }
    }

    private static String iconOf(CCType type) {
        return switch (type) {
            case STUN -> "✦";
            case ROOT -> "⛓";
            case SILENCE -> "☒";
            case DISARM -> "⚔";
            case FEAR -> "☠";
            case CHARM -> "♥";
            case SLOW -> "❄";
            case BLIND -> "◐";
            case KNOCKBACK -> "↗";
        };
    }

    /* ------------------------------ события фидбека ------------------------------ */

    public static void onApply(RaskolClasses plugin, Player caster, LivingEntity target,
                               CCType type, int ticks, double drMult) {
        playAt(plugin, target,
                soundOf(plugin, "cc.sounds." + type.id() + ".apply", defaultApplySound(type)),
                0.7f, 1.0f);
        burstAt(plugin, target, particleOf(plugin, type), 14);
        hologram(plugin, target, type, ticks);
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
        // 1.15.6 (2.6-46): очистка голограммы при истечении CC
        clearHologram(target != null ? target.getUniqueId() : null);
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
