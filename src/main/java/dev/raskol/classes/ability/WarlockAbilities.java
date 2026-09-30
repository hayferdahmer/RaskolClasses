// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CastChannels;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.fx.WarlockFx;
import dev.raskol.classes.spec.Spec;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.10.0: КИТ ЧЕРНОКНИЖНИКА (5 способностей, power = SP).
 * 1.10.4: «Чёрное Слово» v2 (бесплатно, плата 10% HP, +25 Скверны, без лечения, КД 3 с).
 * 1.11.1: проводка спек (classes.WARLOCK.specs.*).
 * 1.11.2: T2-задачи канала + S5 strip-absorption.
 * 1.11.4 (P4a): математика вынесена в WarlockMath (pure, selftest 41–43),
 *         визуал — в WarlockFx; здесь только оркестрация кита и реестры дебафов.
 * 1.13.0 (Б3): канал soul_rift регистрируется в CastChannels для прерывания
 *         interruptible-CC (STUN/SILENCE/FEAR). Отмена через cancelChannelTasks
 *         снимает и CastChannels-регистрацию (идемпотентно).
 */
public final class WarlockAbilities implements Listener {

    private static final PlayerClass PC = PlayerClass.WARLOCK;

    private static final Map<UUID, Long> SEAL_EXPIRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> SEAL_AMP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ANTIHEAL_EXPIRY = new ConcurrentHashMap<>();
    /** 1.11.2 (T2): задачи канала Раскола Души, indexed by caster UUID. */
    private static final Map<UUID, List<BukkitTask>> CHANNEL_TASKS = new ConcurrentHashMap<>();

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

    /** Плановая чистка протухших дебафов (вызывает ResourceService раз в 30 с). */
    public static void purgeStaleDebuffs() {
        long now = System.currentTimeMillis();
        SEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
        SEAL_AMP.keySet().removeIf(id -> !SEAL_EXPIRY.containsKey(id));
        ANTIHEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
    }

    /**
     * 1.11.2 (T2): отмена всех задач канала конкретного кастера.
     * 1.13.0 (Б3): дополнительно снимает регистрацию в CastChannels —
     *              idempotent (ConcurrentHashMap.remove по несуществующему ключу = no-op),
     *              поэтому безопасно вызывается и из CastChannels.interrupt-canceller,
     *              и из финальной задачи штатного завершения.
     */
    public static void cancelChannelTasks(UUID caster) {
        CastChannels.unregister(caster); // 1.13.0 (Б3): снять CastChannels-регистрацию
        List<BukkitTask> tasks = CHANNEL_TASKS.remove(caster);
        if (tasks == null) {
            return;
        }
        for (BukkitTask t : tasks) {
            if (t != null && !t.isCancelled()) {
                t.cancel();
            }
        }
    }

    /** 1.11.2 (T2): отмена всех задач всех кастеров (onDisable). */
    public static void cancelAllChannelTasks() {
        for (UUID uuid : new ArrayList<>(CHANNEL_TASKS.keySet())) {
            cancelChannelTasks(uuid);
        }
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

    /* ------------------------------ урон/множители (WarlockMath) ------------------------------ */

    private double damageMult(Player caster) {
        double corruption = plugin.getResources().getValue(caster.getUniqueId());
        boolean nether = caster.getWorld().getEnvironment() == World.Environment.NETHER;
        return WarlockMath.damageMult(
                corruption,
                plugin.getRaskolConfig().warlockThresholdOpen(),
                plugin.getRaskolConfig().warlockNetherMult(),
                specDamageMult(caster),
                nether);
    }

    private double spellDamage(Player caster, AbilityDef def, double defBase, double defCoeff) {
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        return (base(def, defBase) + sp * coeff(def, defCoeff)) * damageMult(caster);
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    /** Плата «Чёрного Слова»: pct% от maxHP (formula), не ниже 1 HP. */
    private void paySelfCost(Player caster, double pct) {
        double maxFormula = formulaMax(caster);
        double curFormula = currentFormulaHp(caster);
        double nextFormula = WarlockMath.selfCostNewHp(maxFormula, pct, curFormula, 1.0);
        double scale = plugin.getAttributes().scale(caster);
        caster.setHealth(Math.max(1.0, nextFormula * scale));
        WarlockFx.safeFx(caster.getLocation(), Particle.SCULK_SOUL, 10, 0.4);
    }

    /** 1.11.2 (S5): опциональное снятие Absorption при наложении анти-хила. */
    private void maybeStripAbsorption(LivingEntity target) {
        if (!(target instanceof Player tp)) {
            return;
        }
        if (!plugin.getConfig().getBoolean(
                "classes.WARLOCK.antiheal-strip-absorption", false)) {
            return;
        }
        PotionEffect abs = tp.getPotionEffect(PotionEffectType.ABSORPTION);
        if (abs != null) {
            tp.removePotionEffect(PotionEffectType.ABSORPTION);
        }
    }

    /* -------------------------------- способности -------------------------------- */

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
        WarlockFx.safeFx(t.getLocation(), Particle.SCULK_SOUL, 18, 0.5);
        WarlockFx.safeFx(t.getLocation(), Particle.SONIC_BOOM, 1, 0.0);
        WarlockFx.riseFx(t.getLocation(), Particle.SOUL, 6);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.6f, 1.1f);
        return true;
    }

    public boolean ruinSeal(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        double baseDuration = cfgD("classes.WARLOCK.abilities." + def.id() + ".duration", 66.6);
        double durationSec = specSealDuration(caster, baseDuration);
        double amplify = cfgD("classes.WARLOCK.abilities." + def.id() + ".amplify", 0.26);
        UUID id = t.getUniqueId();
        SEAL_EXPIRY.put(id, System.currentTimeMillis() + (long) (durationSec * 1000.0));
        SEAL_AMP.put(id, amplify);
        t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                (int) (durationSec * 20.0), 0, false, false, false));
        WarlockFx.ringFx(t.getLocation(), 1.2, Particle.SOUL_FIRE_FLAME, 3);
        WarlockFx.riseFx(t.getLocation(), Particle.CRIMSON_SPORE, 12);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.7f, 0.9f);
        return true;
    }

    public boolean hungerCorruption(Player caster, AbilityDef def) {
        double radius = radius(def, 6.0);
        double dmg = spellDamage(caster, def, 20.0, 1.2);
        Location center = caster.getLocation();
        double totalDealt = 0.0;

        WarlockFx.ringFx(center, radius, Particle.SCULK_SOUL, 2);
        WarlockFx.riseFx(center, Particle.WARPED_SPORE, 16);

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
                WarlockFx.safeFx(t.getLocation(), Particle.SCULK_SOUL, 10, 0.4);
                WarlockFx.riseFx(t.getLocation(), Particle.SOUL, 4);
            }
        }
        if (totalDealt <= 0.0) {
            return false;
        }
        // 1.11.4: дрейн через WarlockMath.drainHeal с капом lifesteal-cap
        double heal = WarlockMath.drainHeal(totalDealt, drain(def, 0.666),
                plugin.getRaskolConfig().warlockLifestealCap());
        plugin.getHpBarService().heal(caster, heal);
        plugin.getResources().add(caster.getUniqueId(),
                cfgD("classes.WARLOCK.abilities." + def.id() + ".corruption-gain", 12.0));
        plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.8f, 1.2f);
        return true;
    }

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
        int strips = specUnwritingStrips(caster);
        for (int i = 0; i < strips; i++) {
            String src = plugin.getResists().stripOneTimedModifier(t.getUniqueId());
            if (src == null) {
                break;
            }
            purged++;
            WarlockFx.safeFx(t.getLocation(), Particle.REVERSE_PORTAL, 8, 0.4);
        }
        double perPurged = cfgD("classes.WARLOCK.abilities." + def.id() + ".per-purged", 16.0);
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        double dmg = (base(def, 16.0) + sp * coeff(def, 0.8) + perPurged * purged) * damageMult(caster);
        WarlockFx.safeFx(t.getLocation(), Particle.SOUL, 14, 0.5);
        WarlockFx.safeFx(t.getLocation(), Particle.LARGE_SMOKE, 10, 0.4);
        double dealt = plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_VEX_DEATH, 0.6f, 0.8f);
        if (dealt <= 0.0) {
            return purged > 0;
        }
        return true;
    }

    /**
     * 1.11.2 (T2) + 1.11.4: канал с задачами в CHANNEL_TASKS; взрыв через WarlockMath.
     * 1.13.0 (Б3): регистрация канала в CastChannels для прерывания interruptible-CC
     *              (STUN/SILENCE/FEAR на кастере → cancelChannelTasks → отмена всех задач,
     *              включая финальный взрыв). Antiheal-таймеры жертв уже применены —
     *              не снимаются (это корректное поведение: зона уже нанесла дебафф).
     */
    public boolean soulRift(Player caster, AbilityDef def) {
        double radius = radius(def, 8.0) + specRiftRadiusBonus(caster);
        double channelSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".channel", 2.5);
        double antihealSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".antiheal", 6.0)
                + specAntihealBonus(caster);
        double missingBonus = cfgD("classes.WARLOCK.abilities." + def.id() + ".missing-hp-bonus", 0.666);
        int ticks = Math.max(1, (int) (channelSec * 20.0));

        plugin.getFx().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.9f, 0.7f);
        WarlockFx.ringFx(caster.getLocation(), radius, Particle.CRIMSON_SPORE, 2);

        List<BukkitTask> tasks = new ArrayList<>();
        // 1.13.0 (Б3): регистрация канала для прерывания CC (STUN/SILENCE/FEAR)
        CastChannels.register(caster.getUniqueId(),
                () -> cancelChannelTasks(caster.getUniqueId()));

        for (int i = 1; i <= ticks; i += 5) {
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!caster.isOnline() || caster.isDead()) {
                    return;
                }
                Location center = caster.getLocation();
                double tickDmg = 6.0 * damageMult(caster);
                WarlockFx.ringFx(center, radius, Particle.SCULK_SOUL, 1);
                for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                        continue;
                    }
                    if (!plugin.getCombat().canHit(caster, t)) {
                        continue;
                    }
                    plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(tickDmg));
                    WarlockFx.riseFx(t.getLocation(), Particle.SOUL, 3);
                    ANTIHEAL_EXPIRY.put(t.getUniqueId(),
                            System.currentTimeMillis() + (long) (antihealSec * 1000.0));
                    maybeStripAbsorption(t);
                }
            }, i);
            tasks.add(task);
        }

        BukkitTask finalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!caster.isOnline() || caster.isDead()) {
                return;
            }
            Location center = caster.getLocation();
            double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
            double baseDmg = (base(def, 30.0) + sp * coeff(def, 2.4)) * damageMult(caster);
            WarlockFx.ringFx(center, radius, Particle.SONIC_BOOM, 1);
            WarlockFx.ringFx(center, radius * 0.6, Particle.ASH, 3);
            for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                    continue;
                }
                if (!plugin.getCombat().canHit(caster, t)) {
                    continue;
                }
                double bonus = WarlockMath.missingHpBonus(
                        currentFormulaHp(t), formulaMax(t), missingBonus);
                double dmg = baseDmg + bonus;
                plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg), true);
                WarlockFx.safeFx(t.getLocation(), Particle.SCULK_SOUL, 20, 0.6);
                WarlockFx.riseFx(t.getLocation(), Particle.SOUL, 8);
                ANTIHEAL_EXPIRY.put(t.getUniqueId(),
                        System.currentTimeMillis() + (long) (antihealSec * 1000.0));
                maybeStripAbsorption(t);
            }
            plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.8f);
            purgeStaleDebuffs();
            // 1.13.0 (Б3): снятие CastChannels-регистрации при штатном завершении канала
            CastChannels.unregister(caster.getUniqueId());
            CHANNEL_TASKS.remove(caster.getUniqueId());
        }, ticks + 1L);
        tasks.add(finalTask);

        CHANNEL_TASKS.put(caster.getUniqueId(), tasks);
        return true;
    }

    /* ------------------------------ 1.11.2 (T2): отмена на выход ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelChannelTasks(event.getPlayer().getUniqueId());
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
