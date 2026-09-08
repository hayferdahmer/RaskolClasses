// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.effect.EffectType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Активные способности воина (ресурс — ярость).
 * 1.6.0 пакет 3: Стальная кожа даёт физрезист через ResistService.
 * 1.6.6: значение гранта читается из конфига resist.grants.steel_skin.physical
 * (единый источник с лором Книги класса).
 */
public final class WarriorAbilities {

    private final RaskolClasses plugin;

    public WarriorAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Стальная кожа: +физрезист (конфиг) на duration, дефолт 5 с. */
    public boolean steelSkin(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.WARRIOR, "steel_skin", 5) * 1000L;
        double phys = plugin.getConfig()
                .getDouble("resist.grants.steel_skin.physical", 15.0);
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.SHIELD_WALL, durationMillis);
        plugin.getResists().addTimedModifier(player.getUniqueId(), "steel_skin",
                phys, 0.0, durationMillis);
        return true;
    }

    /** Удар щитом: Slowness II + Blindness в радиусе 4 + таунт мобов. */
    public boolean shieldBash(Player player, AbilityDef def) {
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.WARRIOR, "shield_bash", 3) * 20;
        int affected = 0;
        for (Entity entity : player.getNearbyEntities(4, 4, 4)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1));
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
            if (living instanceof Mob mob) {
                mob.setTarget(player);
            }
            affected++;
        }
        return affected > 0;
    }

    /** Кровавое безумие: 4 с возвращает 20% полученного урона агрессору. */
    public boolean bloodFury(Player player, AbilityDef def) {
        long durationMillis = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.WARRIOR, "blood_fury", 4) * 1000L;
        plugin.getEffects().addTimed(player.getUniqueId(), EffectType.BLOOD_FURY, durationMillis);
        return true;
    }

    /** Бог войны: Сила II + Сопротивление I (duration, дефолт 8 с). */
    public boolean warGod(Player player, AbilityDef def) {
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.WARRIOR, "war_god", 8) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0));
        return true;
    }
}
