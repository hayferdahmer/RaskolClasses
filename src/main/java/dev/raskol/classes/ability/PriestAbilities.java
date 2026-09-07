// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.config.RaskolConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Способности жреца (ресурс — свет).
 * 1.6.0 пакет 2: smite = МАГИЧЕСКИЙ урон через dealDamage.
 * 1.6.2: лечение/щиты ложатся только на себя и союзников
 * (гейт combat.friendly-fire=true возвращает старое поведение).
 */
public final class PriestAbilities {

    private final RaskolClasses plugin;

    public PriestAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Малое исцеление: +3 HP цели (мгновенная). */
    public boolean lesserHeal(Player caster, LivingEntity target, AbilityDef def) {
        return heal(caster, target, 3.0);
    }

    /** Быстрое исцеление: +6 HP, +1 за каждые 10 уровней скилла healing. */
    public boolean flashHeal(Player caster, LivingEntity target, AbilityDef def) {
        int level = plugin.getSkillLevels().getLevel(caster.getUniqueId(), "healing");
        int bonus = level == SkillLevelProvider.NO_SKILL_SYSTEM ? 0 : Math.max(0, level / 10);
        return heal(caster, target, 6.0 + bonus);
    }

    /** Слово силы: Щит — Поглощение II цели (duration, дефолт 6 с). */
    public boolean powerWordShield(Player caster, LivingEntity target, AbilityDef def) {
        if (target instanceof Player tp && !tp.equals(caster)
                && !Targeting.canHealPlayer(plugin, caster, tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.RED));
            return false;
        }
        int ticks = plugin.getRaskolConfig()
                .durationSeconds(PlayerClass.PRIEST, "pw_shield", 6) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 1));
        if (target instanceof Player tp && !tp.equals(caster)) {
            RaskolConfig cfg = plugin.getRaskolConfig();
            caster.sendActionBar(Component.text(
                    cfg.message("shield-target", "Щит на: {target}")
                            .replace("{target}", tp.getName()),
                    NamedTextColor.GREEN));
            tp.sendMessage(Component.text(
                    cfg.message("shield-you", "{caster} наложил на тебя щит")
                            .replace("{caster}", caster.getName()),
                    NamedTextColor.GREEN));
        }
        return true;
    }

    /** Круг молитвы: +4 HP себе и союзникам в радиусе 5 (1.6.2: фильтр союзников). */
    public boolean circleOfPrayer(Player player, AbilityDef def) {
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player ally
                    && Targeting.canHealPlayer(plugin, player, ally)) {
                heal(player, ally, 4.0);
            }
        }
        heal(player, player, 4.0);
        return true;
    }

    /** Кара: мобам — МАГИЧЕСКИЙ урон; союзникам-игрокам +3 HP (1.6.2: фильтр). */
    public boolean smite(Player player, AbilityDef def) {
        double magic = plugin.getRaskolConfig()
                .abilityDamageMagic(PlayerClass.PRIEST, def.id(), 6.0);
        boolean affected = false;
        for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
            if (entity instanceof Monster monster) {
                plugin.getCombat().dealDamage(monster, player, DamageProfile.magic(magic));
                affected = true;
            } else if (entity instanceof Player ally && !ally.equals(player)
                    && Targeting.canHealPlayer(plugin, player, ally)) {
                heal(player, ally, 3.0);
                affected = true;
            }
        }
        return affected;
    }

    /** Лечение с капом по максимуму, Благодатью ×1.15 и проверкой союзности (1.6.2). */
    private boolean heal(Player caster, LivingEntity target, double amount) {
        if (target instanceof Player tp && !tp.equals(caster)
                && !Targeting.canHealPlayer(plugin, caster, tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.RED));
            return false; // ресурс не тратим
        }
        double max = maxHealth(target);
        if (target.getHealth() >= max) {
            if (target instanceof Player tp && !tp.equals(caster)) {
                caster.sendActionBar(Component.text(
                        plugin.getRaskolConfig().message("target-full-hp", "Цель здорова"),
                        NamedTextColor.YELLOW));
            }
            return false; // ресурс не тратим впустую
        }
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (cfg.passiveEnabled(PlayerClass.PRIEST, "grace")) {
            amount *= cfg.passiveDouble(PlayerClass.PRIEST, "grace", "multiplier", 1.15);
        }
        double before = target.getHealth();
        target.setHealth(Math.min(max, before + amount));
        double applied = target.getHealth() - before;
        plugin.getResources().addHealBonus(caster.getUniqueId());

        String amt = String.valueOf((int) Math.round(applied));
        if (target instanceof Player tp && !tp.equals(caster)) {
            caster.sendActionBar(Component.text(
                    cfg.message("healed-target", "✚ {target}: +{amount} HP")
                            .replace("{target}", tp.getName())
                            .replace("{amount}", amt),
                    NamedTextColor.GREEN));
            tp.sendMessage(Component.text(
                    cfg.message("healed-you", "{caster} исцелил тебя")
                            .replace("{caster}", caster.getName()),
                    NamedTextColor.GREEN));
        } else {
            caster.sendActionBar(Component.text("✚ +" + amt + " HP", NamedTextColor.GREEN));
        }
        return true;
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }
}
