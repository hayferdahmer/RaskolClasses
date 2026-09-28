// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/** 1.11.4 (P2): Знамя войны — аура Resistance I союзникам в r6. */
public final class WarBannerHandler extends BaseInstallationHandler {

    public WarBannerHandler(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public InstallationType type() {
        return InstallationType.WAR_BANNER;
    }

    @Override
    public double radius() {
        return cfgD("installations.war_banner.radius", 6.0);
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
            if (e instanceof Player t && isAllyOf(inst.owner(), t)) {
                t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 2 * 20, 0));
            }
        }
        return true;
    }

    @Override
    public void expire(ActiveInstallation inst) {
        plugin.getFx().impactBurst(inst.location(), Particle.CLOUD, 8, null, 0f, 1f);
    }
}
