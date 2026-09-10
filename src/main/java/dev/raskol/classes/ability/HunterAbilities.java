// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 1.7.2: КИТ ОХОТНИКА (средневековье других вселенных: Ведьмак/Skyrim-вайб).
 * Все способности масштабируются от Силы оружия (WP): base + WP × coeff.
 *
 * Кит (слоты 1–5):
 *  1. «Метка Волка» (wolf_mark)    — рей 20: физ base+WP×0.5 + Slowness I 3с + Glowing 6с;
 *  2. «Ласточка» (swallow)         — Скорость II + Регенерация I на 8с (ведьмак-зелье);
 *  3. «Пронзающий выстрел» (piercing_shot) — рей 20: одиночный физ base+WP×0.9;
 *  4. «Веер стрел» (arrow_fan)     — 3 стрелы конусом, урон каждой base+WP×0.35;
 *  5. «Дождь стрел» (arrow_rain)   — AoE радиус 5 (LOS): физ base+WP×0.5 каждому врагу.
 * Стрелы несут урон через setDamage (ванильный снаряд + наш урон), шутер = игрок,
 * поэтому path-A офенс (WP×basic-coeff) применяется к ним автоматически.
 */
public final class HunterAbilities {

    private static final PlayerClass PC = PlayerClass.HUNTER;

    private final RaskolClasses plugin;

    public HunterAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.HUNTER.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.HUNTER.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.HUNTER.abilities." + def.id() + ".power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.HUNTER.abilities." + def.id() + ".duration", defv);
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

    /** 1. «Метка Волка» — метка-удар: урон + slow + glowing. */
    public boolean wolfMark(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 8.0, 0.5);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, 0));
        t.setGlowing(true);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> t.setGlowing(false), 6 * 20L);
        return true;
    }

    /** 2. «Ласточка» — мобильность + реген (ведьмак-зелье). */
    public boolean swallow(Player p, AbilityDef def) {
        int secs = duration(def, 8);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, secs * 20, 0));
        return true;
    }

    /** 3. «Пронзающий выстрел» — тяжёлый одиночный выстрел. */
    public boolean piercingShot(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 12.0, 0.9);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        return true;
    }

    /** 4. «Веер стрел» — 3 стрелы конусом, урон каждой через setDamage. */
    public boolean arrowFan(Player p, AbilityDef def) {
        double dmgEach = dmg(p, def, 6.0, 0.35);
        Vector dir = p.getLocation().getDirection().normalize();
        for (int i = -1; i <= 1; i++) {
            Vector v = dir.clone().rotateAroundY(i * 0.22);
            AbstractArrow arrow = p.launchProjectile(AbstractArrow.class, v.multiply(2.2));
            arrow.setDamage(dmgEach);
            arrow.setShooter(p);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        return true;
    }

    /** 5. «Дождь стрел» — AoE по врагам в радиусе (с LOS-проверкой 1.6.11). */
    public boolean arrowRain(Player p, AbilityDef def) {
        double radius = cfgD("classes.HUNTER.abilities." + def.id() + ".radius", 5.0);
        double dmg = dmg(p, def, 10.0, 0.5);
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
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            hit = true;
        }
        return hit;
    }
}
