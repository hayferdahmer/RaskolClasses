// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.UUID;

/**
 * КИТ МАГА (1.9.0-fix7 → 1.12.5).
 *  1. «Огонь Прометея» — снаряд Snowball: трейл/impact через FxService, горение =
 *     школьный DoT burning (PDC-тег rc_dot), ванильный fire-ticks убран.
 *  2. «Шаг Гермеса» — блинк 16 блоков, упор в блок, урон сквозь мобов на пути.
 *  3. «Дыхание Борея» — nova: маг-урон + Slowness II + DoT chilled; без целей = refund.
 *  4. «Эгида Афины» — грант маг-резиста + аура + звук снятия.
 *  5. «Гнев Зевса» — мгновенный урон + сцена; поджог заменён на DoT burning.
 * 1.12.3 (Батч 5): школы FIRE/ARCANE/FROST, каст/impact/execute-VFX конфиг-драйвен.
 * 1.14.0 (Б4): талантовые хуки baseBonus/coeffMult читаются из Spec2Service.
 */
public final class MageAbilities {

    private final RaskolClasses plugin;

    public MageAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
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

    /** 1.14.0 (Б4): хуки Spec2Service. */
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

    /* ------------------------------ VFX-хелперы ------------------------------ */

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

    private void executeFx(LivingEntity target) {
        Sound sound = plugin.getFx().resolveSound(
                cfgS("vfx.zeus_wrath.execute-sound", "ENTITY_LIGHTNING_BOLT_THUNDER"));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, 1.0f, 0.6f);
        }
        Particle particle = resolveParticle(cfgS("vfx.zeus_wrath.execute-particle", "FLASH"));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, 6, 0.2, 0.3, 0.2, 0.0);
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

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Огонь Прометея»: снаряд с тегом rc_dot=burning; ванильного поджога нет. */
    public boolean firePrometheus(Player p, AbilityDef def) {
        castFx(p, "fire_prometheus", "ITEM_FIRECHARGE_USE", "FLAME", 0.6f, 1.0f, 12);
        double speed = cfgD("classes.MAGE.abilities." + def.id() + ".projectile-speed", 2.6);
        double dmg = dmg(p, def, 15.0, 1.2);
        Vector dir = p.getLocation().getDirection().normalize();
        Snowball sb = p.launchProjectile(Snowball.class, dir.multiply(speed));
        sb.setShooter(p);
        sb.getPersistentDataContainer().set(plugin.getCombat().dots().dotTagKey(),
                PersistentDataType.STRING, "burning");
        plugin.getFx().chargeProjectile(sb.getUniqueId(), p.getUniqueId(), def.id(),
                0.0, dmg, 0);
        return true;
    }

    /** 2. «Шаг Гермеса»: блинк с уроном по пути; VFX конфиг-драйвен. */
    public boolean hermesStep(Player p, AbilityDef def) {
        double maxDist = cfgD("classes.MAGE.abilities." + def.id() + ".distance", 16.0);
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Location origin = p.getLocation();

        Location dest;
        RayTraceResult hit = p.getWorld().rayTraceBlocks(
                origin.clone().add(0.0, 1.0, 0.0), dir, maxDist, FluidCollisionMode.NEVER);
        if (hit == null) {
            dest = origin.clone().add(dir.clone().multiply(maxDist));
        } else {
            dest = hit.getHitPosition().toLocation(p.getWorld());
            dest.subtract(dir.clone().multiply(0.4));
            int guard = 0;
            while (!dest.getBlock().isPassable() && guard++ < 10) {
                dest.subtract(dir.clone().multiply(0.3));
            }
        }
        dest.setYaw(origin.getYaw());
        dest.setPitch(origin.getPitch());

        castFx(p, "hermes_step", "ENTITY_ENDERMAN_TELEPORT", "REVERSE_PORTAL", 0.5f, 1.2f, 16);

        double pathDmg = dmg(p, def, 10.0, 0.8);
        int hitCount = 0;
        for (Entity e : p.getNearbyEntities(maxDist + 2, maxDist + 2, maxDist + 2)) {
            if (!(e instanceof LivingEntity t) || e.equals(p)) {
                continue;
            }
            if (!plugin.getCombat().canHit(p, t)) {
                continue;
            }
            if (distanceToSegment(t.getLocation(), origin, dest) <= 1.2) {
                plugin.getCombat().dealDamage(t, p, DamageProfile.magic(pathDmg));
                impactFx(t, "hermes_step", "ENTITY_ENDERMAN_HURT", "PORTAL", 0.3f, 1.4f, 10);
                hitCount++;
            }
        }

        p.teleport(dest);
        Particle destParticle = resolveParticle(
                cfgS("vfx.hermes_step.cast-particle", "REVERSE_PORTAL"));
        Sound destSound = plugin.getFx().resolveSound(
                cfgS("vfx.hermes_step.cast-sound", "ENTITY_ENDERMAN_TELEPORT"));
        if (destParticle != null) {
            plugin.getFx().impactBurst(dest.clone().add(0.0, 1.0, 0.0),
                    destParticle, 16, destSound, 0.4f, 1.4f);
        }
        if (hitCount > 0) {
            p.sendMessage(Component.text("Шаг Гермеса: пронесено сквозь " + hitCount + " целей",
                    NamedTextColor.LIGHT_PURPLE));
        }
        return true;
    }

    private static double distanceToSegment(Location point, Location a, Location b) {
        Vector ab = b.toVector().subtract(a.toVector());
        Vector ap = point.toVector().subtract(a.toVector());
        double lenSq = ab.lengthSquared();
        double t = lenSq == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ap.dot(ab) / lenSq));
        Vector closest = a.toVector().add(ab.multiply(t));
        return point.toVector().distance(closest);
    }

    /** 3. «Дыхание Борея»: nova + Slowness II + DoT chilled; без целей = refund. */
    public boolean boreasBreath(Player p, AbilityDef def) {
        double radius = cfgD("classes.MAGE.abilities." + def.id() + ".radius", 5.0);
        int secs = duration(def, 4);
        double dmg = dmg(p, def, 12.0, 1.0);

        int targets = 0;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity t && !e.equals(p)
                    && Targeting.isValidDamageTarget(plugin, p, t)
                    && Targeting.hasLineOfSight(plugin, p, t)) {
                targets++;
            }
        }
        if (targets == 0) {
            p.sendMessage(Component.text("Дыхание Борея: целей в радиусе нет", NamedTextColor.GRAY));
            return false;
        }

        Location center = p.getLocation().add(0.0, 0.3, 0.0);
        Particle castParticle = resolveParticle(cfgS("vfx.boreas_breath.cast-particle", "SNOWFLAKE"));
        if (castParticle != null) {
            p.getWorld().spawnParticle(castParticle, center, 60, radius * 0.7, 0.2, radius * 0.7, 0.05);
        }
        p.getWorld().spawnParticle(Particle.CLOUD, center, 24, radius * 0.5, 0.3, radius * 0.5, 0.03);
        Sound cast = plugin.getFx().resolveSound(
                cfgS("vfx.boreas_breath.cast-sound", "BLOCK_SNOW_BREAK"));
        if (cast != null) {
            plugin.getFx().playSound(p.getLocation(), cast, 0.7f, 0.9f);
        }

        int hit = 0;
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
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, 1));
            plugin.getCombat().dots().applyById(p, t, "chilled");
            impactFx(t, "boreas_breath", "ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE", 0.4f, 1.0f, 12);
            hit++;
        }
        p.sendMessage(Component.text("Дыхание Борея: поражено " + hit, NamedTextColor.AQUA));
        return true;
    }

    /** 4. «Эгида Афины»: грант маг-резиста + аура + звук снятия. 1.14.0 (Б4): хуки Spec2. */
    public boolean athenaAegis(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 15.0) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, 0.05) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().spellPower(uuid) * c;
        int secs = duration(def, 5);

        plugin.getResists().addTimedModifier(uuid, def.id(), 0.0, grant, secs * 1000L);

        Sound grantSound = plugin.getFx().resolveSound(
                cfgS("vfx.athena_aegis.cast-sound", "ITEM_ARMOR_EQUIP_DIAMOND"));
        if (grantSound != null) {
            plugin.getFx().playSound(p.getLocation(), grantSound, 0.6f, 1.0f);
        }
        Particle castParticle = resolveParticle(
                cfgS("vfx.athena_aegis.cast-particle", "ENCHANTED_HIT"));
        if (castParticle != null) {
            p.getWorld().spawnParticle(castParticle, p.getLocation().add(0.0, 1.0, 0.0),
                    24, 0.4, 0.8, 0.4, 0.05);
        }
        plugin.getFx().startAura(uuid, Particle.ENCHANT, secs * 20, 3,
                cfgS("vfx.athena_aegis.expire-sound", "BLOCK_AMETHYST_BLOCK_CHIME"));
        p.sendMessage(Component.text("Эгида Афины: +" + (int) grant + "% магрезиста на " + secs + " с",
                NamedTextColor.AQUA));
        return true;
    }

    /** 5. «Гнев Зевса»: урон МГНОВЕННО при касте; сцена молнии/подброса поверх. */
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
        UUID casterId = p.getUniqueId();
        UUID targetId = t.getUniqueId();
        castFx(p, "zeus_wrath", "ENTITY_EVOKER_CAST_SPELL", "ELECTRIC_SPARK", 0.7f, 0.9f, 14);
        double dmg = dmg(p, def, 25.0, 2.0);
        double threshold = cfgD("classes.MAGE.abilities." + def.id() + ".threshold", 0.25);
        double execMult = cfgD("classes.MAGE.abilities." + def.id() + ".execute-mult", 3.0);

        var attr0 = t.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max0 = attr0 != null ? attr0.getValue() : 20.0;
        double frac0 = max0 > 0 ? t.getHealth() / max0 : 1.0;
        double finalDmg = dmg;
        boolean execute = frac0 < threshold;
        if (execute) {
            finalDmg *= execMult;
        }
        double dealt = plugin.getCombat().dealDamage(t, p, DamageProfile.magic(finalDmg), true);
        if (dealt <= 0.0) {
            plugin.getLogger().warning("zeus_wrath: урон 0 по " + t.getType()
                    + " (событие отменено внешним плагином: WG/Towny/GrimAC?)");
        }
        if (execute) {
            executeFx(t);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute-mage", "Кара Зевса ×3!"), NamedTextColor.RED));
        }

        Location castLoc = t.getLocation().add(0.0, 1.0, 0.0);
        Sound thunder = plugin.getFx().resolveSound(
                cfgS("vfx.zeus_wrath.execute-sound", "ENTITY_LIGHTNING_BOLT_THUNDER"));
        if (thunder != null) {
            plugin.getFx().playSound(castLoc, thunder, 1.0f, 0.6f);
        }
        if (t instanceof Player tp && tp.isValid()) {
            tp.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 2 * 20, 0));
        }
        Particle spark = resolveParticle(cfgS("vfx.zeus_wrath.cast-particle", "ELECTRIC_SPARK"));
        if (spark != null) {
            plugin.getFx().impactBurst(castLoc, spark, 30, null, 0f, 1f);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Entity e = plugin.getServer().getEntity(targetId);
            if (e instanceof LivingEntity living && living.isValid()) {
                living.setVelocity(new Vector(0.0, 1.2, 0.0));
            }
        }, 10L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Entity e = plugin.getServer().getEntity(targetId);
            if (!(e instanceof LivingEntity living) || !living.isValid()) {
                return;
            }
            Player caster = plugin.getServer().getPlayer(casterId);
            if (caster == null) {
                return;
            }
            Location strikeLoc = living.getLocation().add(0.0, 1.0, 0.0);
            plugin.getFx().strikeLightningVisual(strikeLoc);
            Sound impactSound = plugin.getFx().resolveSound(
                    cfgS("vfx.zeus_wrath.impact-sound", "ENTITY_LIGHTNING_BOLT_IMPACT"));
            Particle flash = resolveParticle(cfgS("vfx.zeus_wrath.impact-particle", "FLASH"));
            if (flash != null) {
                plugin.getFx().impactBurst(strikeLoc, flash, 12, null, 0f, 1f);
                plugin.getFx().impactBurst(strikeLoc,
                        resolveParticle(cfgS("vfx.zeus_wrath.cast-particle", "ELECTRIC_SPARK")) != null
                                ? resolveParticle(cfgS("vfx.zeus_wrath.cast-particle", "ELECTRIC_SPARK"))
                                : Particle.ELECTRIC_SPARK,
                        30, impactSound, 0.8f, 1.0f);
            }
            if (plugin.getCombat().canHit(caster, living)) {
                plugin.getCombat().dots().applyById(caster, living, "burning");
            }
        }, 20L);

        return true;
    }
}
