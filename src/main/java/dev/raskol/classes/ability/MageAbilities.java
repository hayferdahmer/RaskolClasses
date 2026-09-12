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
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * 1.7.3 → 1.9.0: КИТ МАГА (греческая мифология), редизайн визуала по спеке владельца.
 *
 * 1. «Огонь Прометея» — быстрый огненный шар ПО НАПРАВЛЕНИЮ (цель не обязательна):
 *    промах/блок = вспышка и звук, ресурс потрачен (выстрел «впустую» осознанно).
 *    Попадание в сущность = гибрид 30/70 + поджог 3 с (в FxService.onProjectileHit).
 * 2. «Шаг Гермеса» — блинк до 16 блоков рейтрейсом: сквозь блоки НЕ проходит,
 *    упирается перед блоком; безопасности/тауни-гейта НЕТ; сущности на пути
 *    получают маг-урон.
 * 3. «Дыхание Борея» — AoE-нова: кольцо партиклов, урон+Slowness II, отчёт целей.
 * 4. «Эгида Афины» — грант маг-резиста + аура партиклов на кастере + звук снятия.
 * 5. «Гнев Зевса» — визуальная молния в цель + урон + поджог; execute = вторая молния.
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

    /* -------------------------------- способности -------------------------------- */

    /**
     * 1. «Огонь Прометея»: огненный шар по направлению взгляда, скорость из конфига
     * (дефолт 2.6 — быстрее прежнего 1.6). Цель не обязательна: промах = вспышка+звук,
     * ресурс потрачен. Попадание обрабатывает FxService.onProjectileHit
     * (гибрид 30/70 + поджог 3 с + вспышка/звук).
     */
    public boolean firePrometheus(Player p, AbilityDef def) {
        double speed = cfgD("classes.MAGE.abilities." + def.id() + ".projectile-speed", 2.6);
        double dmg = dmg(p, def, 15.0, 1.2);
        Vector dir = p.getLocation().getDirection().normalize();
        Fireball fb = p.launchProjectile(Fireball.class, dir.multiply(speed));
        fb.setShooter(p);
        fb.setIsIncendiary(false);   // без ванильного поджога блоков от взрыва
        fb.setYield(0.0f);           // без разрушения блоков
        plugin.getFx().chargeProjectile(fb.getUniqueId(), p.getUniqueId(), def.id(),
                dmg * 0.3, dmg * 0.7, 3 * 20);
        return true; // выстрел состоялся всегда → ресурс списан конвейером
    }

    /**
     * 2. «Шаг Гермеса»: блинк рейтрейсом до 16 блоков. Сквозь блоки не проходит:
     * при упоре в блок телепорт ПЕРЕД блоком. Безопасности/тауни-гейта нет.
     * Сущности на отрезке пути получают маг-урон + вспышку.
     */
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
            // уперлись в блок: встаём перед ним (отступ вдоль луча)
            dest = hit.getHitPosition().toLocation(p.getWorld());
            dest.subtract(dir.clone().multiply(0.4));
            int guard = 0;
            while (!dest.getBlock().isPassable() && guard++ < 10) {
                dest.subtract(dir.clone().multiply(0.3));
            }
        }
        dest.setYaw(origin.getYaw());
        dest.setPitch(origin.getPitch());

        // урон сущностям на отрезке пути (радиус 1.2 от сегмента)
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

        // телепорт с портальным визуалом на обоих концах
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

    /** Расстояние от точки до отрезка [a, b] (по X/Z + Y). */
    private static double distanceToSegment(Location point, Location a, Location b) {
        Vector ab = b.toVector().subtract(a.toVector());
        Vector ap = point.toVector().subtract(a.toVector());
        double lenSq = ab.lengthSquared();
        double t = lenSq == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ap.dot(ab) / lenSq));
        Vector closest = a.toVector().add(ab.multiply(t));
        return point.toVector().distance(closest);
    }

    /**
     * 3. «Дыхание Борея»: nova радиус 5 (LOS, фракционный фильтр).
     * Нет целей → отказ ДО визуала (refund). Есть → кольцо партиклов, урон+Slowness II,
     * отчёт «поражено N».
     */
    public boolean boreasBreath(Player p, AbilityDef def) {
        double radius = cfgD("classes.MAGE.abilities." + def.id() + ".radius", 5.0);
        int secs = duration(def, 4);
        double dmg = dmg(p, def, 12.0, 1.0);

        // предварительный подсчёт целей, чтобы не жечь каст впустую
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
            return false; // конвейер вернёт ресурс, кулдаун не встанет
        }

        // каст-визуал: расширяющееся кольцо
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

    /**
     * 4. «Эгида Афины»: грант маг-резиста на 5 с + аура партиклов на кастере
     * (видимо на персонаже всё время действия) + звук снятия по истечении.
     */
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
        // аура на всё время действия + звук снятия
        plugin.getFx().startAura(uuid, Particle.ENCHANT, secs * 20, 3,
                plugin.getConfig().getString("vfx.athena_aegis.expire-sound", "BLOCK_AMETHYST_BLOCK_CHIME"));
        p.sendMessage(Component.text("Эгида Афины: +" + (int) grant + "% магрезиста на " + secs + " с",
                NamedTextColor.AQUA));
        return true;
    }

    /**
     * 5. «Гнев Зевса»: визуальная молния в цель + маг-урон + поджог 3 с.
     * Execute (цель < порога): вторая молния + красный FLASH + тег «Кара Зевса ×3!».
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
        double threshold = cfgD("classes.MAGE.abilities." + def.id() + ".threshold", 0.25);
        double max = maxOf(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 25.0, 2.0);
        int fireTicks = (int) cfgD("classes.MAGE.abilities." + def.id() + ".fire-ticks", 60);

        Location targetLoc = t.getLocation().add(0.0, 1.0, 0.0);
        plugin.getFx().strikeLightningVisual(targetLoc);
        plugin.getFx().impactBurst(targetLoc, Particle.FLASH, 8, null, 0f, 1f);
        plugin.getFx().impactBurst(targetLoc, Particle.ELECTRIC_SPARK, 20,
                Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.0f);

        if (frac < threshold) {
            dmg *= cfgD("classes.MAGE.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            plugin.getFx().strikeLightningVisual(targetLoc);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute-mage", "Кара Зевса ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        }
        if (plugin.getCombat().canHit(p, t)) {
            t.setFireTicks(fireTicks);
        }
        return true;
    }

    private double maxOf(LivingEntity e) {
        var attr = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
