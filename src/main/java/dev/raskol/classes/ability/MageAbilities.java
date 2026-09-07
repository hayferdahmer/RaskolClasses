// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class MageAbilities {

    private final RaskolClasses plugin;

    public MageAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * Огненная стрела — ГИБРИД: 30 физ (снаряд) + 70 маг (огонь/поджог).
     * Числа читаются из конфига damage-numbers.mage.firebolt.{physical,magic}.
     */
    public boolean firebolt(Player player, AbilityDef def) {
        double phys = plugin.getRaskolConfig().abilityDamagePhysical(PlayerClass.MAGE, def.id(), 30.0);
        double magic = plugin.getRaskolConfig().abilityDamageMagic(PlayerClass.MAGE, def.id(), 70.0);
        Location loc = player.getEyeLocation();
        Vector dir = loc.getDirection();
        Fireball fireball = player.launchProjectile(Fireball.class, dir.multiply(1.5));
        fireball.setIsIncendiary(true);
        fireball.setYield(0f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (fireball.isValid() && !fireball.isDead()) {
                Entity target = null;
                for (Entity e : fireball.getNearbyEntities(2.0, 2.0, 2.0)) {
                    if (e instanceof LivingEntity && e != player) {
                        target = e;
                        break;
                    }
                }
                if (target instanceof LivingEntity living) {
                    plugin.getCombat().dealDamage(living, player,
                            DamageProfile.hybrid(phys, magic));
                    living.setFireTicks(60);
                }
                fireball.remove();
            }
        }, 40L);
        return true;
    }

    public boolean blink(Player player, AbilityDef def) {
        Location loc = player.getLocation();
        Vector dir = loc.getDirection().multiply(8.0);
        Location target = loc.clone().add(dir);
        if (target.getBlock().getType().isSolid()) {
            player.sendMessage(plugin.getRaskolConfig().message("blink-unsafe",
                    "Скачок невозможен: нет безопасной точки"));
            return false;
        }
        player.teleport(target);
        player.getWorld().spawnParticle(Particle.PORTAL, loc, 30, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.PORTAL, target, 30, 0.5, 0.5, 0.5, 0.1);
        return true;
    }

    /** Кольцо льда — МАГ. */
    public boolean frostNova(Player player, AbilityDef def) {
        double magic = plugin.getRaskolConfig().abilityDamageMagic(PlayerClass.MAGE, def.id(), 40.0);
        double radius = 5.0;
        int duration = plugin.getRaskolConfig().durationSeconds(PlayerClass.MAGE, def.id(), 4);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Mob mob && mob != player) {
                plugin.getCombat().dealDamage(mob, player, DamageProfile.magic(magic));
                mob.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration * 20, 2));
            }
        }
        player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                player.getLocation().clone().add(0.0, 0.5, 0.0),
                40, radius * 0.6, 0.3, radius * 0.6, 0.0);
        return true;
    }

    /** Чародейский взрыв — МАГ. */
    public boolean arcaneBurst(Player player, AbilityDef def) {
        double magic = plugin.getRaskolConfig().abilityDamageMagic(PlayerClass.MAGE, def.id(), 100.0);
        double radius = 6.0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && living != player) {
                plugin.getCombat().dealDamage(living, player, DamageProfile.magic(magic));
            }
        }
        player.getWorld().spawnParticle(Particle.POOF,
                player.getLocation().clone().add(0.0, 1.0, 0.0),
                50, 0.8, 0.8, 0.8, 0.05);
        return true;
    }
}
