// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 1.11.4 (P1): общая база пассивок: конфиг-хелперы, proc-КД, утилиты. */
public abstract class BaseClassPassive implements ClassPassive {

    protected final RaskolClasses plugin;
    private final Map<UUID, Map<String, Long>> lastProc = new ConcurrentHashMap<>();

    protected BaseClassPassive(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    protected double cfgD(String id, String key, double def) {
        return plugin.getRaskolConfig().passiveDouble(playerClass(), id, key, def);
    }

    protected int cfgI(String id, String key, int def) {
        return plugin.getRaskolConfig().passiveInt(playerClass(), id, key, def);
    }

    protected boolean enabled(String id) {
        return plugin.getRaskolConfig().passiveEnabled(playerClass(), id);
    }

    protected boolean procCdOk(UUID uuid, String id, int cdSeconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> map = lastProc.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Long prev = map.get(id);
        if (prev != null && now - prev < cdSeconds * 1000L) {
            return false;
        }
        map.put(id, now);
        return true;
    }

    @Override
    public void clear(UUID uuid) {
        lastProc.remove(uuid);
    }

    protected boolean roll(double chance) {
        return Math.random() < chance;
    }

    protected double hpFraction(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        return max > 0 ? e.getHealth() / max : 1.0;
    }

    protected boolean isBehind(LivingEntity target, Player attacker) {
        Vector facing = target.getLocation().getDirection();
        Vector toAtt = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        double angle = AttributeMath.angleToAttacker(
                facing.getX(), facing.getZ(), toAtt.getX(), toAtt.getZ());
        return AttributeMath.isBack(angle,
                plugin.getConfig().getDouble("avoidance.back-angle", 135.0));
    }

    public static Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
