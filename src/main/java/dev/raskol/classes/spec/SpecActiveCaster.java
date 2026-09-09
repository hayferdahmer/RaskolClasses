// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import dev.raskol.classes.compat.AuthGate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Каст активок спеков со слота 6 (/rc 6).
 * 1.6.0 пакет 2: урон через CombatService.dealDamage с DamageProfile
 * (SHADOWWEAVER/FROST = маг, LIQUIDATOR = физ).
 * 1.6.11: AoE спеки FROST не бьёт сквозь стены (Targeting.hasLineOfSight).
 */
public final class SpecActiveCaster {

    private final RaskolClasses plugin;

    public SpecActiveCaster(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public void tryCast(Player player, Spec spec) {
        // 1.5.8: auth + creative гейт; 1.5.9: текст из конфига
        if (!AuthGate.canAct(plugin, player)) {
            player.sendMessage(Component.text(plugin.getRaskolConfig().message("gate.blocked",
                    "Способности недоступны в этом режиме или до входа в аккаунт."),
                    NamedTextColor.RED));
            return;
        }
        SpecRegistry.SpecDef def = plugin.getSpecRegistry().get(spec);
        if (def == null) {
            return;
        }
        var uuid = player.getUniqueId();
        String cdId = "spec_" + spec.id();

        long remaining = plugin.getCooldowns().getRemainingMillis(uuid, cdId);
        if (remaining > 0L) {
            player.sendMessage(Component.text("«" + def.activeId() + "»: перезарядка ещё "
                    + (remaining / 1000L + 1L) + "с", NamedTextColor.GRAY));
            return;
        }
        if (!plugin.getResources().consume(uuid, def.activeCost())) {
            player.sendMessage(Component.text("Не хватает ресурса для «"
                    + def.activeDescription() + "»", NamedTextColor.RED));
            return;
        }

        plugin.getCooldowns().start(uuid, cdId, def.activeCooldown() * 1000L);
        plugin.getFx().onCast(player, def.activeId());
        cast(player, spec, def);
        player.sendMessage(Component.text("«" + def.activeDescription() + "» — активирована",
                NamedTextColor.GREEN));
    }

    private void cast(Player player, Spec spec, SpecRegistry.SpecDef def) {
        switch (spec) {
            case GUARDIAN -> {
                double radius = def.activeDouble("radius", 4.0);
                for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (entity instanceof Mob mob) {
                        mob.setTarget(player);
                    }
                }
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, def.activeInt("duration", 3) * 20, 0));
            }
            case BERSERKER -> plugin.getSpecEffects().startRageBurst(player.getUniqueId(),
                    def.activeInt("duration", 5) * 1000L,
                    def.activeDouble("damage_bonus", 4.0));
            case MARKSMAN -> plugin.getSpecEffects().armPrecise(player.getUniqueId());
            case TRACKER -> {
                Entity target = player.getTargetEntity(6);
                if (target instanceof LivingEntity living) {
                    int duration = def.activeInt("duration", 2) * 20;
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, duration, 5));
                    TrapVisual.show(plugin, living.getLocation(), duration);
                }
            }
            case LIGHTBEARER -> {
                Location origin = player.getLocation().clone();
                double radius = def.activeDouble("radius", 4.0);
                int heal = def.activeInt("heal", 2);
                int ticks = def.activeInt("duration", 5) * 20;
                new BukkitRunnable() {
                    int elapsed = 0;

                    @Override
                    public void run() {
                        elapsed += 10;
                        if (elapsed > ticks) {
                            cancel();
                            return;
                        }
                        if (origin.getWorld() != null) {
                            origin.getWorld().spawnParticle(Particle.HEART,
                                    origin.clone().add(0.0, 0.5, 0.0),
                                    2, radius * 0.5, 0.3, radius * 0.5, 0.0);
                        }
                        for (Entity entity : origin.getNearbyEntities(radius, radius, radius)) {
                            if (entity instanceof Player ally) {
                                ally.heal(heal);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            }
            case SHADOWWEAVER -> {
                double magic = def.activeDouble("damage", 6.0);
                Entity target = player.getTargetEntity(6);
                if (target instanceof LivingEntity living) {
                    plugin.getCombat().dealDamage(living, player, DamageProfile.magic(magic));
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, 30, 0));
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, 30, 1));
                }
            }
            case ARCANE -> {
                plugin.getResources().refund(player.getUniqueId(),
                        def.activeInt("mana_restore", 50));
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.WEAKNESS);
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 60, 0));
            }
            case FROST -> {
                double magic = def.activeDouble("damage", 40.0);
                double radius = def.activeDouble("radius", 3.0);
                int duration = def.activeInt("duration", 3) * 20;
                for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (entity instanceof Mob mob) {
                        // 1.6.11: цели за стенами не получают урон (LOS)
                        if (!Targeting.hasLineOfSight(plugin, player, mob)) {
                            continue;
                        }
                        plugin.getCombat().dealDamage(mob, player, DamageProfile.magic(magic));
                        mob.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, duration, 2));
                    }
                }
                Location c = player.getLocation();
                if (c.getWorld() != null) {
                    c.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            c.clone().add(0.0, 0.3, 0.0),
                            24, radius * 0.6, 0.2, radius * 0.6, 0.0);
                }
            }
            case LIQUIDATOR -> {
                double physPerTick = def.activeDouble("damage_per_tick", 2.0);
                Entity target = player.getTargetEntity(5);
                if (target instanceof LivingEntity living) {
                    int ticks = def.activeInt("duration", 6) * 20;
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            elapsed += 20;
                            if (elapsed > ticks || living.isDead()) {
                                cancel();
                                return;
                            }
                            plugin.getCombat().dealDamage(living, player,
                                    DamageProfile.physical(physPerTick));
                        }
                    }.runTaskTimer(plugin, 20L, 20L);
                }
            }
            case TRICKSTER -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, def.activeInt("duration", 4) * 20, 0));
                for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof Mob mob
                            && player.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                }
            }
        }
    }
}
