// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.spec.Spec;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.10.0: КИТ ЧЕРНОКНИЖНИКА (5 способностей, power = SP).
 * 1.10.4: «Чёрное Слово» v2 (бесплатно, плата 10% HP, +25 Скверны, без лечения, КД 3 с).
 * 1.11.1: ПРОВОДКА СПЕК — числовые трейты читаются из config
 *         (classes.WARLOCK.specs.*): урон/радиус/диспел-резиста у Чёрного Мага,
 *         длительность печати/анти-хил у Адского Канала. Реестры дебафов
 *         purge'ятся по расписанию (purgeStaleDebuffs public static).
 */
public final class WarlockAbilities {

    private static final PlayerClass PC = PlayerClass.WARLOCK;

    private static final Map<UUID, Long> SEAL_EXPIRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> SEAL_AMP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ANTIHEAL_EXPIRY = new ConcurrentHashMap<>();

    public static double sealAmplifyOf(UUID uuid) {
        Long expiry = SEAL_EXPIRY.get(uuid);
        if (expiry == null) {
            return 0.0;
        }
        if (System.currentTimeMillis() > expiry) {
            SEAL_EXPIRY.remove(uuid);
            SEAL_AMP.remove(uuid);
            return 0.0;
        }
        return SEAL_AMP.getOrDefault(uuid, 0.0);
    }

    public static boolean isAntihealed(UUID uuid) {
        Long expiry = ANTIHEAL_EXPIRY.get(uuid);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            ANTIHEAL_EXPIRY.remove(uuid);
            return false;
        }
        return true;
    }

    /** 1.11.1: плановая чистка протухших дебафов (вызывает ResourceService раз в 30 с). */
    public static void purgeStaleDebuffs() {
        long now = System.currentTimeMillis();
        SEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
        SEAL_AMP.keySet().removeIf(id -> !SEAL_EXPIRY.containsKey(id));
        ANTIHEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
    }

    private final RaskolClasses plugin;

    public WarlockAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private int cfgI(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".coeff", defv);
    }

    private double drain(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".drain", defv);
    }

    private double radius(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".radius", defv);
    }

    /* ------------------------------ спеки (1.11.1) ------------------------------ */

    private Spec specOf(Player p) {
        return plugin.getSpecService().getSpec(p.getUniqueId());
    }

    private boolean isBlackMage(Player p) {
        return specOf(p) == Spec.BLACK_MAGE;
    }

    private boolean isHellChannel(Player p) {
        return specOf(p) == Spec.HELL_CHANNEL;
    }

    private double specDamageMult(Player p) {
        return isBlackMage(p)
                ? 1.0 + cfgD("classes.WARLOCK.specs.black_mage.damage-mult", 0.10)
                : 1.0;
    }

    private double specRiftRadiusBonus(Player p) {
        return isBlackMage(p)
                ? cfgD("classes.WARLOCK.specs.black_mage.soul-rift-radius-bonus", 2.0)
                : 0.0;
    }

    private int specUnwritingStrips(Player p) {
        return isBlackMage(p)
                ? cfgI("classes.WARLOCK.specs.black_mage.unwriting-strips-resist", 1)
                : 0;
    }

    private double specSealDuration(Player p, double baseDuration) {
        return isHellChannel(p)
                ? cfgD("classes.WARLOCK.specs.hell_channel.seal-duration", 86.6)
                : baseDuration;
    }

    private double specAntihealBonus(Player p) {
        return isHellChannel(p)
                ? cfgD("classes.WARLOCK.specs.hell_channel.antiheal-bonus", 3.0)
                : 0.0;
    }

    /* ------------------------------ урон/множители ------------------------------ */

    private double damageMult(Player caster) {
        double mult = 1.0;
        double corruption = plugin.getResources().getValue(caster.getUniqueId());
        if (corruption >= plugin.getRaskolConfig().warlockThresholdOpen()) {
            mult *= 1.2;
        }
        if (caster.getWorld().getEnvironment() == World.Environment.NETHER) {
            mult *= plugin.getRaskolConfig().warlockNetherMult();
        }
        mult *= specDamageMult(caster); // 1.11.1: Чёрный Маг +10%
        return mult;
    }

    private double spellDamage(Player caster, AbilityDef def, double defBase, double defCoeff) {
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        return (base(def, defBase) + sp * coeff(def, defCoeff)) * damageMult(caster);
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    /* ------------------------------ визуал-хелперы ------------------------------ */

    private void safeFx(Location loc, Particle particle, int count, double spread) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        try {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 1.0, 0.0),
                    count, spread, spread * 0.6, spread, 0.04);
        } catch (IllegalArgumentException e) {
            loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.0, 1.0, 0.0),
                    Math.max(4, count / 2), spread, spread * 0.6, spread, 0.02);
        }
    }

    private void ringFx(Location center, double radius, Particle particle, int perPoint) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        try {
            int points = 20;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 * i) / points;
                Location p = center.clone().add(Math.cos(angle) * radius, 0.4, Math.sin(angle) * radius);
                center.getWorld().spawnParticle(particle, p, perPoint, 0.0, 0.3, 0.0, 0.01);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void riseFx(Location loc, Particle particle, int count) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        try {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 0.3, 0.0),
                    count, 0.35, 0.9, 0.35, 0.06);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void paySelfCost(Player caster, double pct) {
        double maxFormula = formulaMax(caster);
        double cost = maxFormula * pct / 100.0;
        double cur = currentFormulaHp(caster);
        double next = Math.max(1.0, cur - cost);
        double scale = plugin.getAttributes().scale(caster);
        caster.setHealth(Math.max(1.0, next * scale));
        safeFx(caster.getLocation(), Particle.SCULK_SOUL, 10, 0.4);
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Чёрное Слово» v2: бесплатно, плата 10% HP, +25 Скверны, без лечения, КД 3 с. */
    public boolean blackWord(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        paySelfCost(caster, cfgD("classes.WARLOCK.abilities." + def.id() + ".self-cost-pct", 10.0));
        plugin.getResources().add(caster.getUniqueId(),
                cfgD("classes.WARLOCK.abilities." + def.id() + ".corruption-gain", 25.0));

        double dmg = spellDamage(caster, def, 18.0, 1.5);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        safeFx(t.getLocation(), Particle.SCULK_SOUL, 18, 0.5);
        safeFx(t.getLocation(), Particle.SONIC_BOOM, 1, 0.0);
        riseFx(t.getLocation(), Particle.SOUL, 6);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.6f, 1.1f);
        return true;
    }

    /** 2. «Печать Погибели»: +26% входящего урона, Glowing; АК — длительность 86.6 с. */
    public boolean ruinSeal(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        double baseDuration = cfgD("classes.WARLOCK.abilities." + def.id() + ".duration", 66.6);
        double durationSec = specSealDuration(caster, baseDuration); // 1.11.1
        double amplify = cfgD("classes.WARLOCK.abilities." + def.id() + ".amplify", 0.26);
        UUID id = t.getUniqueId();
        SEAL_EXPIRY.put(id, System.currentTimeMillis() + (long) (durationSec * 1000.0));
        SEAL_AMP.put(id, amplify);
        t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                (int) (durationSec * 20.0), 0, false, false, false));
        ringFx(t.getLocation(), 1.2, Particle.SOUL_FIRE_FLAME, 3);
        riseFx(t.getLocation(), Particle.CRIMSON_SPORE, 12);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.7f, 0.9f);
        return true;
    }

    /** 3. «Голод Скверны»: AoE r6 + дрейн 66.6% + 12 Скверны. */
    public boolean hungerCorruption(Player caster, AbilityDef def) {
        double radius = radius(def, 6.0);
        double dmg = spellDamage(caster, def, 20.0, 1.2);
        Location center = caster.getLocation();
        double totalDealt = 0.0;

        ringFx(center, radius, Particle.SCULK_SOUL, 2);
        riseFx(center, Particle.WARPED_SPORE, 16);

        for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                continue;
            }
            if (!plugin.getCombat().canHit(caster, t)) {
                continue;
            }
            double dealt = plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
            if (dealt > 0.0) {
                totalDealt += dealt;
                safeFx(t.getLocation(), Particle.SCULK_SOUL, 10, 0.4);
                riseFx(t.getLocation(), Particle.SOUL, 4);
            }
        }
        if (totalDealt <= 0.0) {
            return false;
        }
        plugin.getHpBarService().heal(caster, totalDealt * drain(def, 0.666));
        plugin.getResources().add(caster.getUniqueId(),
                cfgD("classes.WARLOCK.abilities." + def.id() + ".corruption-gain", 12.0));
        plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.8f, 1.2f);
        return true;
    }

    /** 4. «Небытие»: диспел зелий + урон за каждый; ЧМ дополнительно стирает резист-модификаторы. */
    public boolean unwriting(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        int purged = 0;
        for (PotionEffect effect : List.copyOf(t.getActivePotionEffects())) {
            PotionEffectType type = effect.getType();
            if (type == PotionEffectType.SPEED || type == PotionEffectType.STRENGTH
                    || type == PotionEffectType.REGENERATION || type == PotionEffectType.INVISIBILITY
                    || type == PotionEffectType.RESISTANCE || type == PotionEffectType.ABSORPTION
                    || type == PotionEffectType.FIRE_RESISTANCE || type == PotionEffectType.HASTE) {
                t.removePotionEffect(type);
                purged++;
            }
        }
        // 1.11.1: Чёрный Маг стирает также timed-модификаторы резистов (гранты/руны/сеты-таймеры)
        int strips = specUnwritingStrips(caster);
        for (int i = 0; i < strips; i++) {
            String src = plugin.getResists().stripOneTimedModifier(t.getUniqueId());
            if (src == null) {
                break;
            }
            purged++;
            safeFx(t.getLocation(), Particle.REVERSE_PORTAL, 8, 0.4);
        }
        double perPurged = cfgD("classes.WARLOCK.abilities." + def.id() + ".per-purged", 16.0);
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        double dmg = (base(def, 16.0) + sp * coeff(def, 0.8) + perPurged * purged) * damageMult(caster);
        safeFx(t.getLocation(), Particle.SOUL, 14, 0.5);
        safeFx(t.getLocation(), Particle.LARGE_SMOKE, 10, 0.4);
        double dealt = plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_VEX_DEATH, 0.6f, 0.8f);
        if (dealt <= 0.0) {
            return purged > 0;
        }
        return true;
    }

    /** 5. «Раскол Души»: канал 2.5 с, зона r8 (+2 ЧМ), анти-хил 6 с (+3 АК), взрыв по missing-HP. */
    public boolean soulRift(Player caster, AbilityDef def) {
        double radius = radius(def, 8.0) + specRiftRadiusBonus(caster); // 1.11.1
        double channelSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".channel", 2.5);
        double antihealSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".antiheal", 6.0)
                + specAntihealBonus(caster); // 1.11.1
        double missingBonus = cfgD("classes.WARLOCK.abilities." + def.id() + ".missing-hp-bonus", 0.666);
        int ticks = Math.max(1, (int) (channelSec * 20.0));

        plugin.getFx().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.9f, 0.7f);
        ringFx(caster.getLocation(), radius, Particle.CRIMSON_SPORE, 2);

        for (int i = 1; i <= ticks; i += 5) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!caster.isOnline() || caster.isDead()) {
                    return;
                }
                Location center = caster.getLocation();
                double tickDmg = 6.0 * damageMult(caster);
                ringFx(center, radius, Particle.SCULK_SOUL, 1);
                for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                        continue;
                    }
                    if (!plugin.getCombat().canHit(caster, t)) {
                        continue;
                    }
                    plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(tickDmg));
                    riseFx(t.getLocation(), Particle.SOUL, 3);
                    ANTIHEAL_EXPIRY.put(t.getUniqueId(),
                            System.currentTimeMillis() + (long) (antihealSec * 1000.0));
                }
            }, i);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!caster.isOnline() || caster.isDead()) {
                return;
            }
            Location center = caster.getLocation();
            double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
            double baseDmg = (base(def, 30.0) + sp * coeff(def, 2.4)) * damageMult(caster);
            ringFx(center, radius, Particle.SONIC_BOOM, 1);
            ringFx(center, radius * 0.6, Particle.ASH, 3);
            for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                    continue;
                }
                if (!plugin.getCombat().canHit(caster, t)) {
                    continue;
                }
                double missing = Math.max(0.0, formulaMax(t) - currentFormulaHp(t));
                double dmg = baseDmg + missing * missingBonus;
                plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg), true);
                safeFx(t.getLocation(), Particle.SCULK_SOUL, 20, 0.6);
                riseFx(t.getLocation(), Particle.SOUL, 8);
                ANTIHEAL_EXPIRY.put(t.getUniqueId(),
                        System.currentTimeMillis() + (long) (antihealSec * 1000.0));
            }
            plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
            purgeStaleDebuffs();
        }, ticks + 1L);

        return true;
    }

    /* ------------------------------ unit-хелперы (план B) ------------------------------ */

    private double formulaMax(LivingEntity t) {
        if (t instanceof Player p) {
            return plugin.getAttributes().maxHp(p.getUniqueId());
        }
        double m = t.getMaxHealth();
        return Double.isFinite(m) && m > 0.0 ? m : 20.0;
    }

    private double currentFormulaHp(LivingEntity t) {
        if (t instanceof Player p) {
            return plugin.getAttributes().currentFormulaHp(p);
        }
        return t.getHealth();
    }
}
