// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class MageAbilities {

    private final RaskolClasses plugin;

    public MageAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public boolean firebolt(Player player, AbilityDef def) {
        SmallFireball fireball = player.launchProjectile(SmallFireball.class);
        fireball.setYield(0f);
        fireball.setIsIncendiary(false);
        fireball.setVelocity(fireball.getVelocity().multiply(1.5));
        return true;
    }

    public boolean blink(Player player, AbilityDef def) {
        RayTraceResult hit = player.rayTraceBlocks(8);
        Location destination = player.getLocation().clone();
        if (hit == null || hit.getHitBlock() == null) {
            destination.add(player.getLocation().getDirection().multiply(8));
        } else {
            Block above = hit.getHitBlock().getRelative(BlockFace.UP);
            destination.set(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        }

        if (!isSafe(destination)) {
            // Пакет 3: сообщение из messages.blink-unsafe
            String text = plugin.getRaskolConfig().message("blink-unsafe",
                    "Скачок невозможен: нет безопасной точки");
            player.sendMessage(Component.text(text, NamedTextColor.RED));
            return false;
        }
        player.teleport(destination);
        return true;
    }

    public boolean frostNova(Player player, AbilityDef def) {
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.MAGE, "frost_nova", 4) * 20;
        boolean affected = false;
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1));
            living.damage(3.0, player);
            affected = true;
        }
        return affected;
    }

    public boolean arcaneBurst(Player player, AbilityDef def) {
        boolean affected = false;
        for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.damage(8.0, player);
            living.setVelocity(living.getVelocity().add(new Vector(0.0, 0.6, 0.0)));
            affected = true;
        }
        return affected;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int y = location.getBlockY();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        Block feet = world.getBlockAt(location);
        Block head = world.getBlockAt(location.clone().add(0, 1, 0));
        Block ground = world.getBlockAt(location.clone().subtract(0, 1, 0));
        return feet.isPassable() && head.isPassable()
                && !ground.isPassable()
                && ground.getType() != Material.LAVA;
    }
}
