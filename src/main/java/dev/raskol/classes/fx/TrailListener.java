// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Трейлы снарядов (1.5.0, Пакет 1):
 *  - фаербол мага: пламенный трейл + тихий треск огня каждые 8 тиков;
 *  - стрелы охотника: искровой трейл (CRIT).
 * Таск сам гаснет при смерти снаряда или через 200 тиков — мусора нет.
 */
public final class TrailListener implements Listener {

    private static final int MAX_TRAIL_TICKS = 200;

    private final RaskolClasses plugin;

    public TrailListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        if (projectile instanceof SmallFireball) {
            if (!plugin.getConfig().getBoolean("vfx.trails.fireball", true)) {
                return;
            }
            startTrail(projectile, Particle.FLAME, Sound.BLOCK_CAMPFIRE_CRACKLE, 4);
            return;
        }

        if (projectile instanceof AbstractArrow) {
            if (!plugin.getConfig().getBoolean("vfx.trails.arrows", true)) {
                return;
            }
            PlayerClass pc = plugin.getClassProvider().getClassOf(shooter);
            if (pc == PlayerClass.HUNTER) {
                startTrail(projectile, Particle.CRIT, null, 4);
            }
        }
    }

    private void startTrail(Projectile projectile, Particle particle,
                            Sound sound, int period) {
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += period;
                if (projectile.isDead() || !projectile.isValid()
                        || elapsed > MAX_TRAIL_TICKS || projectile.getWorld() == null) {
                    cancel();
                    return;
                }
                Location loc = projectile.getLocation();
                projectile.getWorld().spawnParticle(particle, loc, 2, 0.1, 0.1, 0.1, 0.0);
                if (sound != null && elapsed % 8 == 0) {
                    projectile.getWorld().playSound(loc, sound, 0.3f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, period, period);
    }
}
