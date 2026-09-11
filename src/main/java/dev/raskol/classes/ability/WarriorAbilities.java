// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 1.7.2: КИТ ВОИНА (нордика). Урон/хил/гранты = base + WP×coeff.
 * 1.8.1 (S3): однотargetные урон-абилки проверяют canHit ДО траты ресурса/КД —
 * каст по союзнику отклоняется с сообщением, ресурс и КД не тратятся.
 */
public final class WarriorAbilities {

    private static final PlayerClass PC = PlayerClass.WARRIOR;

    private final RaskolClasses plugin;

    public WarriorAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.WARRIOR.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.WARRIOR.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.WARRIOR.abilities." + def.id() + ".power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.WARRIOR.abilities." + def.id() + ".duration", defv);
        return v > 0 ? v : defv;
    }

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        return plugin.getCombat().powers().abilityDamage(
                p.getUniqueId(), power(def), base(def, defBase), coeff(def, defCoeff));
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    private void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    private void allyTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Удар Тира» — одиночный физ-нуку. 1.8.1: гейт союзника. */
    public boolean tyrStrike(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 10.0, 0.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        return true;
    }

    /** 2. «Шкура Бальдра» — грант физ-резиста (self, гейт не нужен). */
    public boolean balderSkin(Player p, AbilityDef def) {
        double grant = base(def, 15.0) + plugin.getCombat().powers().weaponPower(p.getUniqueId()) * coeff(def, 0.05);
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(p.getUniqueId(), def.id(), grant, 0.0, secs * 1000L);
        return true;
    }

    /** 3. «Берсеркерганг» — Сила II + Сопротивление I (self). */
    public boolean berserkergang(Player p, AbilityDef def) {
        int secs = duration(def, 6);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 0));
        return true;
    }

    /** 4. «Кровь Фенрира» — self-хил от WP. */
    public boolean fenrirBlood(Player p, AbilityDef def) {
        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        if (p.getHealth() >= max) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        double amount = dmg(p, def, 15.0, 0.5);
        p.setHealth(Math.min(max, p.getHealth() + amount));
        return true;
    }

    /** 5. «Рагнарёк» — execute-финишер. 1.8.1: гейт союзника ДО execute-логики. */
    public boolean ragnarok(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        double threshold = cfgD("classes.WARRIOR.abilities." + def.id() + ".threshold", 0.25);
        AttributeInstance maxAttr = t.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.8);
        if (frac < threshold) {
            dmg *= cfgD("classes.WARRIOR.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg), true);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        }
        return true;
    }
}
