// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * 1.7.3: КИТ МАГА (греческая мифология). Всё масштабируется от Силы заклинаний (SP):
 * урон/гранты = base + SP × coeff (конфиг classes.MAGE.abilities.<id>.*).
 *
 * Кит (слоты 1–5):
 *  1. «Огонь Прометея» (fire_prometheus) — гибрид 30/70 (физ/маг) + поджог 3 с;
 *  2. «Шаг Гермеса» (hermes_step)        — телепорт на distance блоков с проверкой безопасности;
 *  3. «Дыхание Борея» (boreas_breath)   — AoE радиус 5 (LOS): маг-урон + Slowness II 4 с;
 *  4. «Эгида Афины» (athena_aegis)      — грант МАГ-резиста (15 + SP×0.05)% на duration;
 *  5. «Гнев Зевса» (zeus_wrath)         — execute: цель <25% HP → ×3 через allowOverCap.
 */
public final class MageAbilities {

    private static final PlayerClass PC = PlayerClass.MAGE;

    private final RaskolClasses plugin;

    public MageAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.MAGE.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.MAGE.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.MAGE.abilities." + def.id() + ".power", "sp");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.MAGE.abilities." + def.id() + ".duration", defv);
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
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.RED));
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Огонь Прометея» — гибрид 30/70 + поджог. */
    public boolean firePrometheus(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 15.0, 0.8);
        plugin.getCombat().dealDamage(t, p, DamageProfile.hybrid(dmg * 0.3, dmg * 0.7));
        t.setFireTicks(3 * 20);
        return true;
    }

    /** 2. «Шаг Гермеса» — телепорт с проверкой безопасности. */
    public boolean hermesStep(Player p, AbilityDef def) {
        double dist = cfgD("classes.MAGE.abilities." + def.id() + ".distance", 8.0);
        Location loc = p.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        Location target = loc.clone().add(dir.multiply(dist));
        if (!isSafe(target)) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "blink-unsafe", "Скачок невозможен: нет безопасной точки"),
                    NamedTextColor.RED));
            return false;
        }
        target.setYaw(loc.getYaw());
        target.setPitch(loc.getPitch());
        p.teleport(target);
        return true;
    }

    private boolean isSafe(Location l) {
        return l.getBlock().isPassable()
                && l.clone().add(0, 1, 0).getBlock().isPassable()
                && l.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }

    /** 3. «Дыхание Борея» — AoE маг-урон + slow (LOS). */
    public boolean boreasBreath(Player p, AbilityDef def) {
        double radius = cfgD("classes.MAGE.abilities." + def.id() + ".radius", 5.0);
        int secs = duration(def, 4);
        double dmg = dmg(p, def, 12.0, 0.6);
        boolean hit = false;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || e.equals(p)) {
                continue;
            }
            if (!dev.raskol.classes.combat.Targeting.isValidDamageTarget(plugin, p, t)) {
                continue;
            }
            if (!dev.raskol.classes.combat.Targeting.hasLineOfSight(plugin, p, t)) {
                continue;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, 1));
            hit = true;
        }
        return hit;
    }

    /** 4. «Эгида Афины» — грант МАГ-резиста, скалируется от SP. */
    public boolean athenaAegis(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double sp = plugin.getCombat().powers().spellPower(uuid);
        double grant = base(def, 15.0) + sp * coeff(def, 0.05);
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(uuid, def.id(), 0.0, grant, secs * 1000L);
        return true;
    }

    /** 5. «Гнев Зевса» — execute-финишер: цель <25% HP → ×3 через allowOverCap. */
    public boolean zeusWrath(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double threshold = cfgD("classes.MAGE.abilities." + def.id() + ".threshold", 0.25);
        AttributeInstance maxAttr = t.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 25.0, 1.4);
        if (frac < threshold) {
            dmg *= cfgD("classes.MAGE.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        }
        return true;
    }
}
