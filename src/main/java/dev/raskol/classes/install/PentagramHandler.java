// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.DamageProfile;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P2): Пентаграмма (экс-Круг Хулы) — видимая гео-пентаграмма
 * (кольцо огней душ + пятилучевая звезда, шаг 0.5 блока), перерисовка каждые
 * 10 тиков; постановка = призыв визора; эмбиент = ад; в аду урон ×6.
 */
public final class PentagramHandler extends BaseInstallationHandler {

    private final Map<UUID, BukkitTask> redrawTasks = new ConcurrentHashMap<>();

    public PentagramHandler(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public InstallationType type() {
        return InstallationType.HERESY_CIRCLE;
    }

    @Override
    public double radius() {
        return cfgD("installations.heresy_circle.radius", 6.0);
    }

    @Override
    public int cooldownSeconds() {
        return cfgI("installations.heresy_circle.cooldown", 60);
    }

    @Override
    public int durationSeconds() {
        return cfgI("installations.heresy_circle.duration", 25);
    }

    @Override
    public boolean place(Player p, Location loc, UUID instId) {
        plugin.getFx().playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.8f);
        double radius = radius();
        drawPentagram(loc, radius);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                BukkitTask self = redrawTasks.get(instId);
                if (self == null || loc.getWorld() == null) {
                    if (self != null) {
                        self.cancel();
                    }
                    redrawTasks.remove(instId);
                    return;
                }
                drawPentagram(loc, radius);
            }
        }, 10L, 10L);
        redrawTasks.put(instId, task);
        notifyOwner(p.getUniqueId(), "Пентаграмма начертана: действует "
                + durationSeconds() + " с");
        return true;
    }

    @Override
    public boolean tick(ActiveInstallation inst) {
        plugin.getFx().playSound(inst.location(), Sound.ENTITY_BLAZE_AMBIENT, 0.18f, 0.9f);
        double radius = radius();
        double tick = cfgD("installations.heresy_circle.damage-magic", 4.0);
        double corr = cfgD("installations.heresy_circle.corruption-per-sec", 3.0);
        Player owner = plugin.getServer().getPlayer(inst.owner());
        for (Entity e : nearby(inst.location(), radius)) {
            if (e instanceof Player t && isAllyOf(inst.owner(), t)) {
                continue;
            }
            if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                double dmg = tick;
                if (owner != null && owner.getWorld().getEnvironment()
                        == World.Environment.NETHER) {
                    dmg *= plugin.getRaskolConfig().warlockNetherMult();
                }
                if (owner != null) {
                    plugin.getCombat().dealDamage(t, owner, DamageProfile.magic(dmg));
                    riseSoul(t.getLocation());
                }
            }
        }
        if (owner != null
                && owner.getWorld().equals(inst.location().getWorld())
                && owner.getLocation().distanceSquared(inst.location()) <= radius * radius) {
            plugin.getResources().add(owner.getUniqueId(), corr);
        }
        return true;
    }

    @Override
    public void expire(ActiveInstallation inst) {
        BukkitTask task = redrawTasks.remove(inst.id());
        if (task != null) {
            task.cancel();
        }
        plugin.getFx().impactBurst(inst.location(), Particle.SMOKE, 14,
                Sound.ENTITY_BLAZE_DEATH, 0.3f, 0.8f);
    }

    private void riseSoul(Location loc) {
        if (loc.getWorld() == null) {
            return;
        }
        try {
            loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0.0, 0.4, 0.0),
                    3, 0.25, 0.6, 0.25, 0.05);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Кольцо огней душ + пятилучевая звезда (вершина k → k+2), шаг 0.5 блока. */
    private void drawPentagram(Location center, double radius) {
        World w = center.getWorld();
        if (w == null) {
            return;
        }
        double y = center.getY() + 0.06;
        double cx = center.getX();
        double cz = center.getZ();
        try {
            int ring = Math.max(24, (int) (Math.PI * 2 * radius / 0.5));
            for (int i = 0; i < ring; i++) {
                double angle = (Math.PI * 2 * i) / ring;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius,
                        1, 0, 0, 0, 0.0);
            }
            double[] vx = new double[5];
            double[] vz = new double[5];
            double starR = radius * 0.95;
            for (int k = 0; k < 5; k++) {
                double a = Math.PI / 2.0 + k * (Math.PI * 2.0 / 5.0);
                vx[k] = Math.cos(a) * starR;
                vz[k] = Math.sin(a) * starR;
            }
            for (int k = 0; k < 5; k++) {
                int j = (k + 2) % 5;
                double dx = vx[j] - vx[k];
                double dz = vz[j] - vz[k];
                double len = Math.sqrt(dx * dx + dz * dz);
                int steps = Math.max(8, (int) (len / 0.5));
                for (int s = 0; s <= steps; s++) {
                    double t = (double) s / steps;
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            cx + vx[k] + dx * t, y, cz + vz[k] + dz * t,
                            1, 0, 0, 0, 0.0);
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}
