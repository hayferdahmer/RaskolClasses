// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.effect.EffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

public final class RogueAbilities {

    private final RaskolClasses plugin;

    public RogueAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public boolean stealth(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "stealth", 15) * 1000L;
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.STEALTH, durationMillis);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                (int) (durationMillis / 50L), 0));
        return true;
    }

    public boolean fanOfKnives(Player player, AbilityDef def) {
        boolean affected = false;
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.damage(4.0, player);
            affected = true;
        }
        return affected;
    }

    public boolean cheapShot(Player player, AbilityDef def) {
        RayTraceResult hit = player.rayTraceEntities(4);
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            // Пакет 3: сообщение из messages.cheap-shot-no-target
            String text = plugin.getRaskolConfig().message("cheap-shot-no-target",
                    "Нет цели в радиусе 4 блоков");
            player.sendMessage(Component.text(text, NamedTextColor.RED));
            return false;
        }
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "cheap_shot", 2) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
        target.damage(3.0, player);
        return true;
    }

    public boolean evasion(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "evasion", 4) * 1000L;
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.EVASION, durationMillis);
        return true;
    }
}
