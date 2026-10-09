// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.cc.CastChannels;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.school.School;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * КИТ МАГА (1.9.0-fix7 → 1.12.5).
 *  1. «Огонь Прометея» — снаряд Snowball: горение = DoT burning (PDC-тег rc_dot).
 *  2. «Шаг Гермеса» — блинк 16 блоков, упор в блок, урон сквозь мобов на пути.
 *  3. «Дыхание Борея» — nova: маг-урон + Slowness II + DoT chilled; без целей = refund.
 *  4. «Эгида Афины» — грант маг-резиста + аура + звук снятия.
 *  5. «Гнев Зевса» — мгновенный урон + сцена; поджог = DoT burning.
 * 1.12.3: школы FIRE/ARCANE/FROST, каст/impact/execute-VFX конфиг-драйвен.
 * 1.14.0 (Б4): хуки baseBonus/coeffMult из Spec2Service.
 * 1.14.0 (контент-долг 4): +11 древесных способностей Мага:
 *   arcane: arcane_missiles, counterspell, presence_of_mind;
 *   fire: scorch, flamestrike, combustion, pyroblast (ульт);
 *   frost: frostbolt, blizzard, ice_barrier, ice_lance_shatter (ульт).
 *   Гейт — treeUnlocked(); бафф «Возгорание» — статическая карта COMBUSTION_UNTIL.
 * 1.14.0 (Б11.1.2-A): base/coeff/power/duration + threshold/execute-mult(zeus_wrath)
 *   читаются через TreeAbilities.*OrKit (treeAbilities → abilities → код-дефолт),
 *   чтобы переносимые (athena_aegis/zeus_wrath) пережили резку abilities.
 *   projectile-speed/distance/radius(boreas) у slots 1–3 НЕ тронуты (не переносимые).
 * 1.14.7 (Sprint 3, P0-6A): ice_barrier читает длительность через
 *   TreeAbilities.durationWithSpec (kit_dur fr_ice_ward_enh: +2 с за ранг).
 */
public final class MageAbilities {

    private static final PlayerClass PC = PlayerClass.MAGE;

    /** 1.14.0: «Возгорание» — ×1.25 урона заклинаний на 8 с. */
    private static final Map<UUID, Long> COMBUSTION_UNTIL = new ConcurrentHashMap<>();

    private final RaskolClasses plugin;

    public MageAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private String cfgS(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null ? v : def;
    }

    // 1.14.0 (Б11.1.2-A): фолбэк treeAbilities → abilities → def
    private double base(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "coeff", defv);
    }

    private String power(AbilityDef def) {
        return TreeAbilities.stringOrKit(plugin, PC, def.id(), "power", "sp");
    }

    private int duration(AbilityDef def, int defv) {
        return TreeAbilities.durationOrKit(plugin, PC, def.id(), defv);
    }

    private double tbase(AbilityDef def, double defv) {
        return cfgD("classes.MAGE.treeAbilities." + def.id() + ".base", defv);
    }

    private double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes.MAGE.treeAbilities." + def.id() + ".coeff", defv);
    }

    private String tpower(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.MAGE.treeAbilities." + def.id() + ".power", "sp");
    }

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c) * combustionMult(uuid);
    }

    private double tdmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, tpower(def), b, c) * combustionMult(uuid);
    }

    private double combustionMult(UUID uuid) {
        Long until = COMBUSTION_UNTIL.get(uuid);
        return until != null && until > System.currentTimeMillis() ? 1.25 : 1.0;
    }

    private boolean treeUnlocked(Player p, AbilityDef def) {
        if (plugin.getSpec2Service().hasUnlocked(p.getUniqueId(), def.id())) {
            return true;
        }
        p.sendMessage(Component.text("«" + def.displayName()
                + "» откроется узлом дерева путей Мага.", NamedTextColor.GRAY));
        return false;
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

    private int chilledStacks(LivingEntity t) {
        int stacks = 0;
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            if (inst.def().id().equals("chilled")) {
                stacks += inst.stacks();
            }
        }
        return stacks;
    }

    private boolean isBurning(LivingEntity t) {
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            if (inst.def().id().equals("burning") || inst.def().id().equals("burning_passive")) {
                return true;
            }
        }
        return false;
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

    private void pointFx(Location loc, String id, String soundDef, String particleDef,
                         float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(cfgS("vfx." + id + ".impact-sound", soundDef));
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(cfgS("vfx." + id + ".impact-particle", particleDef));
        if (particle != null) {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 0.5, 0.0),
                    count, 0.4, 0.3, 0.4, 0.02);
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

    /* -------------------------------- базовые способности -------------------------------- */

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

    /** 4. «Эгида Афины»: грант маг-резиста + аура + звук снятия (конфиг-драйвен). */
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
        // 1.14.0 (Б11.1.2-A): threshold/execute-mult через фолбэк-хелпер (переносимая slot 5)
        double threshold = TreeAbilities.numberOrKit(plugin, PC, def.id(), "threshold", 0.25);
        double execMult = TreeAbilities.numberOrKit(plugin, PC, def.id(), "execute-mult", 3.0);

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

    /* --------------------- древесные способности (1.14.0, контент-долг 4) --------------------- */

    /** arcane T2: 3 залпа маг-урона по цели (до 20 блоков). */
    public boolean arcaneMissiles(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "arcane_missiles", "ENTITY_EVOKER_CAST_SPELL", "REVERSE_PORTAL", 0.6f, 1.1f, 12);
        double per = tdmg(p, def, 5.0, 0.45);
        for (int i = 0; i < 3; i++) {
            if (t.isDead()) {
                break;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(per));
            impactFx(t, "arcane_missiles", "ENTITY_EVOKER_FANGS", "REVERSE_PORTAL",
                    0.35f, 1.0f + i * 0.1f, 6);
        }
        return true;
    }

    /** arcane T4: маг-урон + Немота 3 с + прерывание канала (CastChannels). */
    public boolean counterspell(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "counterspell", "BLOCK_NOTE_BLOCK_BASS", "REVERSE_PORTAL", 0.7f, 0.8f, 14);
        double dmg = tdmg(p, def, 4.0, 0.3);
        plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        plugin.getCC().tryApply(p, t, CCType.SILENCE, 60);
        if (CastChannels.interrupt(t.getUniqueId())) {
            p.sendMessage(Component.text("Контрзаклинание: каст цели прерван!", NamedTextColor.AQUA));
        }
        impactFx(t, "counterspell", "ENTITY_ENDERMAN_STARE", "REVERSE_PORTAL", 0.5f, 0.9f, 10);
        return true;
    }

    /**
     * arcane T5: «Присутствие разума».
     * ОТКЛОНЕНИЕ: мана → 100 + снятие с себя Немоты (бесплатный каст требует правок
     * AbilityRegistry.castOn — отдельный долг).
     */
    public boolean presenceOfMind(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        UUID uuid = p.getUniqueId();
        plugin.getResources().refund(uuid, 100.0);
        plugin.getCC().removeType(uuid, CCType.SILENCE);
        castFx(p, "presence_of_mind", "BLOCK_ENCHANTMENT_TABLE_USE", "ENCHANTED_HIT", 0.6f, 1.2f, 16);
        p.sendMessage(Component.text("Присутствие разума: мана восполнена, немота снята",
                NamedTextColor.AQUA));
        return true;
    }

    /** fire T2: маг-урон + горение (DoT burning). */
    public boolean scorch(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "scorch", "ITEM_FIRECHARGE_USE", "FLAME", 0.6f, 1.1f, 10);
        double dmg = tdmg(p, def, 7.0, 0.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        plugin.getCombat().dots().applyById(p, t, "burning");
        impactFx(t, "scorch", "ENTITY_BLAZE_SHOOT", "FLAME", 0.4f, 1.0f, 10);
        return true;
    }

    /**
     * fire T4: «Огненный столб» — AoE по точке (цель или блок до 20): урон + горение.
     * ОТКЛОНЕНИЕ: мгновенная зона вместо персистентной.
     */
    public boolean flamestrike(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 4.0);
        LivingEntity target = rayTarget(p, 20);
        Location spot;
        if (target != null && plugin.getCombat().canHit(p, target)) {
            spot = target.getLocation();
        } else {
            RayTraceResult hit = p.rayTraceBlocks(20.0);
            spot = hit != null
                    ? hit.getHitPosition().toLocation(p.getWorld())
                    : p.getLocation().add(p.getLocation().getDirection().multiply(8.0));
        }
        castFx(p, "flamestrike", "ENTITY_BLAZE_SHOOT", "LAVA", 0.8f, 0.8f, 18);
        double dmg = tdmg(p, def, 9.0, 0.8);
        int hits = 0;
        for (Entity e : p.getWorld().getNearbyEntities(spot, radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || t.equals(p) || t.isDead()) {
                continue;
            }
            if (!plugin.getCombat().canHit(p, t)) {
                continue;
            }
            if (t.getLocation().distanceSquared(spot) > radius * radius) {
                continue;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
            plugin.getCombat().dots().applyById(p, t, "burning");
            impactFx(t, "flamestrike", "ENTITY_BLAZE_HURT", "LAVA", 0.4f, 0.9f, 8);
            hits++;
        }
        pointFx(spot, "flamestrike", "ENTITY_GENERIC_EXPLODE", "FLAME", 0.7f, 0.9f, 24);
        if (hits == 0) {
            p.sendMessage(Component.text("Огненный столб: целей в зоне нет", NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    /** fire T5: «Возгорание» — ×1.25 урона заклинаний на 8 с. */
    public boolean combustion(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 8);
        COMBUSTION_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
        castFx(p, "combustion", "ITEM_FIRECHARGE_USE", "FLAME", 0.8f, 1.0f, 20);
        p.sendMessage(Component.text("Возгорание: +25% урона заклинаний на " + secs + " с",
                NamedTextColor.RED));
        return true;
    }

    /** fire T6 (ульт): огромный урон; по горящей цели ×1.5 + обновляет горение. */
    public boolean pyroblast(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        boolean burning = isBurning(t);
        castFx(p, "pyroblast", "ENTITY_EVOKER_CAST_SPELL", "LAVA", 0.9f, 0.7f, 22);
        double dmg = tdmg(p, def, 20.0, 1.8) * (burning ? 1.5 : 1.0);
        plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
        plugin.getCombat().dots().applyById(p, t, "burning");
        impactFx(t, "pyroblast", "ENTITY_GENERIC_EXPLODE", "FLAME", 0.7f, 0.7f, 26);
        if (burning) {
            p.sendMessage(Component.text("Огненная глыба: цель уже горела — ×1.5!", NamedTextColor.RED));
        }
        return true;
    }

    /** frost T2: маг-урон + охлаждение (chilled) + Slowness I 2 с. */
    public boolean frostbolt(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "frostbolt", "BLOCK_SNOW_BREAK", "SNOWFLAKE", 0.6f, 1.0f, 10);
        double dmg = tdmg(p, def, 8.0, 0.7);
        plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        plugin.getCombat().dots().applyById(p, t, "chilled");
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 0));
        impactFx(t, "frostbolt", "ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE", 0.4f, 1.0f, 10);
        return true;
    }

    /**
     * frost T4: «Снежная буря» — AoE по точке: урон + chilled + Slowness I 3 с.
     * ОТКЛОНЕНИЕ: мгновенная зона вместо персистентной.
     */
    public boolean blizzard(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 5.0);
        LivingEntity target = rayTarget(p, 20);
        Location spot;
        if (target != null && plugin.getCombat().canHit(p, target)) {
            spot = target.getLocation();
        } else {
            RayTraceResult hit = p.rayTraceBlocks(20.0);
            spot = hit != null
                    ? hit.getHitPosition().toLocation(p.getWorld())
                    : p.getLocation().add(p.getLocation().getDirection().multiply(8.0));
        }
        castFx(p, "blizzard", "BLOCK_SNOW_BREAK", "SNOWFLAKE", 0.8f, 0.9f, 20);
        double dmg = tdmg(p, def, 6.0, 0.5);
        int hits = 0;
        for (Entity e : p.getWorld().getNearbyEntities(spot, radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || t.equals(p) || t.isDead()) {
                continue;
            }
            if (!plugin.getCombat().canHit(p, t)) {
                continue;
            }
            if (t.getLocation().distanceSquared(spot) > radius * radius) {
                continue;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
            plugin.getCombat().dots().applyById(p, t, "chilled");
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, 0));
            impactFx(t, "blizzard", "ENTITY_PLAYER_HURT_FREEZE", "SNOWFLAKE", 0.4f, 1.0f, 8);
            hits++;
        }
        pointFx(spot, "blizzard", "BLOCK_SNOW_BREAK", "SNOWFLAKE", 0.7f, 0.9f, 30);
        if (hits == 0) {
            p.sendMessage(Component.text("Снежная буря: целей в зоне нет", NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    /**
     * frost T4: «Ледяная преграда» — щит-пул 15% formula-maxHP на 6 с (Absorption).
     * 1.14.7 (P0-6A): длительность через durationWithSpec (fr_ice_ward_enh: +2 с/ранг).
     */
    public boolean iceBarrier(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationWithSpec(plugin, PC, def.id(), 6, p.getUniqueId());
        double formula = plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        double scale = plugin.getHpBarService().scale(p);
        int shieldCarrier = (int) Math.max(4.0, Math.round(formula * 0.15 * scale));
        int level = Math.max(0, (shieldCarrier + 3) / 4 - 1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, secs * 20, level));
        castFx(p, "ice_barrier", "BLOCK_GLASS_PLACE", "SNOWFLAKE", 0.7f, 1.0f, 18);
        p.sendMessage(Component.text("Ледяная преграда: щит " + shieldCarrier + " HP на " + secs + " с",
                NamedTextColor.AQUA));
        return true;
    }

    /** frost T6 (ульт): урон; по охлаждённой цели ×(1+стеки) и скол (снятие chilled). */
    public boolean iceLanceShatter(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        int stacks = chilledStacks(t);
        castFx(p, "ice_lance_shatter", "BLOCK_GLASS_BREAK", "SNOWFLAKE", 0.9f, 0.8f, 20);
        double dmg = tdmg(p, def, 12.0, 1.1);
        if (stacks > 0) {
            dmg *= (1.0 + stacks);
            plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.FROST);
        } else {
            plugin.getCombat().dots().applyById(p, t, "chilled");
        }
        plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
        impactFx(t, "ice_lance_shatter", "BLOCK_GLASS_BREAK", "SNOWFLAKE", 0.7f, 0.9f, 24);
        if (stacks > 0) {
            p.sendMessage(Component.text("Ледяное копьё: раскол по " + stacks + " стекам охлаждения!",
                    NamedTextColor.AQUA));
        }
        return true;
    }

    /** 1.14.0: ids древесных способностей для сверки с TreeAbilities. */
    public static List<String> treeAbilityIds() {
        return List.of("arcane_missiles", "counterspell", "presence_of_mind",
                "scorch", "flamestrike", "combustion", "pyroblast",
                "frostbolt", "blizzard", "ice_barrier", "ice_lance_shatter");
    }
}
