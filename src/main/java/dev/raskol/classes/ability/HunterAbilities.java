// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.pet.PetService;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.7.2: КИТ ОХОТНИКА. 1.7.6.3: стрелы веера неподбираемы. 1.8.1: canHit-гейты.
 * 1.12.3: школа PHYSICAL из cast-контекста; VFX конфиг-драйвен.
 * 1.14.0 (Б4): хуки baseBonus/coeffMult из Spec2Service.
 * 1.14.0 (контент-долг 2): +14 древесных способностей Охотника
 *   marksmanship: aimed_shot, silencing_shot, chimera_shot, true_shot;
 *   survival: poison_shot, explosive_trap, black_arrow, wyvern_sting,
 *             readiness, serpent_sting;
 *   beastmaster: intimidation, pet_wolf, beast_ferocity, bestial_wrath.
 *   Гейт — treeUnlocked() (Spec2Service.hasUnlocked).
 * 1.14.0-fix: доступ к атрибутам через RegistryAccess API (Paper 1.21.4).
 * 1.14.0 (Б11.1.2-A): base/coeff/power/duration + radius(arrow_rain) читаются
 *   через TreeAbilities.*OrKit (treeAbilities → abilities → код-дефолт),
 *   чтобы переносимые (arrow_fan/arrow_rain) пережили резку abilities.
 * 1.14.6 (6b): питомец-волк и баффы — делегирование в PetService;
 *   static-карта PET_WOLF, livePet() и inline-спавн Wolf удалены.
 * 1.14.6-fix (Sprint 1, P0-8a): petWolf возвращает false на ЛЮБОЙ не-OK результат
 *   summon (включая ALREADY) — castOn делает refund и не запускает кулдаун.
 */
public final class HunterAbilities {

    private static final PlayerClass PC = PlayerClass.HUNTER;

    /** 1.14.0: бафф «Верный выстрел» (+30% урона способностей, 6 с). */
    private static final Map<UUID, Long> TRUE_SHOT_UNTIL = new ConcurrentHashMap<>();

    private final RaskolClasses plugin;
    private final NamespacedKey fanArrowKey;

    public HunterAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
        this.fanArrowKey = new NamespacedKey(plugin, "fan_arrow");
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
        return cfgD("classes.HUNTER.treeAbilities." + def.id() + ".base", defv);
    }

    private double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes.HUNTER.treeAbilities." + def.id() + ".coeff", defv);
    }

    private String tpower(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.HUNTER.treeAbilities." + def.id() + ".power", "wp");
    }

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        double d = plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c);
        return trueShotMult(uuid) * d;
    }

    private double tdmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        double d = plugin.getCombat().powers().abilityDamage(uuid, tpower(def), b, c);
        return trueShotMult(uuid) * d;
    }

    private double trueShotMult(UUID uuid) {
        Long until = TRUE_SHOT_UNTIL.get(uuid);
        return until != null && until > System.currentTimeMillis() ? 1.30 : 1.0;
    }

    /** 1.14.0: гейт древесной способности. */
    private boolean treeUnlocked(Player p, AbilityDef def) {
        if (plugin.getSpec2Service().hasUnlocked(p.getUniqueId(), def.id())) {
            return true;
        }
        p.sendMessage(Component.text("«" + def.displayName()
                + "» откроется узлом дерева путей Охотника.", NamedTextColor.GRAY));
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
        // 1.14.0 (Б11.1.2-A): radius через фолбэк-хелпер (переносимая slot 5)
        double radius = TreeAbilities.numberOrKit(plugin, PC, def.id(), "radius", 5.0);
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

    /* --------------------- древесные способности (1.14.0, контент-долг 2) --------------------- */

    /** marksmanship T2: тяжёлый выстрел + подсветка цели 3 с. */
    public boolean aimedShot(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 24);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "aimed_shot", "ITEM_CROSSBOW_SHOOT", "CRIT", 0.7f, 0.8f, 10);
        double dmg = tdmg(p, def, 16.0, 1.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.setGlowing(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (t.isValid()) {
                t.setGlowing(false);
            }
        }, 3 * 20L);
        impactFx(t, "aimed_shot", "ENTITY_ARROW_HIT_PLAYER", "END_ROD", 0.5f, 0.9f, 10);
        return true;
    }

    /** marksmanship T3: урон + SILENCE 3 с (CC, уходит в DR). */
    public boolean silencingShot(Player p, AbilityDef def) {
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
        castFx(p, "silencing_shot", "ENTITY_ARROW_SHOOT", "SMOKE", 0.6f, 1.0f, 8);
        double dmg = tdmg(p, def, 6.0, 0.5);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.SILENCE, 60);
        impactFx(t, "silencing_shot", "ENTITY_PLAYER_HURT", "SMOKE", 0.4f, 1.0f, 8);
        return true;
    }

    /** marksmanship T4: физ+маг урон (школа PHYSICAL/ARCANE-микс) + кровотечение. */
    public boolean chimeraShot(Player p, AbilityDef def) {
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
        castFx(p, "chimera_shot", "ENTITY_ARROW_SHOOT", "ELECTRIC_SPARK", 0.7f, 1.0f, 12);
        double half = tdmg(p, def, 8.0, 0.7);
        plugin.getCombat().dealDamage(t, p, new DamageProfile(half, half, 0.0));
        plugin.getCombat().dots().applyById(p, t, "bleed");
        impactFx(t, "chimera_shot", "ENTITY_PLAYER_HURT", "DAMAGE_INDICATOR", 0.5f, 1.0f, 10);
        return true;
    }

    /** marksmanship T5: бафф +30% урона способностей на 6 с. */
    public boolean trueShot(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 6);
        TRUE_SHOT_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
        castFx(p, "true_shot", "BLOCK_NOTE_BLOCK_PLING", "END_ROD", 0.6f, 1.2f, 16);
        p.sendMessage(Component.text("Верный выстрел: +30% урона способностей на " + secs + " с",
                NamedTextColor.GREEN));
        return true;
    }

    /** survival T2: урон + DoT poison. */
    public boolean poisonShot(Player p, AbilityDef def) {
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
        castFx(p, "poison_shot", "ENTITY_SPIDER_STEP", "COMPOSTER", 0.6f, 1.0f, 10);
        double dmg = tdmg(p, def, 6.0, 0.5);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCombat().dots().applyById(p, t, "poison");
        impactFx(t, "poison_shot", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.4f, 0.9f, 10);
        return true;
    }

    /** survival T3: заряд в точку (до 10 блоков): через 1 с AoE 3 = урон + ROOT 2 с + burning. */
    public boolean explosiveTrap(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 3.0);
        RayTraceResult hit = p.rayTraceBlocks(10.0);
        Location spot = hit != null
                ? hit.getHitPosition().toLocation(p.getWorld()).add(0.0, 0.2, 0.0)
                : p.getLocation().add(p.getLocation().getDirection().multiply(6.0));
        castFx(p, "explosive_trap", "BLOCK_TRIPWIRE_ATTACH", "SMOKE", 0.6f, 1.0f, 8);
        p.getWorld().spawnParticle(Particle.SMOKE, spot.clone().add(0.0, 0.3, 0.0), 6, 0.2, 0.2, 0.2, 0.01);
        UUID pid = p.getUniqueId();
        double dmg = tdmg(p, def, 10.0, 0.8);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player caster = plugin.getServer().getPlayer(pid);
            if (caster == null) {
                return;
            }
            caster.getWorld().spawnParticle(Particle.EXPLOSION, spot.clone().add(0.0, 0.5, 0.0),
                    2, 0.3, 0.3, 0.3, 0.0);
            Sound boom = plugin.getFx().resolveSound(
                    cfgS("vfx.explosive_trap.impact-sound", "ENTITY_GENERIC_EXPLODE"));
            if (boom != null) {
                plugin.getFx().playSound(spot, boom, 0.8f, 1.0f);
            }
            for (Entity e : caster.getWorld().getNearbyEntities(spot, radius, radius, radius)) {
                if (!(e instanceof LivingEntity t) || t.equals(caster) || t.isDead()) {
                    continue;
                }
                if (!plugin.getCombat().canHit(caster, t)) {
                    continue;
                }
                if (t.getLocation().distanceSquared(spot) > radius * radius) {
                    continue;
                }
                plugin.getCombat().dealDamage(t, caster, DamageProfile.physical(dmg));
                plugin.getCC().tryApply(caster, t, CCType.ROOT, 40);
                plugin.getCombat().dots().applyById(caster, t, "burning");
            }
        }, 20L);
        return true;
    }

    /** survival T4: урон + DoT wither (SHADOW). */
    public boolean blackArrow(Player p, AbilityDef def) {
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
        castFx(p, "black_arrow", "ENTITY_ARROW_SHOOT", "SCULK_SOUL", 0.6f, 0.9f, 10);
        double dmg = tdmg(p, def, 8.0, 0.7);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCombat().dots().applyById(p, t, "wither");
        impactFx(t, "black_arrow", "ENTITY_PLAYER_HURT", "SCULK_SOUL", 0.4f, 0.9f, 10);
        return true;
    }

    /**
     * survival T4: «Укус виверны» — урон + паралич токсином.
     * ОТКЛОНЕНИЕ от дизайн-дока: CC «сон» в модели нет → STUN 3 с.
     */
    public boolean wyvernSting(Player p, AbilityDef def) {
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
        castFx(p, "wyvern_sting", "ENTITY_SPIDER_STEP", "COMPOSTER", 0.6f, 0.8f, 10);
        double dmg = tdmg(p, def, 5.0, 0.4);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.STUN, 60);
        impactFx(t, "wyvern_sting", "ENTITY_PLAYER_HURT", "COMPOSTER", 0.4f, 0.8f, 8);
        return true;
    }

    /**
     * survival T5: «Готовность» — Концентрация → 100 + снятие с себя SLOW/ROOT.
     * ОТКЛОНЕНИЕ: сброс КД ловушек верну после расширения CooldownManager.
     */
    public boolean readiness(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        UUID uuid = p.getUniqueId();
        plugin.getResources().refund(uuid, 100.0);
        plugin.getCC().removeType(uuid, CCType.SLOW);
        plugin.getCC().removeType(uuid, CCType.ROOT);
        castFx(p, "readiness", "ENTITY_PLAYER_LEVELUP", "ENCHANTED_HIT", 0.6f, 1.2f, 16);
        p.sendMessage(Component.text("Готовность: концентрация восполнена, оковы сняты",
                NamedTextColor.GREEN));
        return true;
    }

    /** survival T6 (ульт): урон = стеки poison × base; обновляет poison. */
    public boolean serpentSting(Player p, AbilityDef def) {
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
        int stacks = 0;
        for (DotInstance inst : plugin.getCombat().dots().activeDotsOf(t.getUniqueId())) {
            String dotId = inst.def().id();
            if (dotId.equals("poison") || dotId.equals("poison_passive")) {
                stacks += inst.stacks();
            }
        }
        castFx(p, "serpent_sting", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.8f, 0.8f, 16);
        double perStack = tdmg(p, def, 12.0, 1.0);
        double dmg = stacks > 0 ? perStack * stacks : perStack * 0.5;
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCombat().dots().applyById(p, t, "poison");
        impactFx(t, "serpent_sting", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.6f, 0.8f, 14);
        p.sendMessage(Component.text("Укус змея: стеков яда учтено — " + stacks, NamedTextColor.GREEN));
        return true;
    }

    /** beastmaster T2: урон + FEAR 3 с (CC, уходит в DR). */
    public boolean intimidation(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity t = rayTarget(p, 8);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "intimidation", "ENTITY_WOLF_GROWL", "ANGRY_VILLAGER", 0.7f, 0.7f, 12);
        double dmg = tdmg(p, def, 4.0, 0.3);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.FEAR, 60);
        impactFx(t, "intimidation", "ENTITY_WOLF_GROWL", "ANGRY_VILLAGER", 0.5f, 0.7f, 10);
        return true;
    }

    /**
     * 1.14.6 (6b): beastmaster T3 — призыв волка через PetService.
     * 1.14.6-fix (P0-8a): любой не-OK результат = false (refund + без кулдауна).
     */
    public boolean petWolf(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        PetService.SummonResult r = plugin.getPets().summon(p, "wolf", null);
        if (r != PetService.SummonResult.OK) {
            return false;
        }
        castFx(p, "pet_wolf", "ENTITY_WOLF_AMBIENT", "HEART", 0.7f, 1.0f, 14);
        p.sendMessage(Component.text("Волк приручён и следует за тобой (до смерти или выхода).",
                NamedTextColor.GREEN));
        return true;
    }

    /**
     * 1.14.6 (6b): beastmaster T4 — бафф питомца через PetService
     * (dmgMult=1.5, speedMult=1.5, без подсветки, secs из конфига).
     */
    public boolean beastFerocity(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity pet = plugin.getPets().petOf(p);
        if (pet == null) {
            p.sendMessage(Component.text("Сначала призови волка («Приручить волка»).", NamedTextColor.GRAY));
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 10);
        boolean ok = plugin.getPets().buff(p, 1.5, 1.5, false, secs);
        if (!ok) {
            return false;
        }
        castFx(p, "beast_ferocity", "ENTITY_WOLF_GROWL", "CRIMSON_SPORE", 0.7f, 1.0f, 14);
        pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(0.0, 1.0, 0.0),
                6, 0.3, 0.3, 0.3, 0.0);
        return true;
    }

    /**
     * 1.14.6 (6b): beastmaster T6 (ульт) — бафф питомца через PetService
     * (dmgMult=2.0, speedMult=1.0, подсветка=true, secs из конфига).
     */
    public boolean bestialWrath(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        LivingEntity pet = plugin.getPets().petOf(p);
        if (pet == null) {
            p.sendMessage(Component.text("Сначала призови волка («Приручить волка»).", NamedTextColor.GRAY));
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 8);
        boolean ok = plugin.getPets().buff(p, 2.0, 1.0, true, secs);
        if (!ok) {
            return false;
        }
        castFx(p, "bestial_wrath", "ENTITY_WOLF_HOWL", "CRIMSON_SPORE", 0.9f, 0.7f, 20);
        p.sendMessage(Component.text("Звериная ярость: волк усилен на " + secs + " с",
                NamedTextColor.GREEN));
        return true;
    }

    /**
     * 1.14.6 (6b): оставлен как no-op для обратной совместимости.
     * PetService.onOwnerQuit сам чистит handle пета при выходе владельца.
     */
    public static void forgetPet(UUID ownerUuid) {
        // no-op: PetService owns pet lifecycle
    }

    /** 1.14.0: список активных древесных баффов (для отладки /rc debug). */
    public static boolean trueShotActive(UUID uuid) {
        Long until = TRUE_SHOT_UNTIL.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /** 1.14.0: ids древесных способностей для сверки с TreeAbilities. */
    public static List<String> treeAbilityIds() {
        return List.of("aimed_shot", "silencing_shot", "chimera_shot", "true_shot",
                "poison_shot", "explosive_trap", "black_arrow", "wyvern_sting",
                "readiness", "serpent_sting", "intimidation", "pet_wolf",
                "beast_ferocity", "bestial_wrath");
    }
}
