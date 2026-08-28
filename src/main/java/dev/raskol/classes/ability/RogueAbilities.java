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

/**
 * Активные способности разбойника (ресурс — энергия).
 * D2: хардкод STEALTH_DURATION_MILLIS удалён — длительность в config.
 */
public final class RogueAbilities {

    private final RaskolClasses plugin;

    public RogueAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * Скрытность: невидимость (duration, дефолт 15 с — аудит D1, было 60)
     * до первой атаки или урона; первый удар ×1.5. Флаг и INVISIBILITY
     * получают одинаковую длительность (D2).
     */
    public boolean stealth(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "stealth", 15) * 1000L;
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.STEALTH, durationMillis);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                (int) (durationMillis / 50L), 0));
        return true;
    }

    /** Веер ножей: 4 урона всем живым в радиусе 3 (мгновенная). */
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

    /** Подлый удар: цель в фокусе (4 блока) — слепота и замедление (duration, 2 с) + 3 урона. */
    public boolean cheapShot(Player player, AbilityDef def) {
        RayTraceResult hit = player.rayTraceEntities(4);
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            player.sendMessage(Component.text("Нет цели в радиусе 4 блоков", NamedTextColor.RED));
            return false; // каст отменяется с возвратом ресурса и кулдауна
        }
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "cheap_shot", 2) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
        target.damage(3.0, player);
        return true;
    }

    /** Уклонение: полная отмена входящего урона (duration, дефолт 4 с). */
    public boolean evasion(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "evasion", 4) * 1000L;
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.EVASION, durationMillis);
        return true;
    }
}