// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 * Пассивки всех 12 спеков (1.4.0 → 1.11.4).
 * 1.11.4 (P4e): ЕДИНЫЙ допуск единиц SpecMath.asFraction (F1/F2: 15% → 0.15);
 *  ключи и семантика приведены к specs.yml/лору (F4 berserker по HP, F5 arcane
 *  перенесён в ResourceService как +mana_regen/с, F6 marksman = крит стрелами);
 *  F3: лифстил тенеплёта через HpBarService.heal (уважает анти-хил Раскола Души);
 *  F8: мёртвые ветки precise/rageBurst удалены; F9: двойной тег благодати убран.
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

        // Берсерк: +damage_pct% урона, пока HP ≥ threshold (F4: по HP, не по ярости)
        if (spec == Spec.BERSERKER) {
            if (hpFraction(attacker) >= def.passiveDouble("threshold", 0.60)) {
                damage *= 1.0 + SpecMath.asFraction(def.passiveDouble("damage_pct", 15.0));
            }
        }

        // Стрелок: crit_bonus% шанса крита стрелами ×1.5 (F6: по лору, не дистанция)
        if (spec == Spec.MARKSMAN && isArrow) {
            if (ThreadLocalRandom.current().nextDouble()
                    < SpecMath.asFraction(def.passiveDouble("crit_bonus", 10.0))) {
                damage *= 1.5;
                event.getEntity().getWorld().spawnParticle(Particle.CRIT,
                        event.getEntity().getLocation().add(0.0, 1.0, 0.0),
                        6, 0.3, 0.3, 0.3, 0.0);
            }
        }

        // Ликвидатор: crit_chance% шанса крита ×crit_mult (F1: asFraction)
        if (spec == Spec.LIQUIDATOR) {
            if (ThreadLocalRandom.current().nextDouble()
                    < SpecMath.asFraction(def.passiveDouble("crit_chance", 10.0))) {
                damage *= def.passiveDouble("crit_mult", 1.5);
                event.getEntity().getWorld().spawnParticle(Particle.CRIT,
                        event.getEntity().getLocation().add(0.0, 1.0, 0.0),
                        6, 0.3, 0.3, 0.3, 0.0);
                plugin.getFx().procByKey(attacker, "⚡ Крит ×1.5!", "crit_liquidator");
            }
        }

        event.setDamage(damage);

        // Тенеплёт: lifesteal_pct% от дошедшего урона (F3: через HpBarService, анти-хил уважается)
        if (spec == Spec.SHADOWWEAVER) {
            double healFormula = damage * SpecMath.asFraction(def.passiveDouble("lifesteal_pct", 15.0));
            if (healFormula > 0.0) {
                plugin.getHpBarService().heal(attacker, healFormula);
                attacker.spawnParticle(Particle.HEART,
                        attacker.getLocation().add(0.0, 1.2, 0.0),
                        2, 0.2, 0.2, 0.2, 0.0);
                plugin.getFx().procByKey(attacker, "♥ Лифстил", "lifesteal_shadowweaver");
            }
        }

        // Мороз: замедление цели с внутренним КД 3 с
        if (spec == Spec.FROST && event.getEntity() instanceof LivingEntity livingTarget) {
            if (plugin.getSpecEffects().tryFrostSlow(livingTarget.getUniqueId(), 3000L)) {
                livingTarget.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        (int) def.passiveDouble("slow_duration", 2.0) * 20, 0));
            }
        }
    }

    // Трюкач: dodge_chance% уклонения (F2: asFraction)
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
                < SpecMath.asFraction(def.passiveDouble("dodge_chance", 10.0))) {
            event.setCancelled(true);
            victim.spawnParticle(Particle.CLOUD,
                    victim.getLocation().add(0.0, 1.0, 0.0),
                    5, 0.3, 0.2, 0.3, 0.0);
            plugin.getFx().procByKey(victim, "💨 Уклонение!", "dodge_trickster");
        }
    }

    // Светоносец: ×heal_multiplier к входящему лечению (F9: без дубля тега)
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
    }

    private double hpFraction(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        double max = inst != null ? inst.getValue() : 20.0;
        return max > 0 ? p.getHealth() / max : 1.0;
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
