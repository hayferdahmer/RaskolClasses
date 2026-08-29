// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PassiveListener implements Listener {

    private final RaskolClasses plugin;
    private final Map<UUID, Map<String, Long>> lastProc = new ConcurrentHashMap<>();

    public PassiveListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            applyAttackerPassives(attacker, event);
        }
        if (event.getEntity() instanceof Player victim) {
            applyVictimPassives(victim, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        RaskolConfig config = plugin.getRaskolConfig();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc != PlayerClass.PRIEST || !config.passiveEnabled(pc, "grace")) {
            return;
        }
        event.setAmount(event.getAmount() * config.passiveDouble(pc, "grace", "multiplier", 1.15));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastProc.remove(event.getPlayer().getUniqueId());
    }

    private void applyAttackerPassives(Player attacker, EntityDamageByEntityEvent event) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(attacker);
        if (pc == null) {
            return;
        }
        RaskolConfig config = plugin.getRaskolConfig();
        UUID id = attacker.getUniqueId();
        boolean melee = event.getDamager() instanceof Player;

        switch (pc) {
            case WARRIOR -> {
                if (config.passiveEnabled(pc, "execute_passive")
                        && event.getEntity() instanceof LivingEntity target
                        && target.getHealth() <= config.passiveDouble(pc, "execute_passive", "threshold", 0.20)
                            * maxHealth(target)
                        && ThreadLocalRandom.current().nextDouble()
                            < config.passiveDouble(pc, "execute_passive", "chance", 0.20)
                        && tryProc(id, "execute_passive",
                            config.passiveInt(pc, "execute_passive", "cooldown-seconds", 6) * 1000L)) {
                    event.setDamage(event.getDamage()
                            * config.passiveDouble(pc, "execute_passive", "multiplier", 3.0));
                    tag(attacker, "tag.execute", "Казнь ×3!");
                }
            }
            case HUNTER -> {
                if (config.passiveEnabled(pc, "predator")
                        && event.getEntity() instanceof LivingEntity target
                        && target.getHealth() >= config.passiveDouble(pc, "predator", "threshold", 0.80)
                            * maxHealth(target)) {
                    event.setDamage(event.getDamage()
                            * config.passiveDouble(pc, "predator", "multiplier", 1.20));
                    tag(attacker, "tag.predator", "Хищник!");
                }
            }
            case ROGUE -> {
                if (melee && config.passiveEnabled(pc, "poisoned_blades")
                        && event.getEntity() instanceof LivingEntity target
                        && ThreadLocalRandom.current().nextDouble()
                            < config.passiveDouble(pc, "poisoned_blades", "chance", 0.30)
                        && tryProc(id, "poisoned_blades",
                            config.passiveInt(pc, "poisoned_blades", "cooldown-seconds", 3) * 1000L)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                            config.passiveInt(pc, "poisoned_blades", "duration-seconds", 2) * 20, 0));
                    tag(attacker, "tag.poison", "Яд!");
                }
                if (melee && config.passiveEnabled(pc, "sadism")
                        && event.getEntity() instanceof LivingEntity
                        && isBehind(attacker, event.getEntity())
                        && tryProc(id, "sadism",
                            config.passiveInt(pc, "sadism", "cooldown-seconds", 2) * 1000L)) {
                    event.setDamage(event.getDamage() + config.passiveDouble(pc, "sadism", "bonus", 3.0));
                    tag(attacker, "tag.backstab", "В спину +3!");
                }
            }
            default -> { }
        }
    }

    private void applyVictimPassives(Player victim, EntityDamageByEntityEvent event) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(victim);
        if (pc != PlayerClass.MAGE) {
            return;
        }
        RaskolConfig config = plugin.getRaskolConfig();
        if (!config.passiveEnabled(pc, "mana_soaked")) {
            return;
        }
        if (plugin.getResources().getValue(victim.getUniqueId())
                >= config.passiveDouble(pc, "mana_soaked", "threshold", 50.0)) {
            event.setDamage(event.getDamage()
                    * (1.0 - config.passiveDouble(pc, "mana_soaked", "reduction", 0.15)));
        }
    }

    private boolean tryProc(UUID playerId, String passiveId, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Map<String, Long> byPassive = lastProc.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Long previous = byPassive.get(passiveId);
        if (previous != null && now - previous < cooldownMillis) {
            return false;
        }
        byPassive.put(passiveId, now);
        return true;
    }

    private boolean isBehind(Player attacker, Entity victim) {
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).normalize();
        Vector victimDir = victim.getLocation().getDirection().normalize();
        return victimDir.dot(toAttacker) < -0.707;
    }

    /** Пакет 3: тег из messages.<key> с фолбэком на встроенный текст. */
    private void tag(Player player, String key, String fallback) {
        player.sendActionBar(Component.text(
                plugin.getRaskolConfig().message(key, fallback),
                NamedTextColor.YELLOW));
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }
}
