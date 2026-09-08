// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
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
 * 1.6.0 пакет 2: fan_of_knives/cheap_shot = ФИЗИЧЕСКИЙ урон через dealDamage.
 * 1.6.2: союзники не бьются (гейт combat.friendly-fire).
 * 1.6.6: грант Уклонения читается из конфига resist.grants.evasion.physical.
 */
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

    /** Веер ножей — ФИЗИЧЕСКИЙ урон (4 дефолт). Союзники пропускаются. */
    public boolean fanOfKnives(Player player, AbilityDef def) {
        double phys = plugin.getRaskolConfig()
                .abilityDamagePhysical(PlayerClass.ROGUE, def.id(), 4.0);
        boolean affected = false;
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            if (!Targeting.isValidDamageTarget(plugin, player, living)) {
                continue;
            }
            plugin.getCombat().dealDamage(living, player, DamageProfile.physical(phys));
            affected = true;
        }
        return affected;
    }

    /** Подлый удар — ФИЗИЧЕСКИЙ урон (3 дефолт) + Blind + Slowness. Не по союзникам. */
    public boolean cheapShot(Player player, AbilityDef def) {
        RayTraceResult hit = player.rayTraceEntities(4);
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            String text = plugin.getRaskolConfig().message("cheap-shot-no-target",
                    "Нет цели в радиусе 4 блоков");
            player.sendMessage(Component.text(text, NamedTextColor.RED));
            return false;
        }
        if (!Targeting.isValidDamageTarget(plugin, player, target)) {
            player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
            return false;
        }
        double phys = plugin.getRaskolConfig()
                .abilityDamagePhysical(PlayerClass.ROGUE, def.id(), 3.0);
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "cheap_shot", 2) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
        plugin.getCombat().dealDamage(target, player, DamageProfile.physical(phys));
        return true;
    }

    /** Уклонение: +физрезист (конфиг) на duration, дефолт 4 с. */
    public boolean evasion(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.ROGUE, "evasion", 4) * 1000L;
        double phys = plugin.getConfig()
                .getDouble("resist.grants.evasion.physical", 30.0);
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.EVASION, durationMillis);
        plugin.getResists().addTimedModifier(player.getUniqueId(), "evasion",
                phys, 0.0, durationMillis);
        return true;
    }
}
