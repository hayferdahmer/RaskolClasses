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
 * Пассивки всех 18 активных спек (1.4.0 → 1.14.0).
 * 1.11.4 (P4e): единый допуск единиц SpecMath.asFraction; ключи по specs.yml/лору.
 * 1.14.0 (Б2b): legacy-алиасы в современных спеках (ASSASSIN/OUTLAW/HOLY),
 *         новые пассивки ARMS/FIRE/BEAST_MASTER/SUBTLETY/AFFLICTION/DESTRUCTION;
 *         FIX: горение от FIRE-пассивки через DotService.applyById(owner, target, id)
 *         (прежде был перепутан порядок аргументов apply);
 *         FIX: hasAllyNearby возвращает true только для союзников (canHit=false).
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

        // Берсерк: +damage_pct% урона, пока HP ≥ threshold
        if (spec == Spec.BERSERKER) {
            if (hpFraction(attacker) >= def.passiveDouble("threshold", 0.60)) {
                damage *= 1.0 + SpecMath.asFraction(def.passiveDouble("damage_pct", 15.0));
            }
        }

        // Стрелок: crit_bonus% шанса крита стрелами ×1.5
        if (spec == Spec.MARKSMAN && isArrow) {
            if (ThreadLocalRandom.current().nextDouble()
                    < SpecMath.asFraction(def.passiveDouble("crit_bonus", 10.0))) {
                damage *= 1.5;
                event.getEntity().getWorld().spawnParticle(Particle.CRIT,
                        event.getEntity().getLocation().add(0.0, 1.0, 0.0),
                        6, 0.3, 0.3, 0.3, 0.0);
            }
        }

        // Ликвидация (бывш. LIQUIDATOR): crit_chance% ×crit_mult с тыла
        if (spec == Spec.ASSASSIN) {
            if (isBack(attacker, event.getEntity())
                    && ThreadLocalRandom.current().nextDouble()
                    < SpecMath.asFraction(def.passiveDouble("crit_chance", 15.0))) {
                damage *= def.passiveDouble("crit_mult", 1.5);
                event.getEntity().getWorld().spawnParticle(Particle.CRIT,
                        event.getEntity().getLocation().add(0.0, 1.0, 0.0),
                        6, 0.3, 0.3, 0.3, 0.0);
                plugin.getFx().procByKey(attacker, "⚡ Крит ×1.5!", "crit_liquidator");
            }
        }

        // Оружие: execute-бонус ×1.5 по цели ≤30% HP
        if (spec == Spec.ARMS && event.getEntity() instanceof LivingEntity target) {
            if (targetHpFraction(target) <= 0.30) {
                damage *= 1.5;
                plugin.getFx().procByKey(attacker, "⚔ Казнь ×1.5!", "execute_passive");
            }
        }

        // Огонь: крит поджигает цель (burning через DotService.applyById)
        if (spec == Spec.FIRE && event.getEntity() instanceof LivingEntity target) {
            if (ThreadLocalRandom.current().nextDouble()
                    < SpecMath.asFraction(def.passiveDouble("crit_chance", 15.0))) {
                damage *= 1.5;
                // FIX 1.14.0: корректная сигнатура applyById(owner, target, dotId)
                plugin.getCombat().dots().applyById(attacker, target, "burning");
                event.getEntity().getWorld().spawnParticle(Particle.FLAME,
                        event.getEntity().getLocation().add(0.0, 1.0, 0.0),
                        8, 0.3, 0.3, 0.3, 0.0);
                plugin.getFx().procByKey(attacker, "🔥 Крит + горение!", "predator");
            }
        }

        // Повелитель зверей: +pet_bonus% урона, пока рядом НЕТ союзников
        if (spec == Spec.BEAST_MASTER) {
            double bonus = SpecMath.asFraction(def.passiveDouble("pet_bonus", 15.0));
            if (!hasAllyNearby(attacker, 10.0)) {
                damage *= 1.0 + bonus;
            }
        }

        // Скрытность: +stealth_bonus% урона из невидимости
        if (spec == Spec.SUBTLETY && attacker.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            damage *= 1.0 + SpecMath.asFraction(def.passiveDouble("stealth_bonus", 25.0));
            plugin.getFx().procByKey(attacker, "☠ Удар из тени!", "sadism");
        }

        event.setDamage(damage);

        // Тенеплёт: lifesteal_pct% от дошедшего урона (через HpBarService, анти-хил уважается)
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

    // Головорез (бывш. TRICKSTER): dodge_chance% уклонения
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Spec spec = specOf(victim.getUniqueId());
        if (spec != Spec.OUTLAW) {
            return;
        }
        SpecRegistry.SpecDef def = defOf(spec);
        if (def == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble()
                < SpecMath.asFraction(def.passiveDouble("dodge_chance", 15.0))) {
            event.setCancelled(true);
            victim.spawnParticle(Particle.CLOUD,
                    victim.getLocation().add(0.0, 1.0, 0.0),
                    5, 0.3, 0.2, 0.3, 0.0);
            plugin.getFx().procByKey(victim, "💨 Уклонение!", "dodge_trickster");
        }
    }

    // Свет (бывш. LIGHTBEARER): ×heal_multiplier к входящему лечению
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Spec spec = specOf(player.getUniqueId());
        if (spec != Spec.HOLY) {
            return;
        }
        SpecRegistry.SpecDef def = defOf(spec);
        if (def == null) {
            return;
        }
        event.setAmount(event.getAmount() * def.passiveDouble("heal_multiplier", 1.20));
    }

    /* -------------------------------- хелперы -------------------------------- */

    private double hpFraction(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        double max = inst != null ? inst.getValue() : 20.0;
        return max > 0 ? p.getHealth() / max : 1.0;
    }

    private double targetHpFraction(LivingEntity target) {
        AttributeInstance inst = target.getAttribute(Attribute.MAX_HEALTH);
        double max = inst != null ? inst.getValue() : 20.0;
        return max > 0 ? target.getHealth() / max : 1.0;
    }

    private boolean isBack(Player attacker, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        double dx = living.getLocation().getX() - attacker.getLocation().getX();
        double dz = living.getLocation().getZ() - attacker.getLocation().getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        double targetYaw = living.getLocation().getYaw();
        double diff = Math.abs((angle - targetYaw + 180) % 360 - 180);
        return diff < 90.0;
    }

    /**
     * FIX 1.14.0: союзник = игрок, по которому canHit=false (фракция/фф-выкл).
     * Ранее логика была инвертирована и бонус срабатывал наоборот.
     */
    private boolean hasAllyNearby(Player player, double radius) {
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player other && !other.getUniqueId().equals(player.getUniqueId())) {
                if (!plugin.getCombat().canHit(player, other)) {
                    return true;
                }
            }
        }
        return false;
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
