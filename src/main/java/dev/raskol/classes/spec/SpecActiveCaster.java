// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Каст активок спеков со слота 6 (/rc 6).
 * Кулдауны — через CooldownManager (id "spec_<id>"), стоимость — через ResourceService.
 */
public final class SpecActiveCaster {

    private final RaskolClasses plugin;

    public SpecActiveCaster(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public void tryCast(Player player, Spec spec) {
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
        cast(player, spec, def);
        player.sendMessage(Component.text("«" + def.activeDescription() + "» — активирована",
                NamedTextColor.GREEN));
    }

    private void cast(Player player, Spec spec, SpecRegistry.SpecDef def) {
        switch (spec) {
            case GUARDIAN -> {
                // Таунт: мобы в радиусе атакуют тебя
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
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, def.activeInt("duration", 2) * 20, 5));
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
                        for (Entity entity : origin.getNearbyEntities(radius, radius, radius)) {
                            if (entity instanceof Player ally) {
                                ally.heal(heal);
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            }
            case SHADOWWEAVER -> {
                Entity target = player.getTargetEntity(6);
                if (target instanceof LivingEntity living) {
                    living.damage(def.activeDouble("damage", 6.0), player);
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, 30, 0));
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, 30, 1));
                }
            }
            case ARCANE -> {
                // +N маны и снять негативные эффекты
                plugin.getResources().refund(player.getUniqueId(),
                        def.activeInt("mana_restore", 50));
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.WEAKNESS);
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 60, 0));
            }
            case FROST -> {
                double radius = def.activeDouble("radius", 3.0);
                int duration = def.activeInt("duration", 3) * 20;
                for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (entity instanceof Mob mob) {
                        mob.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, duration, 2));
                    }
                }
            }
            case LIQUIDATOR -> {
                Entity target = player.getTargetEntity(5);
                if (target instanceof LivingEntity living) {
                    int ticks = def.activeInt("duration", 6) * 20;
                    double perTick = def.activeDouble("damage_per_tick", 2.0);
                    new BukkitRunnable() {
                        int elapsed = 0;

                        @Override
                        public void run() {
                            elapsed += 20;
                            if (elapsed > ticks || living.isDead()) {
                                cancel();
                                return;
                            }
                            living.damage(perTick, player);
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
