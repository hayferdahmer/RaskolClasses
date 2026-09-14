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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * КИТ МАГА (1.9.0-fix6).
 *  1. «Огонь Прометея» — чистая магия; снаряд Snowball без взрыва + огненный трейл.
 *  2. «Шаг Гермеса» — блинк 16 блоков, упор в блок, урон сквозь мобов на пути.
 *  3. «Дыхание Борея» — nova: маг-урон + Slowness II; без целей = refund.
 *  4. «Эгида Афины» — грант маг-резиста + аура + звук снятия.
 *  5. «Гнев Зевса» — сцена гром→Darkness→подброс→молния.
 *     1.9.0-fix6 (баг «не убил курицу»): цель перезолвится по UUID в момент удара
 *     (живая ссылка на LivingEntity в отложенной задаче могла отваливаться);
 *     тело удара в try/catch с warning, если урон=0 — причина станет видна в логе.
 *     Burst-окно и кап по мобам НЕ действуют (капы только по игрокам), ульта идёт
 *     с allowOverCap=true — ограничение не является причиной нулевого урона.
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
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getTalentService().coeffMult(uuid, def.id());
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

    /** 1. «Огонь Прометея»: чистая магия, снаряд без взрыва. */
    public boolean firePrometheus(Player p, AbilityDef def) {
        double speed = cfgD("classes.MAGE.abilities." + def.id() + ".projectile-speed", 2.6);
        double dmg = dmg(p, def, 15.0, 1.2);
        Vector dir = p.getLocation().getDirection().normalize();
        Snowball sb = p.launchProjectile(Snowball.class, dir.multiply(speed));
        sb.setShooter(p);
        plugin.getFx().chargeProjectile(sb.getUniqueId(), p.getUniqueId(), def.id(),
                0.0, dmg, 3 * 20);
        return true;
    }

    /** 2. «Шаг Гермеса». */
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
                plugin.getFx().impactBurst(t.getLocation().add(0.0, 1.0, 0.0),
                        Particle.PORTAL, 10, Sound.ENTITY_ENDERMAN_HURT, 0.3f, 1.4f);
                hitCount++;
            }
        }

        plugin.getFx().impactBurst(origin.clone().add(0.0, 1.0, 0.0),
                Particle.REVERSE_PORTAL, 16, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
        p.teleport(dest);
        plugin.getFx().impactBurst(dest.clone().add(0.0, 1.0, 0.0),
                Particle.PORTAL, 16, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.4f);
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

    /** 3. «Дыхание Борея». */
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
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 60, radius * 0.7, 0.2, radius * 0.7, 0.05);
        p.getWorld().spawnParticle(Particle.CLOUD, center, 24, radius * 0.5, 0.3, radius * 0.5, 0.03);
        Sound cast = plugin.getFx().resolveSound(
                plugin.getConfig().getString("vfx.boreas_breath.cast-sound", "BLOCK_SNOW_BLOCK_BREAK"));
        if (cast != null) {
            plugin.getFx().playSound(p, cast, 0.7f, 0.9f);
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
            t.getWorld().spawnParticle(Particle.SNOWFLAKE, t.getLocation().add(0.0, 1.0, 0.0), 12, 0.4, 0.5, 0.4, 0.03);
            hit++;
        }
        p.sendMessage(Component.text("Дыхание Борея: поражено " + hit, NamedTextColor.AQUA));
        return true;
    }

    /** 4. «Эгида Афины». */
    public boolean athenaAegis(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 15.0) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, 0.05) * plugin.getTalentService().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().spellPower(uuid) * c;
        int secs = duration(def, 5);

        plugin.getResists().addTimedModifier(uuid, def.id(), 0.0, grant, secs * 1000L);

        Sound grantSound = plugin.getFx().resolveSound(
                plugin.getConfig().getString("vfx.athena_aegis.cast-sound", "ITEM_ARMOR_EQUIP_DIAMOND"));
        if (grantSound != null) {
            plugin.getFx().playSound(p, grantSound, 0.6f, 1.0f);
        }
        p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0.0, 1.0, 0.0), 24, 0.4, 0.8, 0.4, 0.05);
        plugin.getFx().startAura(uuid, Particle.ENCHANT, secs * 20, 3,
                plugin.getConfig().getString("vfx.athena_aegis.expire-sound", "BLOCK_AMETHYST_BLOCK_CHIME"));
        p.sendMessage(Component.text("Эгида Афины: +" + (int) grant + "% магрезиста на " + secs + " с",
                NamedTextColor.AQUA));
        return true;
    }

    /**
     * 5. «Гнев Зевса»: сцена с перезолвом цели по UUID (1.9.0-fix6).
     * Урон и execute-порог пересчитываются в момент удара, а не на касте.
     */
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
        double dmg = dmg(p, def, 25.0, 2.0);
        int fireTicks = (int) cfgD("classes.MAGE.abilities." + def.id() + ".fire-ticks", 60);
        double threshold = cfgD("classes.MAGE.abilities." + def.id() + ".threshold", 0.25);
        double execMult = cfgD("classes.MAGE.abilities." + def.id() + ".execute-mult", 3.0);

        // Фаза 1: гром + затемнение
        Location castLoc = t.getLocation().add(0.0, 1.0, 0.0);
        plugin.getFx().playSound(castLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);
        if (t instanceof Player tp) {
            tp.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 2 * 20, 0));
        }
        plugin.getFx().impactBurst(castLoc, Particle.ELECTRIC_SPARK, 30, null, 0f, 1f);

        // Фаза 2: подброс
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Entity e = plugin.getServer().getEntity(targetId);
            if (e instanceof LivingEntity living && living.isValid()) {
                living.setVelocity(new Vector(0.0, 1.2, 0.0));
            }
        }, 10L);

        // Фаза 3: молния + урон (цель перезолвлена по UUID)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                Entity e = plugin.getServer().getEntity(targetId);
                if (!(e instanceof LivingEntity living) || living.isDead()) {
                    return;
                }
                Player caster = plugin.getServer().getPlayer(casterId);
                if (caster == null) {
                    return;
                }
                Location strikeLoc = living.getLocation().add(0.0, 1.0, 0.0);
                plugin.getFx().strikeLightningVisual(strikeLoc);
                plugin.getFx().impactBurst(strikeLoc, Particle.FLASH, 12, null, 0f, 1f);
                plugin.getFx().impactBurst(strikeLoc, Particle.ELECTRIC_SPARK, 30,
                        Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);

                var attr = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                double max = attr != null ? attr.getValue() : 20.0;
                double frac = max > 0 ? living.getHealth() / max : 1.0;
                double finalDmg = dmg;
                boolean execute = frac < threshold;
                if (execute) {
                    finalDmg *= execMult;
                }
                double dealt = plugin.getCombat().dealDamage(living, caster,
                        DamageProfile.magic(finalDmg), true);
                if (dealt <= 0.0) {
                    plugin.getLogger().warning("zeus_wrath: урон 0 по " + living.getType()
                            + " (возможно, событие отменено внешним плагином: WG/Towny/GrimAC)");
                }
                if (execute && living.isValid()) {
                    plugin.getFx().strikeLightningVisual(strikeLoc);
                    caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "tag.execute-mage", "Кара Зевса ×3!"), NamedTextColor.RED));
                }
                if (living.isValid() && plugin.getCombat().canHit(caster, living)) {
                    living.setFireTicks(fireTicks);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("zeus_wrath: исключение в фазе удара: " + ex.getMessage());
            }
        }, 20L);

        return true;
    }
}
