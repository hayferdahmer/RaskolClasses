// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.EffectType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Активные способности охотника (ресурс — концентрация). */
public final class HunterAbilities {

    private static final int[] FAN_ANGLES = {-10, -5, 5, 10};
    private static final float ARROW_SPEED = 2.5f;
    private static final int BARRAGE_ARROWS = 6;

    private final RaskolClasses plugin;

    public HunterAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Прицельный выстрел: разовый флаг — следующая стрела ×2 + Slowness II 3 с. */
    public boolean aimedShot(Player player, AbilityDef def) {
        plugin.getEffects().addOneShot(player.getUniqueId(), EffectType.AIMED_SHOT);
        return true;
    }

    /** Аспект гепарда: Скорость I (duration-speed, 8 с) + нет урона падения (duration-no-fall, 10 с). */
    public boolean cheetahAspect(Player player, AbilityDef def) {
        RaskolConfig config = plugin.getRaskolConfig();
        int speedTicks = config
                .durationSeconds(PlayerClass.HUNTER, "cheetah_aspect", "speed", 8) * 20;
        long noFallMillis = config
                .durationSeconds(PlayerClass.HUNTER, "cheetah_aspect", "no-fall", 10) * 1000L;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedTicks, 0));
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.NO_FALL_DAMAGE, noFallMillis);
        return true;
    }

    /** Мультивыстрел: 4 дополнительные стрелы веером ±5° и ±10° от взгляда. */
    public boolean multiShot(Player player, AbilityDef def) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (int angle : FAN_ANGLES) {
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setVelocity(direction.clone().multiply(ARROW_SPEED)
                    .rotateAroundY(Math.toRadians(angle)));
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        return true;
    }

    /** Заградительный огонь: 6 стрел с Y+20 над точкой взгляда, разброс 2 блока. */
    public boolean barrage(Player player, AbilityDef def) {
        RayTraceResult hit = player.rayTraceBlocks(30);
        Vector point;
        if (hit != null && hit.getHitPosition() != null) {
            point = hit.getHitPosition();
        } else {
            // Преград нет: точка в 15 блоках по направлению взгляда
            Location eye = player.getEyeLocation();
            point = eye.toVector().add(eye.getDirection().multiply(15));
        }

        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < BARRAGE_ARROWS; i++) {
            Vector from = point.clone().add(new Vector(
                    random.nextDouble(-2.0, 2.0), 20.0, random.nextDouble(-2.0, 2.0)));
            Vector to = point.clone().add(new Vector(
                    random.nextDouble(-1.0, 1.0), 0.0, random.nextDouble(-1.0, 1.0)));
            Vector direction = to.subtract(from).normalize();
            Arrow arrow = world.spawnArrow(from.toLocation(world), direction, 1.5f, 0f);
            arrow.setShooter(player); // урон и килл-сообщения — от охотника
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        return true;
    }
}