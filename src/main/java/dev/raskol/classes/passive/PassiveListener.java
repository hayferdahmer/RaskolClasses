// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Классовые пассивки (1.3.x + 1.5.0 Пакет 3).
 * FIX 1.5.0.4: без deprecated getMaxHealth() — через Attribute.MAX_HEALTH.
 */
public final class PassiveListener implements Listener {

    private final RaskolClasses plugin;

    public PassiveListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(attacker);
        if (pc == null) {
            return;
        }
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = attacker.getUniqueId();
        double damage = event.getDamage();

        // execute_passive (Воин)
        if (pc == PlayerClass.WARRIOR && cfg.passiveEnabled(pc, "execute_passive")) {
            double chance = cfg.passiveDouble(pc, "execute_passive", "chance", 0.20);
            double threshold = cfg.passiveDouble(pc, "execute_passive", "threshold", 0.20);
            double multiplier = cfg.passiveDouble(pc, "execute_passive", "multiplier", 3.0);
            int cooldown = cfg.passiveInt(pc, "execute_passive", "cooldown-seconds", 6);
            if (healthRatio(target) <= threshold
                    && ThreadLocalRandom.current().nextDouble() < chance
                    && !plugin.getCooldowns().isOnCooldown(uuid, "passive_execute")) {
                damage *= multiplier;
                plugin.getCooldowns().start(uuid, "passive_execute", cooldown * 1000L);
                plugin.getFx().procByKey(attacker, "⚡ Казнь ×3!", "execute_passive");
            }
        }

        // predator (Охотник)
        if (pc == PlayerClass.HUNTER && cfg.passiveEnabled(pc, "predator")) {
            double threshold = cfg.passiveDouble(pc, "predator", "threshold", 0.80);
            double multiplier = cfg.passiveDouble(pc, "predator", "multiplier", 1.20);
            if (healthRatio(attacker) >= threshold) {
                damage *= multiplier;
                plugin.getFx().procByKey(attacker, "🐺 Хищник!", "predator");
            }
        }

        // poisoned_blades (Разбойник)
        if (pc == PlayerClass.ROGUE && cfg.passiveEnabled(pc, "poisoned_blades")) {
            double chance = cfg.passiveDouble(pc, "poisoned_blades", "chance", 0.30);
            int duration = cfg.passiveInt(pc, "poisoned_blades", "duration-seconds", 2);
            int cooldown = cfg.passiveInt(pc, "poisoned_blades", "cooldown-seconds", 3);
            if (ThreadLocalRandom.current().nextDouble() < chance
                    && !plugin.getCooldowns().isOnCooldown(uuid, "passive_poisoned")) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.POISON, duration * 20, 0));
                plugin.getCooldowns().start(uuid, "passive_poisoned", cooldown * 1000L);
                plugin.getFx().procByKey(attacker, "☠ Яд!", "poisoned_blades");
            }
        }

        // sadism (Разбойник)
        if (pc == PlayerClass.ROGUE && cfg.passiveEnabled(pc, "sadism")) {
            double bonus = cfg.passiveDouble(pc, "sadism", "bonus", 3.0);
            int cooldown = cfg.passiveInt(pc, "sadism", "cooldown-seconds", 2);
            if (isBackstab(attacker, target)
                    && !plugin.getCooldowns().isOnCooldown(uuid, "passive_sadism")) {
                damage += bonus;
                plugin.getCooldowns().start(uuid, "passive_sadism", cooldown * 1000L);
                plugin.getFx().procByKey(attacker, "🗡 В спину +3!", "sadism");
            }
        }

        event.setDamage(damage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(victim);
        if (pc == null) {
            return;
        }
        RaskolConfig cfg = plugin.getRaskolConfig();

        // mana_soaked (Маг): −15% входящего урона при мане ≥ 50
        if (pc == PlayerClass.MAGE && cfg.passiveEnabled(pc, "mana_soaked")) {
            double threshold = cfg.passiveDouble(pc, "mana_soaked", "threshold", 50.0);
            double reduction = cfg.passiveDouble(pc, "mana_soaked", "reduction", 0.15);
            double mana = plugin.getResources().getValue(victim.getUniqueId());
            if (mana >= threshold) {
                event.setDamage(event.getDamage() * (1.0 - reduction));
                plugin.getFx().procByKey(victim, "💠 Пропитан маной!", "mana_soaked");
            }
        }
    }

    /** Отношение HP/max HP; без deprecated getMaxHealth(). */
    private double healthRatio(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        return max > 0 ? entity.getHealth() / max : 0.0;
    }

    /** Атака со спины: угол между направлением цели и вектором к атакующему < 60°. */
    private boolean isBackstab(Player attacker, LivingEntity target) {
        Vector targetDir = target.getLocation().getDirection().setY(0).normalize();
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(target.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() < 1e-6) {
            return false;
        }
        toAttacker.normalize();
        return targetDir.dot(toAttacker) > 0.5;
    }
}
