// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

/** Способности жреца (ресурс — свет). Цель — игрок в фокусе взгляда, иначе сам жрец. */
public final class PriestAbilities {

    private final RaskolClasses plugin;

    public PriestAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Малое исцеление: +3 HP цели (мгновенная, без duration). */
    public boolean lesserHeal(Player player, AbilityDef def) {
        return heal(player, findTarget(player), 3.0);
    }

    /** Быстрое исцеление: +6 HP, +1 за каждые 10 уровней скилла healing. */
    public boolean flashHeal(Player player, AbilityDef def) {
        int level = plugin.getSkillLevels().getLevel(player.getUniqueId(), "healing");
        int bonus = level == SkillLevelProvider.NO_SKILL_SYSTEM ? 0 : Math.max(0, level / 10);
        return heal(player, findTarget(player), 6.0 + bonus);
    }

    /** Слово силы: Щит — Поглощение II (duration, дефолт 6 с) цели. */
    public boolean powerWordShield(Player player, AbilityDef def) {
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.PRIEST, "pw_shield", 6) * 20;
        findTarget(player).addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 1));
        return true;
    }

    /** Круг молитвы: всем игрокам в радиусе 5 (и жрецу) +4 HP. */
    public boolean circleOfPrayer(Player player, AbilityDef def) {
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player ally) {
                heal(player, ally, 4.0);
            }
        }
        heal(player, player, 4.0);
        return true;
    }

    /** Кара: враждебным мобам в радиусе 6 — 6 урона, союзникам-игрокам +3 HP. */
    public boolean smite(Player player, AbilityDef def) {
        boolean affected = false;
        for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
            if (entity instanceof Monster monster) {
                monster.damage(6.0, player); // источник урона — жрец
                affected = true;
            } else if (entity instanceof Player ally && !ally.equals(player)) {
                heal(player, ally, 3.0);
                affected = true;
            }
        }
        return affected; // пустая область — отмена каста с возвратом ресурса
    }

    /** Цель: игрок в фокусе взгляда (до 10 блоков), иначе сам жрец. */
    private LivingEntity findTarget(Player player) {
        RayTraceResult hit = player.rayTraceEntities(10);
        if (hit != null && hit.getHitEntity() instanceof Player target) {
            return target;
        }
        return player;
    }

    /** Лечение с капом по максимальному здоровью. true — HP реально выросли. */
    private boolean heal(Player caster, LivingEntity target, double amount) {
        double max = maxHealth(target);
        if (target.getHealth() >= max) {
            return false; // цель здорова — ресурс не тратим впустую
        }
        target.setHealth(Math.min(max, target.getHealth() + amount));
        // Собственные хилы жреца тоже дают свет (ванильное событие не вызывается)
        plugin.getResources().addHealBonus(caster.getUniqueId());
        return true;
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }
}