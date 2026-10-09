// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.school.School;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.7.3: КИТ РАЗБОЙНИКА (средневековый реализм). Урон = base + WP×coeff.
 * 1.8.1: canHit-гейты. 1.12.3: школы SHADOW/PHYSICAL/NATURE, VFX конфиг-драйвен.
 * 1.12.5: яд/кровотечение = школьные DoT (DotService), ванильный POISON убран.
 * 1.14.0 (Б4): хуки baseBonus/coeffMult из Spec2Service.
 * 1.14.0 (контент-долг 3): +16 древесных способностей Разбойника:
 *   assassination: poison_burst, cold_blood, vendetta, envenom, deathmark (ульт);
 *   outlaw: pistol_shot, blade_flurry, adrenaline_rush, killing_spree,
 *           between_the_eyes (ульт);
 *   subtlety: backstab, shadowstep, preparation, cloak_of_shadows (cleanse),
 *           hemorrhage, shadow_blades (ульт).
 *   Гейт — treeUnlocked(); вендетта — статический реестр VENDETTA_EXPIRY
 *   (публичный vendettaAmplifyOf, по образцу sealAmplifyOf чернокнижника).
 * 1.14.0 (Б11.1.2-A): base/coeff/power/duration читаются через TreeAbilities.*OrKit
 *   (treeAbilities → abilities → код-дефолт), чтобы переносимые
 *   (borgia_poison/shadow_dance) пережили резку abilities. У Разбойника нет прямых
 *   cfgD("…abilities…") в методах переносимых — всё идёт через хелперы.
 * 1.14.1 (Волна 1): heal() передаёт кастера для роли HEALER (CustomHealEvent).
 * 1.14.3 (Волна 3, 3C2): refreshVendetta(UUID) — продление вендетты по proc_vendetta_refresh;
 *   shadowCloak учитывает proc_stealth_extend (+N с к длительности невидимости).
 * 1.14.7 (Sprint 3, P0-6A): kit_dur-узлы Разбойника живые: shadow_cloak
 *   (su_master_of_deception +5 с/ранг), adrenaline_rush (ol_improved_adrenaline +5 с/ранг),
 *   strangle (su_nerve_strike +1 с SLOW/ранг) — через durationWithSpec / kitDurBonus.
 */
public final class RogueAbilities {

    private static final PlayerClass PC = PlayerClass.ROGUE;

    /** 1.14.0: «Хладнокровие» — +50% урона способностей 5 с. */
    private static final Map<UUID, Long> COLD_BLOOD_UNTIL = new ConcurrentHashMap<>();
    /** 1.14.0: «Теневые клинки» — +50% урона способностей 6 с (ульт). */
    private static final Map<UUID, Long> SHADOW_BLADES_UNTIL = new ConcurrentHashMap<>();
    /** 1.14.0: «Вендетта» — цель получает +30% урона от разбойника 10 с. */
    private static final Map<UUID, Long> VENDETTA_EXPIRY = new ConcurrentHashMap<>();

    private final RaskolClasses plugin;

    public RogueAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** 1.14.0: амплификация урона по цели под «Вендеттой» (0.0 если нет). */
    public static double vendettaAmplifyOf(UUID targetUuid) {
        Long until = VENDETTA_EXPIRY.get(targetUuid);
        if (until == null || System.currentTimeMillis() > until) {
            return 0.0;
        }
        return 0.30;
    }

    /**
     * 1.14.3 (3C2): продление активной вендетты на цели (proc_vendetta_refresh).
     * Вызывается из ProcService.rollCritProcs при крит-проке. Если вендетты нет
     * или она истекла — no-op (прок не создаёт новую метку, только освежает).
     */
    public static void refreshVendetta(UUID targetUuid) {
        Long until = VENDETTA_EXPIRY.get(targetUuid);
        if (until == null || System.currentTimeMillis() > until) {
            return;
        }
        VENDETTA_EXPIRY.put(targetUuid, System.currentTimeMillis() + 10_000L);
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
        return TreeAbilities.stringOrKit(plugin, PC, def.id(), "power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        return TreeAbilities.durationOrKit(plugin, PC, def.id(), defv);
    }

    private double tbase(AbilityDef def, double defv) {
        return cfgD("classes.ROGUE.treeAbilities." + def.id() + ".base", defv);
    }

    private double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes.ROGUE.treeAbilities." + def.id() + ".coeff", defv);
    }

    private String tpower(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.ROGUE.treeAbilities." + def.id() + ".power", "wp");
    }

    /* ------------------------------ урон с баффами и вендеттой ------------------------------ */

    private double buffMult(UUID uuid) {
        long now = System.currentTimeMillis();
        double m = 1.0;
        Long cb = COLD_BLOOD_UNTIL.get(uuid);
        if (cb != null && cb > now) {
            m *= 1.5;
        }
        Long sb = SHADOW_BLADES_UNTIL.get(uuid);
        if (sb != null && sb > now) {
            m *= 1.5;
        }
        return m;
    }

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c) * buffMult(uuid);
    }

    private double tdmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, tpower(def), b, c) * buffMult(uuid);
    }

    /** Урон с учётом «Вендетты» по цели (+30%). */
    private double deal(Player p, LivingEntity t, DamageProfile prof) {
        double mult = 1.0 + vendettaAmplifyOf(t.getUniqueId());
        if (mult != 1.0) {
            return plugin.getCombat().dealDamage(t, p, new DamageProfile(
                    prof.physical() * mult, prof.magic() * mult, prof.trueDamage()));
        }
        return plugin.getCombat().dealDamage(t, p, prof);
    }

    private boolean treeUnlocked(Player p, AbilityDef def) {
        if (plugin.getSpec2Service().hasUnlocked(p.getUniqueId(), def.id())) {
            return true;
        }
        p.sendMessage(Component.text("«" + def.displayName()
                + "» откроется узлом дерева путей Разбойника.", NamedTextColor.GRAY));
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

    private boolean isBehind(LivingEntity target, Player attacker) {
        Vector facing = target.getLocation().getDirection();
        Vector toAtt = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        double angle = AttributeMath.angleToAttacker(
                facing.getX(), facing.getZ(), toAtt.getX(), toAtt.getZ());
        return AttributeMath.isBack(angle,
                plugin.getConfig().getDouble("avoidance.back-angle", 135.0));
    }

    private int poisonStacks(LivingEntity t) {
        int stacks = 0;
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            String id = inst.def().id();
            if (id.equals("poison") || id.equals("poison_passive")) {
                stacks += inst.stacks();
            }
        }
        return stacks;
    }

    private int bleedStacks(LivingEntity t) {
        int stacks = 0;
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            String id = inst.def().id();
            if (id.equals("bleed") || id.equals("bleed_passive")) {
                stacks += inst.stacks();
            }
        }
        return stacks;
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

    /** 1.14.7 (P0-6A): длительность невидимости через durationWithSpec (su_master_of_deception). */
    public boolean shadowCloak(Player p, AbilityDef def) {
        int secs = TreeAbilities.durationWithSpec(plugin, PC, def.id(), 15, p.getUniqueId());
        // 1.14.3 (3C2): proc_stealth_extend — +N с к длительности невидимости за ранг
        double extend = plugin.getCombat().procs().getStealthExtendBonus(p);
        int totalSecs = secs + (int) Math.round(Math.max(0.0, extend));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, totalSecs * 20, 0));
        castFx(p, "shadow_cloak", "ENTITY_PHANTOM_FLAP", "SMOKE", 0.5f, 0.9f, 16);
        impactFx(p, "shadow_cloak", "ENTITY_ENDERMAN_TELEPORT", "SMOKE", 0.4f, 1.2f, 12);
        return true;
    }

    public boolean bladeFan(Player p, AbilityDef def) {
        double radius = cfgD("classes.ROGUE.abilities." + def.id() + ".radius", 3.0);
        castFx(p, "blade_fan", "ENTITY_PLAYER_ATTACK_SWEEP", "SWEEP_ATTACK", 0.6f, 1.0f, 12);
        double dmg = dmg(p, def, 8.0, 0.8);
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
            deal(p, t, DamageProfile.physical(dmg));
            impactFx(t, "blade_fan", "ENTITY_PLAYER_HURT", "DAMAGE_INDICATOR", 0.4f, 1.0f, 8);
            hit = true;
        }
        return hit;
    }

    /** 1.14.7 (P0-6A): su_nerve_strike — +1 с SLOW за ранг (kit_dur strangle). */
    public boolean strangle(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "strangle", "ENTITY_PLAYER_ATTACK_WEAK", "DAMAGE_INDICATOR", 0.5f, 0.9f, 8);
        double dmg = dmg(p, def, 10.0, 0.9);
        deal(p, t, DamageProfile.physical(dmg));
        impactFx(t, "strangle", "ENTITY_PLAYER_HURT", "SMOKE", 0.4f, 0.8f, 10);
        int slowSecs = 2 + (int) Math.round(
                plugin.getSpec2Service().kitDurBonus(p.getUniqueId(), "strangle"));
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowSecs * 20, 1));
        plugin.getCombat().dots().applyById(p, t, "bleed");
        return true;
    }

    public boolean borgiaPoison(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "borgia_poison", "ENTITY_SPIDER_STEP", "COMPOSTER", 0.5f, 1.0f, 10);
        double dmg = dmg(p, def, 5.0, 0.3);
        deal(p, t, DamageProfile.physical(dmg));
        impactFx(t, "borgia_poison", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.4f, 0.9f, 12);
        plugin.getCombat().dots().applyById(p, t, "poison");
        return true;
    }

    public boolean shadowDance(Player p, AbilityDef def) {
        double agiBonus = base(def, 30.0);
        int secs = duration(def, 4);
        castFx(p, "shadow_dance", "ENTITY_ENDERMAN_TELEPORT", "CLOUD", 0.6f, 1.1f, 20);
        plugin.getAttributes().addTimedModifier(
                p.getUniqueId(), def.id(), 0.0, agiBonus, 0.0, secs * 1000L);
        impactFx(p, "shadow_dance", "ENTITY_ENDERMAN_TELEPORT", "CLOUD", 0.4f, 1.3f, 16);
        return true;
    }

    /* --------------------- древесные способности (1.14.0, контент-долг 3) --------------------- */

    /** assassination T2: детонация стеков яда (урон = стеки × base, яд сгорает). */
    public boolean poisonBurst(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        int stacks = poisonStacks(t);
        if (stacks == 0) {
            p.sendMessage(Component.text("Взрыв яда: на цели нет яда.", NamedTextColor.GRAY));
            return false;
        }
        castFx(p, "poison_burst", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.7f, 0.9f, 14);
        double per = tdmg(p, def, 5.0, 0.4);
        deal(p, t, DamageProfile.physical(per * stacks));
        plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.NATURE);
        impactFx(t, "poison_burst", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.5f, 0.9f, 16);
        p.sendMessage(Component.text("Взрыв яда: стеков детонировано — " + stacks, NamedTextColor.GREEN));
        return true;
    }

    /** assassination T3: +50% урона способностей на 5 с. */
    public boolean coldBlood(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 5);
        COLD_BLOOD_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
        castFx(p, "cold_blood", "BLOCK_NOTE_BLOCK_PLING", "ENCHANTED_HIT", 0.6f, 1.2f, 14);
        p.sendMessage(Component.text("Хладнокровие: +50% урона способностей на " + secs + " с",
                NamedTextColor.AQUA));
        return true;
    }

    /** assassination T4: метка цели — +30% урона от разбойника на 10 с. */
    public boolean vendetta(Player p, AbilityDef def) {
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
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 10);
        VENDETTA_EXPIRY.put(t.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
        t.setGlowing(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (t.isValid()) {
                t.setGlowing(false);
            }
        }, secs * 20L);
        castFx(p, "vendetta", "ENTITY_ENDERMAN_STARE", "SCULK_SOUL", 0.7f, 0.8f, 14);
        p.sendMessage(Component.text("Вендетта: цель отмечена на " + secs + " с (+30% урона)",
                NamedTextColor.RED));
        return true;
    }

    /** assassination T5: урон + хил по стекам яда (не сжигает их). */
    public boolean envenom(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        int stacks = poisonStacks(t);
        castFx(p, "envenom", "ENTITY_SPIDER_STEP", "COMPOSTER", 0.6f, 1.0f, 10);
        double dmg = tdmg(p, def, 8.0, 0.6) + stacks * 4.0;
        double dealt = deal(p, t, DamageProfile.physical(dmg));
        plugin.getCombat().dots().applyById(p, t, "poison");
        if (dealt > 0.0) {
            // 1.14.1 (Волна 1): атрибуция целителя для роли HEALER
            plugin.getHpBarService().heal(p, dealt * 0.20, p);
        }
        impactFx(t, "envenom", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.4f, 1.0f, 10);
        return true;
    }

    /** assassination T6 (ульт): execute по цели с ≥3 стеками яда (×3, игнор капов). */
    public boolean deathmark(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        int stacks = poisonStacks(t);
        castFx(p, "deathmark", "ENTITY_WITHER_SPAWN", "SCULK_SOUL", 0.9f, 0.7f, 20);
        double dmg = tdmg(p, def, 15.0, 1.2);
        if (stacks >= 3) {
            deal(p, t, DamageProfile.physical(dmg * 3.0));
            plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.NATURE);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            deal(p, t, DamageProfile.physical(dmg));
            plugin.getCombat().dots().applyById(p, t, "poison");
        }
        impactFx(t, "deathmark", "ENTITY_WITHER_HURT", "SCULK_SOUL", 0.6f, 0.8f, 16);
        return true;
    }

    /** outlaw T2: урон + Оцепенение 1.5 с (CC, уходит в DR). */
    public boolean pistolShot(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 12);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "pistol_shot", "ENTITY_FIREWORK_ROCKET_BLAST", "SMOKE", 0.7f, 1.0f, 10);
        double dmg = tdmg(p, def, 6.0, 0.5);
        deal(p, t, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.STUN, 30);
        impactFx(t, "pistol_shot", "ENTITY_PLAYER_HURT", "SMOKE", 0.5f, 1.0f, 8);
        return true;
    }

    /**
     * outlaw T3: «Шквал клинков».
     * ОТКЛОНЕНИЕ: мгновенное AoE радиус 4 + кровотечение всем целям
     * (вместо 10-секундного баффа дополнительных целей).
     */
    public boolean bladeFlurry(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 4.0);
        castFx(p, "blade_flurry", "ENTITY_PLAYER_ATTACK_SWEEP", "SWEEP_ATTACK", 0.7f, 1.1f, 16);
        double dmg = tdmg(p, def, 7.0, 0.6);
        int hits = 0;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || e.equals(p) || t.isDead()) {
                continue;
            }
            if (!Targeting.isValidDamageTarget(plugin, p, t)) {
                continue;
            }
            deal(p, t, DamageProfile.physical(dmg));
            plugin.getCombat().dots().applyById(p, t, "bleed");
            impactFx(t, "blade_flurry", "ENTITY_PLAYER_HURT", "DAMAGE_INDICATOR", 0.4f, 1.0f, 6);
            hits++;
        }
        if (hits == 0) {
            p.sendMessage(Component.text("Шквал клинков: целей рядом нет.", NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    /** outlaw T3: «Прилив адреналина» — Энергия +50 и Спех II. 1.14.7: durationWithSpec. */
    public boolean adrenalineRush(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationWithSpec(plugin, PC, def.id(), 10, p.getUniqueId());
        plugin.getResources().refund(p.getUniqueId(), 50.0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, secs * 20, 1));
        castFx(p, "adrenaline_rush", "ENTITY_PLAYER_LEVELUP", "CRIMSON_SPORE", 0.7f, 1.2f, 16);
        p.sendMessage(Component.text("Прилив адреналина: +50 энергии, темп на " + secs + " с",
                NamedTextColor.GREEN));
        return true;
    }

    /**
     * outlaw T4: «Серия убийств» — 5 быстрых ударов по цели, каждый +8% урона.
     * ОТКЛОНЕНИЕ: без телепортов между целями (серия по одной цели).
     */
    public boolean killingSpree(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "killing_spree", "ENTITY_PLAYER_ATTACK_SWEEP", "CRIT", 0.8f, 1.1f, 14);
        double dmg = tdmg(p, def, 4.0, 0.35);
        for (int i = 0; i < 5; i++) {
            if (t.isDead()) {
                break;
            }
            deal(p, t, DamageProfile.physical(dmg * (1.0 + 0.08 * i)));
            impactFx(t, "killing_spree", "ENTITY_PLAYER_HURT", "CRIT", 0.35f, 1.0f + i * 0.08f, 5);
        }
        return true;
    }

    /** outlaw T6 (ульт): урон + Оцепенение 3 с по цели с кровотечением, иначе 1.5 с. */
    public boolean betweenTheEyes(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        boolean bleeding = bleedStacks(t) > 0;
        castFx(p, "between_the_eyes", "ENTITY_PLAYER_ATTACK_CRIT", "DAMAGE_INDICATOR", 0.8f, 0.8f, 14);
        double dmg = tdmg(p, def, 10.0, 0.9);
        deal(p, t, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.STUN, bleeding ? 60 : 30);
        impactFx(t, "between_the_eyes", "ENTITY_PLAYER_HURT", "DAMAGE_INDICATOR", 0.6f, 0.8f, 12);
        if (bleeding) {
            p.sendMessage(Component.text("Между глаз: цель истекает кровью — оцепенение 3 с",
                    NamedTextColor.RED));
        }
        return true;
    }

    /** subtlety T2: урон ×1.5 со спины. */
    public boolean backstab(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        boolean behind = isBehind(t, p);
        castFx(p, "backstab", "ENTITY_PLAYER_ATTACK_WEAK", "SMOKE", 0.6f, 1.0f, 10);
        double dmg = tdmg(p, def, 8.0, 0.7) * (behind ? 1.5 : 1.0);
        deal(p, t, DamageProfile.physical(dmg));
        impactFx(t, "backstab", "ENTITY_PLAYER_HURT", "CRIT", 0.4f, 0.9f, 8);
        if (behind) {
            p.sendMessage(Component.text("Удар в спину: ×1.5", NamedTextColor.LIGHT_PURPLE));
        }
        return true;
    }

    /** subtlety T3: телепорт за спину цели (до 20 блоков) + Скорость I 3 с. */
    public boolean shadowstep(Player p, AbilityDef def) {
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
        Vector facing = t.getLocation().getDirection().setY(0).normalize();
        Location dest = t.getLocation().add(facing.multiply(1.2));
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        if (!dest.getBlock().isPassable()) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "blink-unsafe", "Скачок невозможен: нет безопасной точки"), NamedTextColor.GRAY));
            return false;
        }
        castFx(p, "shadowstep", "ENTITY_ENDERMAN_TELEPORT", "REVERSE_PORTAL", 0.6f, 1.2f, 14);
        p.teleport(dest);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 0));
        impactFx(t, "shadowstep", "ENTITY_ENDERMAN_TELEPORT", "REVERSE_PORTAL", 0.4f, 1.2f, 10);
        return true;
    }

    /**
     * subtlety T3: «Подготовка».
     * ОТКЛОНЕНИЕ (как readiness у охотника): Энергия → 100 + снятие с себя SLOW/ROOT;
     * сброс кулдаунов верну после расширения CooldownManager.
     */
    public boolean preparation(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        UUID uuid = p.getUniqueId();
        plugin.getResources().refund(uuid, 100.0);
        plugin.getCC().removeType(uuid, CCType.SLOW);
        plugin.getCC().removeType(uuid, CCType.ROOT);
        castFx(p, "preparation", "ENTITY_PLAYER_LEVELUP", "ENCHANTED_HIT", 0.6f, 1.2f, 16);
        p.sendMessage(Component.text("Подготовка: энергия восполнена, оковы сняты",
                NamedTextColor.GREEN));
        return true;
    }

    /** subtlety T4: cleanse — снять с себя все CC и DoT NATURE/SHADOW + Невидимость 3 с. */
    public boolean cloakOfShadows(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        UUID uuid = p.getUniqueId();
        plugin.getCC().removeAll(uuid);
        plugin.getCombat().dots().removeSchoolOn(uuid, School.NATURE);
        plugin.getCombat().dots().removeSchoolOn(uuid, School.SHADOW);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 3 * 20, 0));
        castFx(p, "cloak_of_shadows", "ENTITY_PHANTOM_FLAP", "REVERSE_PORTAL", 0.7f, 1.0f, 20);
        p.sendMessage(Component.text("Плащ теней: оковы и проклятия сняты, ты в тени",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** subtlety T5: урон + кровотечение 8 с (стеки dots.bleed). */
    public boolean hemorrhage(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "hemorrhage", "ENTITY_PLAYER_ATTACK_WEAK", "DAMAGE_INDICATOR", 0.6f, 0.9f, 10);
        double dmg = tdmg(p, def, 7.0, 0.6);
        deal(p, t, DamageProfile.physical(dmg));
        plugin.getCombat().dots().applyById(p, t, "bleed");
        impactFx(t, "hemorrhage", "ENTITY_PLAYER_HURT", "DAMAGE_INDICATOR", 0.4f, 0.9f, 10);
        return true;
    }

    /** subtlety T6 (ульт): «Теневые клинки» — +50% урона способностей 6 с. */
    public boolean shadowBlades(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 6);
        SHADOW_BLADES_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
        castFx(p, "shadow_blades", "ENTITY_ENDERMAN_TELEPORT", "SCULK_SOUL", 0.9f, 0.8f, 22);
        p.sendMessage(Component.text("Теневые клинки: +50% урона способностей на " + secs + " с",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** 1.14.0: ids древесных способностей для сверки с TreeAbilities. */
    public static List<String> treeAbilityIds() {
        return List.of("poison_burst", "cold_blood", "vendetta", "envenom", "deathmark",
                "pistol_shot", "blade_flurry", "adrenaline_rush", "killing_spree",
                "between_the_eyes", "backstab", "shadowstep", "preparation",
                "cloak_of_shadows", "hemorrhage", "shadow_blades");
    }
}
