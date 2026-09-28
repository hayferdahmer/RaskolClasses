// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 1.11.4 (P2): общая база обработчиков: конфиг, соседние сущности, фракции, notify. */
public abstract class BaseInstallationHandler implements InstallationHandler {

    protected final RaskolClasses plugin;

    protected BaseInstallationHandler(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    protected double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    protected int cfgI(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    protected List<Entity> nearby(Location loc, double radius) {
        return new ArrayList<>(loc.getWorld().getNearbyEntities(loc, radius, radius, radius));
    }

    protected boolean isAllyOf(UUID ownerId, Player target) {
        if (target.getUniqueId().equals(ownerId)) {
            return true;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null) {
            return false;
        }
        return !plugin.getCombat().canHit(owner, target);
    }

    protected boolean isEnemyOf(UUID ownerId, LivingEntity target) {
        if (target instanceof Player tp) {
            Player owner = plugin.getServer().getPlayer(ownerId);
            return owner != null && plugin.getCombat().canHit(owner, tp);
        }
        return true;
    }

    protected void notifyOwner(UUID ownerId, String text) {
        if (!plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage(Component.text(text, NamedTextColor.YELLOW));
        }
    }

    @Override
    public int cooldownSeconds() {
        return cfgI("installations.ttl-seconds", 60);
    }

    @Override
    public int durationSeconds() {
        return cfgI("installations.ttl-seconds", 60);
    }
}
