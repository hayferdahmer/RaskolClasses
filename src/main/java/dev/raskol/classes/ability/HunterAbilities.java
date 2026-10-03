// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.UUID;

/**
 * 1.7.2: КИТ ОХОТНИКА. 1.7.6.3: стрелы веера неподбираемы.
 * 1.8.1: canHit-гейты. 1.9.0: талантовые хуки.
 * 1.12.3: школа PHYSICAL из cast-контекста; VFX из vfx.<id>.* с дефолтами.
 * 1.14.0 (Б3): хуки читаются из Spec2Service.
 */
public final class HunterAbilities {

    private static final PlayerClass PC = PlayerClass.HUNTER;

    private final RaskolClasses plugin;
    private final NamespacedKey fanArrowKey;

    public HunterAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
        this.fanArrowKey = new NamespacedKey(plugin, "fan_arrow");
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private String cfgS(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null ? v : def;
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

    /** 1.14.0 (Б3): хуки Spec2Service. */
    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c);
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

    private void castFx(Player p, String id, String soundDef, String particleDef,
                        float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(cfgS("vfx." + id + ".cast-sound", soundDef));
        Location loc = p.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(cfgS("vfx." + id + ".cast-particle", particleDef));
        if (particle != null) {
            p.getWorld().spawnParticle(particle, loc, count, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private void impactFx(LivingEntity target, String id,
                          String soundDef, String particleDef,
                          float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(cfgS("vfx." + id + ".impact-sound", soundDef));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(cfgS("vfx." + id + ".impact-particle", particleDef));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, count, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private Particle resolveParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean wolfMark(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "wolf_mark", "ENTITY_WOLF_GROWL", "CRIT", 0.6f, 0.9f, 10);
        double dmg = dmg(p, def, 8.0, 0.5);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        impactFx(t, "wolf_mark", "ENTITY_WOLF_HOWL", "END_ROD", 0.5f, 1.0f, 8);
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, 0));
        t.setGlowing(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (t.isValid()) {
                t.setGlowing(false);
            }
        }, 6 * 20L);
        return true;
    }

    public boolean swallow(Player p, AbilityDef def) {
        castFx(p, "swallow", "ENTITY_GENERIC_DRINK", "EFFECT", 0.5f, 1.1f, 12);
        int secs = duration(def, 8);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, secs * 20, 0));
        impactFx(p, "swallow", "ENTITY_PLAYER_LEVELUP", "HEART", 0.4f, 1.3f, 6);
        return true;
    }

    public boolean piercingShot(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "piercing_shot", "ITEM_CROSSBOW_SHOOT", "CRIT", 0.7f, 1.0f, 8);
        double dmg = dmg(p, def, 12.0, 1.4);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        impactFx(t, "piercing_shot", "ENTITY_ARROW_HIT_PLAYER", "CRIT", 0.6f, 0.8f, 12);
        return true;
    }

    public boolean arrowFan(Player p, AbilityDef def) {
        castFx(p, "arrow_fan", "ENTITY_ARROW_SHOOT", "SWEEP_ATTACK", 0.6f, 1.1f, 10);
        double dmgEach = dmg(p, def, 6.0, 0.35);
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        for (int i = -1; i <= 1; i++) {
            Vector v = dir.clone().rotateAroundY(i * 0.22).multiply(2.2);
            launchFanArrow(p, v, dmgEach);
        }
        return true;
    }

    private void launchFanArrow(Player p, Vector velocity, double damage) {
        AbstractArrow arrow = p.launchProjectile(AbstractArrow.class, velocity);
        arrow.setShooter(p);
        arrow.setDamage(damage);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setLifetimeTicks(600);
        arrow.getPersistentDataContainer().set(fanArrowKey, PersistentDataType.BYTE, (byte) 1);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (arrow.isValid()) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (arrow.isValid()) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
        }, 2L);
    }

    public boolean arrowRain(Player p, AbilityDef def) {
        double radius = cfgD("classes.HUNTER.abilities." + def.id() + ".radius", 5.0);
        castFx(p, "arrow_rain", "ENTITY_ARROW_SHOOT", "POOF", 0.8f, 0.9f, 24);
        double dmg = dmg(p, def, 10.0, 0.9);
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
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            impactFx(t, "arrow_rain", "ENTITY_ARROW_HIT_PLAYER", "POOF", 0.4f, 1.0f, 6);
            hit = true;
        }
        return hit;
    }
}
