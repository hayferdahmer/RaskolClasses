// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CastChannels;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.fx.WarlockFx;
import dev.raskol.classes.pet.PetService;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * 1.11.4 (P4a): математика в WarlockMath, визуал в WarlockFx.
 * 1.13.0 (Б3): канал soul_rift в CastChannels (прерывание interruptible-CC).
 * 1.14.0 (Б4): specOf() читает основную спеку из Spec2Storage.
 * 1.14.0 (контент-долг 1): публичный addAntiheal для древесных способностей Воина.
 * 1.14.0 (контент-долг 6): +10 древесных способностей Чернокнижника:
 *   affliction: withering, soul_siphon, soul_harvest (ульт);
 *   destruction: immolate, chaos_bolt, conflagrate;
 *   demonology: dreadfire, summon_demon, demonic_pact, demon_soul (ульт).
 * 1.14.0 (Б11.1.2-A): base/coeff/drain/radius + per-purged(unwriting) +
 *   channel/antiheal/missing-hp-bonus(soul_rift) читаются через TreeAbilities.*OrKit
 *   (treeAbilities → abilities → код-дефолт), чтобы переносимые
 *   (unwriting/soul_rift) пережили резку abilities. self-cost-pct/corruption-gain/
 *   amplify/duration(ruin_seal) у slots 1–3 НЕ тронуты (не переносимые);
 *   spec*()-хелперы читают classes.WARLOCK.specs.* — не трогаем (это спека, не slot).
 * 1.14.1 (Волна 1): heal() передаёт кастера для роли HEALER (CustomHealEvent).
 * 1.14.6 (6b): summon_demon / demon_soul — делегирование в PetService;
 *   inline-спавн Vex и static-карта DEMON_BY_OWNER удалены;
 *   чистка на PlayerQuit больше не нужна (PetService.onOwnerQuit).
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

    /** 1.14.0 (контент-долг 1): публичный анти-хил для древесных способностей Воина. */
    public static void addAntiheal(UUID target, int seconds) {
        if (seconds <= 0) {
            return;
        }
        ANTIHEAL_EXPIRY.put(target, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Плановая чистка протухших дебафов (вызывает ResourceService раз в 30 с). */
    public static void purgeStaleDebuffs() {
        long now = System.currentTimeMillis();
        SEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
        SEAL_AMP.keySet().removeIf(id -> !SEAL_EXPIRY.containsKey(id));
        ANTIHEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
    }

    /** 1.11.2 (T2): отмена всех задач канала конкретного кастера. */
    public static void cancelChannelTasks(UUID caster) {
        CastChannels.unregister(caster);
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

    // 1.14.0 (Б11.1.2-A): фолбэк treeAbilities → abilities → def
    private double base(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "coeff", defv);
    }

    private double drain(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "drain", defv);
    }

    private double radius(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "radius", defv);
    }

    private double tbase(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.treeAbilities." + def.id() + ".base", defv);
    }

    private double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.treeAbilities." + def.id() + ".coeff", defv);
    }

    /* ------------------------------ спеки (1.14.0 Б4: источник — spec2) ------------------------------ */

    private Spec specOf(Player p) {
        String main = plugin.getSpec2Service().mainSpec(p.getUniqueId());
        return main == null ? null : Spec.fromId(main);
    }

    private boolean isAffliction(Player p) {
        return specOf(p) == Spec.AFFLICTION;
    }

    private boolean isDestruction(Player p) {
        return specOf(p) == Spec.DESTRUCTION;
    }

    private boolean isDemonology(Player p) {
        return specOf(p) == Spec.DEMONOLOGY;
    }

    private double specDamageMult(Player p) {
        return isDestruction(p)
                ? 1.0 + cfgD("classes.WARLOCK.specs.destruction.damage-mult", 0.10)
                : 1.0;
    }

    private double specRiftRadiusBonus(Player p) {
        return isAffliction(p)
                ? cfgD("classes.WARLOCK.specs.affliction.soul-rift-radius-bonus", 2.0)
                : 0.0;
    }

    private int specUnwritingStrips(Player p) {
        return isAffliction(p)
                ? cfgI("classes.WARLOCK.specs.affliction.unwriting-strips-resist", 1)
                : 0;
    }

    private double specSealDuration(Player p, double baseDuration) {
        return isDemonology(p)
                ? cfgD("classes.WARLOCK.specs.demonology.seal-duration", 86.6)
                : baseDuration;
    }

    private double specAntihealBonus(Player p) {
        return isDemonology(p)
                ? cfgD("classes.WARLOCK.specs.demonology.antiheal-bonus", 3.0)
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

    private double tspellDamage(Player caster, AbilityDef def, double defBase, double defCoeff) {
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        return (tbase(def, defBase) + sp * tcoeff(def, defCoeff)) * damageMult(caster);
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    private boolean treeUnlocked(Player p, AbilityDef def) {
        if (plugin.getSpec2Service().hasUnlocked(p.getUniqueId(), def.id())) {
            return true;
        }
        p.sendMessage(Component.text("«" + def.displayName()
                + "» откроется узлом дерева путей Чернокнижника.", NamedTextColor.GRAY));
        return false;
    }

    private void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    private void allyTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
    }

    private void paySelfCost(Player caster, double pct) {
        double maxFormula = formulaMax(caster);
        double curFormula = currentFormulaHp(caster);
        double nextFormula = WarlockMath.selfCostNewHp(maxFormula, pct, curFormula, 1.0);
        double scale = plugin.getAttributes().scale(caster);
        caster.setHealth(Math.max(1.0, nextFormula * scale));
        WarlockFx.safeFx(caster.getLocation(), Particle.SCULK_SOUL, 10, 0.4);
    }

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

    private int witherStacks(LivingEntity t) {
        int stacks = 0;
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            if (inst.def().id().equals("wither")) {
                stacks += inst.stacks();
            }
        }
        return stacks;
    }

    private int burningStacks(LivingEntity t) {
        int stacks = 0;
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            String id = inst.def().id();
            if (id.equals("burning") || id.equals("burning_passive")) {
                stacks += inst.stacks();
            }
        }
        return stacks;
    }

    /* -------------------------------- базовые способности -------------------------------- */

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
        double heal = WarlockMath.drainHeal(totalDealt, drain(def, 0.666),
                plugin.getRaskolConfig().warlockLifestealCap());
        // 1.14.1 (Волна 1): атрибуция целителя для роли HEALER
        plugin.getHpBarService().heal(caster, heal, caster);
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
        // 1.14.0 (Б11.1.2-A): per-purged через фолбэк-хелпер (переносимая slot 4)
        double perPurged = TreeAbilities.numberOrKit(plugin, PC, def.id(), "per-purged", 16.0);
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

    /** 1.11.2 (T2) + 1.11.4 + 1.13.0 (Б3): канал с задачами в CHANNEL_TASKS + CastChannels. */
    public boolean soulRift(Player caster, AbilityDef def) {
        double radius = radius(def, 8.0) + specRiftRadiusBonus(caster);
        // 1.14.0 (Б11.1.2-A): channel/antiheal/missing-hp-bonus через фолбэк-хелпер
        // (переносимая slot 5)
        double channelSec = TreeAbilities.numberOrKit(plugin, PC, def.id(), "channel", 2.5);
        double antihealSec = TreeAbilities.numberOrKit(plugin, PC, def.id(), "antiheal", 6.0)
                + specAntihealBonus(caster);
        double missingBonus = TreeAbilities.numberOrKit(plugin, PC, def.id(), "missing-hp-bonus", 0.666);
        int ticks = Math.max(1, (int) (channelSec * 20.0));

        plugin.getFx().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.9f, 0.7f);
        WarlockFx.ringFx(caster.getLocation(), radius, Particle.CRIMSON_SPORE, 2);

        List<BukkitTask> tasks = new ArrayList<>();
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
            CastChannels.unregister(caster.getUniqueId());
            CHANNEL_TASKS.remove(caster.getUniqueId());
        }, ticks + 1L);
        tasks.add(finalTask);

        CHANNEL_TASKS.put(caster.getUniqueId(), tasks);
        return true;
    }

    /* --------------------- древесные способности (1.14.0, контент-долг 6) --------------------- */

    /** affliction T2: маг-урон + DoT wither +6 Скверны. */
    public boolean withering(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        WarlockFx.safeFx(caster.getLocation(), Particle.SCULK_SOUL, 10, 0.4);
        double dmg = tspellDamage(caster, def, 8.0, 0.7);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        plugin.getCombat().dots().applyById(caster, t, "wither");
        plugin.getResources().add(caster.getUniqueId(), 6.0);
        WarlockFx.riseFx(t.getLocation(), Particle.SCULK_SOUL, 6);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 1.0f);
        return true;
    }

    /** affliction T5: дрейн: урон + хил 50% + Скверна; у цели-игрока −10 её ресурса. */
    public boolean soulSiphon(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        WarlockFx.safeFx(caster.getLocation(), Particle.SOUL, 12, 0.4);
        double dmg = tspellDamage(caster, def, 10.0, 0.8);
        double dealt = plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        if (dealt > 0.0) {
            // 1.14.1 (Волна 1): атрибуция целителя для роли HEALER
            plugin.getHpBarService().heal(caster, dealt * 0.5, caster);
        }
        if (t instanceof Player tp) {
            plugin.getResources().stateOf(tp.getUniqueId()).tickDelta(-10.0);
        }
        plugin.getResources().add(caster.getUniqueId(), 10.0);
        WarlockFx.riseFx(t.getLocation(), Particle.SOUL, 8);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.1f);
        return true;
    }

    /** affliction T6 (ульт): детонация стеков wither: урон = стеки × base; +Скверна за стек. */
    public boolean soulHarvest(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        int stacks = witherStacks(t);
        if (stacks == 0) {
            caster.sendMessage(Component.text("Жатва душ: на цели нет иссушения.", NamedTextColor.GRAY));
            return false;
        }
        double per = tspellDamage(caster, def, 10.0, 0.9);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(per * stacks), true);
        plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.SHADOW);
        plugin.getResources().add(caster.getUniqueId(), stacks * 5.0);
        WarlockFx.safeFx(t.getLocation(), Particle.SCULK_SOUL, 24, 0.6);
        WarlockFx.safeFx(t.getLocation(), Particle.SONIC_BOOM, 1, 0.0);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 0.9f);
        caster.sendMessage(Component.text("Жатва душ: стеков собрано — " + stacks, NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** destruction T2: маг-урон + горение +6 Скверны. */
    public boolean immolate(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        WarlockFx.safeFx(caster.getLocation(), Particle.FLAME, 10, 0.4);
        double dmg = tspellDamage(caster, def, 9.0, 0.8);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        plugin.getCombat().dots().applyById(caster, t, "burning");
        plugin.getResources().add(caster.getUniqueId(), 6.0);
        WarlockFx.riseFx(t.getLocation(), Particle.FLAME, 6);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.0f);
        return true;
    }

    /** destruction T4: тяжёлый маг-урон; цель получает −20% маг-резиста на 5 с. */
    public boolean chaosBolt(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        WarlockFx.safeFx(caster.getLocation(), Particle.ELECTRIC_SPARK, 14, 0.4);
        double dmg = tspellDamage(caster, def, 18.0, 1.5);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        plugin.getResists().addTimedModifier(t.getUniqueId(), "chaos_bolt", 0.0, -20.0, 5000L);
        WarlockFx.safeFx(t.getLocation(), Particle.SONIC_BOOM, 2, 0.2);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.2f);
        return true;
    }

    /** destruction T5: детонация стеков burning: урон = стеки × base; огонь сгорает. */
    public boolean conflagrate(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        int stacks = burningStacks(t);
        if (stacks == 0) {
            caster.sendMessage(Component.text("Конфлаграция: цель не горит.", NamedTextColor.GRAY));
            return false;
        }
        double per = tspellDamage(caster, def, 6.0, 0.5);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(per * stacks));
        plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.FIRE);
        plugin.getResources().add(caster.getUniqueId(), stacks * 3.0);
        WarlockFx.safeFx(t.getLocation(), Particle.FLAME, 20, 0.5);
        WarlockFx.safeFx(t.getLocation(), Particle.EXPLOSION, 1, 0.0);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.1f);
        return true;
    }

    /** demonology T2: маг-урон + горение (тёмный окрас VFX) +6 Скверны. */
    public boolean dreadfire(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        WarlockFx.safeFx(caster.getLocation(), Particle.SOUL_FIRE_FLAME, 10, 0.4);
        double dmg = tspellDamage(caster, def, 9.0, 0.8);
        plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        plugin.getCombat().dots().applyById(caster, t, "burning");
        plugin.getResources().add(caster.getUniqueId(), 6.0);
        WarlockFx.riseFx(t.getLocation(), Particle.SOUL_FIRE_FLAME, 6);
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.8f);
        return true;
    }

    /**
     * 1.14.6 (6b): demonology T4 — призыв демона через PetService;
     * +10 Скверны — китовый бонус (PetService про это не знает).
     * Смерть/выход очищают handle в PetService.onPetDeath/onOwnerQuit.
     */
    public boolean summonDemon(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        LivingEntity t = rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            noTarget(caster);
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            allyTarget(caster);
            return false;
        }
        PetService.SummonResult r = plugin.getPets().summon(caster, "demon", t);
        if (r != PetService.SummonResult.OK) {
            return r == PetService.SummonResult.ALREADY;
        }
        // Китовый бонус: +10 Скверны при призыве
        plugin.getResources().add(caster.getUniqueId(), 10.0);
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 12);
        WarlockFx.safeFx(caster.getLocation(), Particle.SCULK_SOUL, 20, 0.5);
        WarlockFx.ringFx(caster.getLocation(), 1.5, Particle.SOUL_FIRE_FLAME, 2);
        plugin.getFx().playSound(caster.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.8f, 0.7f);
        caster.sendMessage(Component.text("Демон призван на " + secs + " с (+10 Скверны)",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** demonology T5: пакт: +10% INT себе на 10 с; +8 Скверны. */
    public boolean demonicPact(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 10);
        double intNow = plugin.getAttributes().value(caster.getUniqueId(),
                dev.raskol.classes.attribute.AttributeType.INT);
        double bonus = intNow * 0.10;
        plugin.getAttributes().addTimedModifier(caster.getUniqueId(), "demonic_pact",
                0.0, 0.0, bonus, secs * 1000L);
        plugin.getResources().add(caster.getUniqueId(), 8.0);
        WarlockFx.safeFx(caster.getLocation(), Particle.SOUL_FIRE_FLAME, 18, 0.5);
        plugin.getFx().startAura(caster.getUniqueId(), Particle.SCULK_SOUL, secs * 20, 2,
                "ENTITY_WARDEN_HEARTBEAT");
        caster.sendMessage(Component.text("Демонический пакт: +" + (int) bonus + " ИНТ на " + secs + " с",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /**
     * 1.14.6 (6b): demonology T6 (ульт) — поглощение демона через PetService.consume;
     * Сила II + Сопротивление I +30 Скверны на 10 с — баффы игрока (остаются в ките).
     */
    public boolean demonSoul(Player caster, AbilityDef def) {
        if (!treeUnlocked(caster, def)) {
            return false;
        }
        UUID cid = caster.getUniqueId();
        LivingEntity demon = plugin.getPets().petOf(caster);
        if (demon == null) {
            caster.sendMessage(Component.text("Душа демона: сначала призови демона.",
                    NamedTextColor.GRAY));
            return false;
        }
        boolean consumed = plugin.getPets().consume(caster, "demon");
        if (!consumed) {
            caster.sendMessage(Component.text("Душа демона: питомец недоступен для поглощения.",
                    NamedTextColor.GRAY));
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 10);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 0));
        plugin.getResources().add(cid, 30.0);
        WarlockFx.safeFx(caster.getLocation(), Particle.SONIC_BOOM, 2, 0.1);
        WarlockFx.ringFx(caster.getLocation(), 2.0, Particle.SOUL_FIRE_FLAME, 3);
        plugin.getFx().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.9f, 0.6f);
        caster.sendMessage(Component.text("Душа демона: слияние на " + secs + " с (+30 Скверны)",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /* ------------------------------ 1.11.2 (T2): отмена на выход ------------------------------ */

    /**
     * 1.14.6 (6b): PetService.onOwnerQuit сам чистит handle пета при выходе;
     * здесь остаётся только отмена задач канала soul_rift.
     */
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

    /** 1.14.0: ids древесных способностей для сверки с TreeAbilities. */
    public static List<String> treeAbilityIds() {
        return List.of("withering", "soul_siphon", "soul_harvest",
                "immolate", "chaos_bolt", "conflagrate",
                "dreadfire", "summon_demon", "demonic_pact", "demon_soul");
    }
}
