// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import dev.raskol.classes.hook.TownyHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 1.7.3: КИТ МАГА (греческая мифология). Урон/гранты = base + SP×coeff.
 * 1.8.1 (S3): однотargetные урон-абилки проверяют canHit ДО урона и побочных
 * эффектов (поджог/execute-тег) — каст по союзнику отклоняется чисто.
 * 1.8.1 (S4): «Шаг Гермеса» не блинкует в чужой клейм Towny
 * (wilderness и свой город/резидентство — можно; чужой город — нет; fail closed
 * при сломанной рефлексии TownyHook).
 * 1.8.1 (нейминг): execute-тег мага — собственный ключ tag.execute-mage
 * («Кара Зевса ×3!»), чтобы не пересекаться с талантом воина «Казнь».
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
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    private void allyTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Огонь Прометея» — гибрид 30/70 + поджог 3 с. 1.8.1: гейт союзника ДО поджога. */
    public boolean firePrometheus(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 15.0, 1.2);
        plugin.getCombat().dealDamage(t, p, DamageProfile.hybrid(dmg * 0.3, dmg * 0.7));
        t.setFireTicks(3 * 20);
        return true;
    }

    /** 2. «Шаг Гермеса» — телепорт 8 блоков. 1.8.1: гейт клеймов Towny (S4). */
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
        // 1.8.1 (S4): нельзя блинковаться в чужой клейм в обход ворот/осад
        if (!TownyHook.canBlink(plugin, p, loc, target)) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "blink-claim", "Скачок невозможен: чужие владения"),
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

    /** 3. «Дыхание Борея» — AoE маг + Slowness II (LOS + фракционный фильтр). */
    public boolean boreasBreath(Player p, AbilityDef def) {
        double radius = cfgD("classes.MAGE.abilities." + def.id() + ".radius", 5.0);
        int secs = duration(def, 4);
        double dmg = dmg(p, def, 12.0, 1.0);
        boolean hit = false;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || e.equals(p)) {
                continue;
            }
            if (!Targeting.isValidDamageTarget(plugin, p, t)) {
                continue;
            }
            if (!Targeting.hasLineOfSight(plugin, p, t)) {
                continue;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
            t.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, secs * 20, 1));
            hit = true;
        }
        return hit;
    }

    /** 4. «Эгида Афины» — грант МАГ-резиста (self, гейт не нужен). */
    public boolean athenaAegis(Player p, AbilityDef def) {
        double grant = base(def, 15.0)
                + plugin.getCombat().powers().spellPower(p.getUniqueId()) * coeff(def, 0.05);
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(p.getUniqueId(), def.id(), 0.0, grant, secs * 1000L);
        return true;
    }

    /** 5. «Гнев Зевса» — execute-финишер. 1.8.1: гейт союзника; тег — tag.execute-mage. */
    public boolean zeusWrath(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        double threshold = cfgD("classes.MAGE.abilities." + def.id() + ".threshold", 0.25);
        double max = maxOf(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 25.0, 2.0);
        if (frac < threshold) {
            dmg *= cfgD("classes.MAGE.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute-mage", "Кара Зевса ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        }
        return true;
    }

    private double maxOf(LivingEntity e) {
        org.bukkit.attribute.AttributeInstance attr =
                e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
