// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.DamageProfile;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/** 1.11.4 (P2): Капкан — мина одноразового срабатывания. */
public final class BearTrapHandler extends BaseInstallationHandler {

    public BearTrapHandler(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public InstallationType type() {
        return InstallationType.BEAR_TRAP;
    }

    @Override
    public double radius() {
        return 1.2;
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
        for (Entity e : nearby(inst.location(), radius())) {
            if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                Player owner = plugin.getServer().getPlayer(inst.owner());
                if (owner != null) {
                    plugin.getCombat().dealDamage(t, owner,
                            DamageProfile.physical(cfgD("installations.bear_trap.damage-physical", 3.0)));
                }
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 5));
                plugin.getFx().impactBurst(inst.location(), Particle.CRIT, 12,
                        Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 1.0f);
                notifyOwner(inst.owner(), "Капкан сработал!");
                return false;
            }
        }
        return true;
    }

    @Override
    public void expire(ActiveInstallation inst) {
        plugin.getFx().impactBurst(inst.location(), Particle.CLOUD, 8, null, 0f, 1f);
    }
}
