// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пассивки всех 10 спеков (1.4.0 + 1.5.0 Пакет 3).
 * 1.5.0.3: проки спеков (крит/додж/лифстил) играют звуки через fx.procByKey.
 */
public final class SpecListener implements Listener {

    private final RaskolClasses plugin;

    public SpecListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    private Spec specOf(UUID uuid) {
        return plugin.getSpecService().getSpec(uuid);
    }

    private SpecRegistry.SpecDef defOf(Spec spec) {
        return plugin.getSpecRegistry().get(spec);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Spec spec = specOf(event.getPlayer().getUniqueId());
        if (spec != null) {
            plugin.getSpecEffects().applyAttributes(event.getPlayer(), spec);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSpecEffects().removeAttributes(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        Spec spec = specOf(uuid);
        if (spec == null) {
            return;
        }
        SpecRegistry.SpecDef def = defOf(spec);
        if (def == null) {
            return;
        }

        double damage = event.getDamage();
        boolean isArrow = event.getDamager() instanceof Arrow;

        if (spec == Spec.BERSERKER) {
            double rage = plugin.getResources().getValue(uuid);
            if (rage >= def.passiveDouble("rage_threshold", 50.0)) {
                damage *= def.passiveDouble("damage_multiplier", 1.15);
            }
        }

        if (spec == Spec.ARCANE) {
            damage *= def.passiveDouble("ability_multiplier", 1.15);
        }

        if (spec == Spec.MARKSMAN && isArrow) {
            double distance = attacker.getLocation()
                    .distance(event.getEntity().getLocation());
            if (distance >= def.passiveDouble("min_distance", 10.0)) {
                damage *= def.passiveDouble("damage_multiplier", 1.20);
            }
        }

        // Ликвидатор: крит + партикл + ЗВУК (1.5.0.3)
        if (spec == Spec.LIQUIDATOR) {
            if (ThreadLocalRandom.current().nextDouble()
                    < def.passiveDouble("crit_chance", 0.10)) {
                damage *= def.passiveDouble("crit_multiplier", 1.5);
                event.getEntity().getWorld().spawnParticle(Particle.CRIT,
                        event.getEntity().getLocation().add(0.0, 1.0, 0.0),
                        6, 0.3, 0.3, 0.3, 0.0);
                plugin.getFx().procByKey(attacker, "⚡ Крит ×1.5!", "crit_liquidator");
            }
        }

        if (isArrow && plugin.getSpecEffects().consumePrecise(uuid)) {
            damage *= 2.0;
        }

        damage += plugin.getSpecEffects().rageBurstBonus(uuid);
        event.setDamage(damage);

        // Тенеплёт: лифстил + сердечки + ЗВУК (1.5.0.3)
        if (spec == Spec.SHADOWWEAVER) {
            double heal = damage * def.passiveDouble("lifesteal_percent", 0.15);
            if (heal > 0.0) {
                attacker.heal(heal);
                attacker.spawnParticle(Particle.HEART,
                        attacker.getLocation().add(0.0, 1.2, 0.0),
                        2, 0.2, 0.2, 0.2, 0.0);
                plugin.getFx().procByKey(attacker, "♥ Лифстил", "lifesteal_shadowweaver");
            }
        }

        if (spec == Spec.FROST && event.getEntity() instanceof LivingEntity livingTarget) {
            if (plugin.getSpecEffects().tryFrostSlow(livingTarget.getUniqueId(), 3000L)) {
                livingTarget.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        (int) def.passiveDouble("slow_duration", 2.0) * 20, 0));
            }
        }
    }

    // Трюкач: додж + партикл + ЗВУК (1.5.0.3)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Spec spec = specOf(victim.getUniqueId());
        if (spec != Spec.TRICKSTER) {
            return;
        }
        SpecRegistry.SpecDef def = defOf(spec);
        if (def == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble()
                < def.passiveDouble("dodge_chance", 0.10)) {
            event.setCancelled(true);
            victim.spawnParticle(Particle.CLOUD,
                    victim.getLocation().add(0.0, 1.0, 0.0),
                    5, 0.3, 0.2, 0.3, 0.0);
            plugin.getFx().procByKey(victim, "💨 Уклонение!", "dodge_trickster");
        }
    }

    // Светоносец: ×1.2 к исходящему лечению + ЗВУК (1.5.0.3)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Spec spec = specOf(player.getUniqueId());
        if (spec != Spec.LIGHTBEARER) {
            return;
        }
        SpecRegistry.SpecDef def = defOf(spec);
        if (def == null) {
            return;
        }
        event.setAmount(event.getAmount() * def.passiveDouble("heal_multiplier", 1.20));
        plugin.getFx().procByKey(player, "✚ Благодать", "grace");
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
