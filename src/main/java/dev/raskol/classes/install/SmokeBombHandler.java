// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/** 1.11.4 (P2): Дымовая шашка — мина: blind врагам, speed владельцу. */
public final class SmokeBombHandler extends BaseInstallationHandler {

    public SmokeBombHandler(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public InstallationType type() {
        return InstallationType.SMOKE_BOMB;
    }

    @Override
    public double radius() {
        return cfgD("installations.smoke_bomb.radius", 3.0);
    }

    @Override
    public boolean place(Player p, Location loc, UUID instId) {
        plugin.getFx().impactBurst(loc, Particle.CLOUD, 10, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.1f);
        notifyOwner(p.getUniqueId(), type().displayName() + " установлена на "
                + durationSeconds() + " с");
        return true;
    }

    @Override
    public boolean tick(ActiveInstallation inst) {
        boolean triggered = false;
        for (Entity e : nearby(inst.location(), radius())) {
            if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                triggered = true;
                break;
            }
        }
        if (!triggered) {
            return true;
        }
        for (Entity e : nearby(inst.location(), radius())) {
            if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
            }
        }
        Player owner = plugin.getServer().getPlayer(inst.owner());
        if (owner != null) {
            owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 0));
        }
        plugin.getFx().impactBurst(inst.location(), Particle.SMOKE, 40,
                Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.8f);
        notifyOwner(inst.owner(), "Дымовая шашка сработала!");
        return false;
    }

    @Override
    public void expire(ActiveInstallation inst) {
        plugin.getFx().impactBurst(inst.location(), Particle.CLOUD, 8, null, 0f, 1f);
    }
}
