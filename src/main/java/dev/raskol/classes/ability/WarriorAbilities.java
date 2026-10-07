// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * 1.7.2: КИТ ВОИНА. 1.9.3 (план B): effectiveMaxHp() читает formulaMaxHp();
 * fenrirBlood() использует HpBarService.heal().
 * 1.12.3: школа PHYSICAL из AbilityDef через cast-контекст; VFX конфиг-драйвен.
 * 1.14.0 (Б4): хуки baseBonus/coeffMult из Spec2Service.
 * 1.14.0 (контент-долг 1): +9 древесных способностей Воина
 *         (whirlwind_slash/mortal_strike/bloodthirst/rampage/concussive_blow/
 *         shield_bash/taunt/bladestorm/last_stand) с гейтом hasUnlocked.
 * 1.14.1 (Волна 1): heal() передаёт кастера для роли HEALER (CustomHealEvent).
 */
public final class WarriorAbilities {

    private static final PlayerClass PC = PlayerClass.WARRIOR;
    private final RaskolClasses plugin;

    public WarriorAbilities(RaskolClasses plugin) {
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
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "coeff", defv);
    }

    /** База/коэф для древесных способностей (секция treeAbilities). */
    private double tbase(AbilityDef def, double defv) {
        return cfgD("classes.WARRIOR.treeAbilities." + def.id() + ".base", defv);
    }

    private double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes.WARRIOR.treeAbilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return TreeAbilities.stringOrKit(plugin, PC, def.id(), "power", "wp");
    }

    private String tpower(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.WARRIOR.treeAbilities." + def.id() + ".power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        return TreeAbilities.durationOrKit(plugin, PC, def.id(), defv);
    }

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c);
    }

    private double tdmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, tpower(def), b, c);
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

    /** 1.14.0: гейт древесной способности (узел дерева путей ранг ≥1). */
    private boolean treeUnlocked(Player p, AbilityDef def) {
        if (plugin.getSpec2Service().hasUnlocked(p.getUniqueId(), def.id())) {
            return true;
        }
        p.sendMessage(Component.text("«" + def.displayName()
                + "» откроется узлом дерева путей Воина.", NamedTextColor.GRAY));
        return false;
    }

    private double effectiveMaxHp(LivingEntity target) {
        if (target instanceof Player p) {
            return plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        }
        org.bukkit.attribute.AttributeInstance ai = target.getAttribute(
                io.papermc.paper.registry.RegistryAccess.registryAccess()
                        .getRegistry(io.papermc.paper.registry.RegistryKey.ATTRIBUTE)
                        .get(org.bukkit.NamespacedKey.minecraft("max_health")));
        return ai != null ? ai.getValue() : 20.0;
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

    private void executeFx(Player caster, LivingEntity target) {
        Sound sound = plugin.getFx().resolveSound(
                cfgS("vfx.ragnarok.execute-sound", "ENTITY_GENERIC_EXPLODE"));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, 0.9f, 0.7f);
        }
        Particle particle = resolveParticle(cfgS("vfx.ragnarok.execute-particle", "EXPLOSION"));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.3, 0.2, 0.0);
        }
    }

    private Particle resolveParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /* -------------------------------- базовые способности -------------------------------- */

    public boolean tyrStrike(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "tyr_strike", "ENTITY_IRON_GOLEM_ATTACK", "SWEEP_ATTACK", 0.7f, 0.9f, 12);
        double dmg = dmg(p, def, 10.0, 0.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        impactFx(t, "tyr_strike", "ENTITY_PLAYER_ATTACK_CRIT", "CRIT", 0.5f, 1.0f, 10);
        return true;
    }

    public boolean balderSkin(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 15.0) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, 0.05) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().weaponPower(uuid) * c;
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(uuid, def.id(), grant, 0.0, secs * 1000L);
        castFx(p, "balder_skin", "ITEM_ARMOR_EQUIP_GOLD", "ENCHANT", 0.6f, 1.0f, 20);
        return true;
    }

    public boolean berserkergang(Player p, AbilityDef def) {
        int secs = duration(def, 6);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 0));
        castFx(p, "berserkergang", "ENTITY_RAVAGER_ROAR", "CRIMSON_SPORE", 0.8f, 0.8f, 24);
        return true;
    }

    public boolean fenrirBlood(Player p, AbilityDef def) {
        double formula = plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        double scale = plugin.getHpBarService().scale(p);
        double hpFormula = scale > 0.0 ? p.getHealth() / scale : p.getHealth();
        if (hpFormula >= formula - 0.001) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        castFx(p, "fenrir_blood", "ENTITY_WOLF_AMBIENT", "CRIMSON_SPORE", 0.6f, 1.0f, 16);
        double amount = dmg(p, def, 15.0, 0.5);
        // 1.14.1 (Волна 1): атрибуция целителя для роли HEALER
        plugin.getHpBarService().heal(p, amount, p);
        Sound healSound = plugin.getFx().resolveSound(
                cfgS("vfx.fenrir_blood.impact-sound", "ENTITY_PLAYER_LEVELUP"));
        Location loc = p.getLocation().add(0.0, 1.0, 0.0);
        if (healSound != null) {
            plugin.getFx().playSound(loc, healSound, 0.5f, 1.2f);
        }
        p.getWorld().spawnParticle(Particle.HEART, loc, 8, 0.3, 0.4, 0.3, 0.0);
        return true;
    }

    public boolean ragnarok(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "ragnarok", "ENTITY_LIGHTNING_BOLT_THUNDER", "EXPLOSION", 0.9f, 0.7f, 8);
        double threshold = TreeAbilities.numberOrKit(plugin, PC, def.id(), "threshold", 0.25);
        double max = effectiveMaxHp(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.8);
        if (frac < threshold) {
            dmg *= TreeAbilities.numberOrKit(plugin, PC, def.id(), "execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg), true);
            executeFx(p, t);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            impactFx(t, "ragnarok", "ENTITY_GENERIC_EXPLODE", "LARGE_SMOKE", 0.6f, 0.9f, 6);
        }
        return true;
    }

    /* --------------------- древесные способности (1.14.0, контент-долг 1) --------------------- */

    /** arms T3: секторный физ-урон 60°, радиус из конфига (дефолт 4). */
    public boolean whirlwindSlash(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 4.0);
        castFx(p, "whirlwind_slash", "ENTITY_PLAYER_ATTACK_SWEEP", "SWEEP_ATTACK", 0.7f, 1.0f, 16);
        Vector facing = p.getLocation().getDirection().setY(0).normalize();
        double dmg = tdmg(p, def, 8.0, 0.7);
        int hits = 0;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || t.equals(p) || t.isDead()) {
                continue;
            }
            if (!plugin.getCombat().canHit(p, t)) {
                continue;
            }
            Vector to = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 1e-6) {
                continue;
            }
            to.normalize();
            if (facing.angle(to) > Math.toRadians(30.0)) {
                continue; // вне сектора 60°
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            impactFx(t, "whirlwind_slash", "ENTITY_PLAYER_HURT", "CRIT", 0.4f, 1.0f, 6);
            hits++;
        }
        if (hits == 0) {
            p.sendMessage(Component.text("Вихревой удар: целей в секторе нет", NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    /** arms T4: физ-урон + анти-хил 3 с (реестр WarlockAbilities). */
    public boolean mortalStrike(Player p, AbilityDef def) {
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
        castFx(p, "mortal_strike", "ENTITY_PLAYER_ATTACK_CRIT", "DAMAGE_INDICATOR", 0.7f, 0.9f, 12);
        double dmg = tdmg(p, def, 14.0, 1.1);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        dev.raskol.classes.ability.WarlockAbilities.addAntiheal(t.getUniqueId(),
                TreeAbilities.durationOf(plugin, PC, def.id(), 3));
        impactFx(t, "mortal_strike", "ENTITY_PLAYER_HURT", "LARGE_SMOKE", 0.5f, 0.9f, 8);
        return true;
    }

    /** fury T3: физ-урон + хил 15% от дошедшего. */
    public boolean bloodthirst(Player p, AbilityDef def) {
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
        castFx(p, "bloodthirst", "ENTITY_PLAYER_ATTACK_STRONG", "CRIMSON_SPORE", 0.6f, 1.0f, 10);
        double dmg = tdmg(p, def, 10.0, 0.9);
        double dealt = plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        double heal = dealt * 0.15;
        if (heal > 0.0) {
            // 1.14.1 (Волна 1): атрибуция целителя для роли HEALER
            plugin.getHpBarService().heal(p, heal, p);
            p.getWorld().spawnParticle(Particle.HEART,
                    p.getLocation().add(0.0, 1.2, 0.0), 4, 0.2, 0.3, 0.2, 0.0);
        }
        impactFx(t, "bloodthirst", "ENTITY_PLAYER_HURT", "CRIMSON_SPORE", 0.4f, 1.0f, 8);
        return true;
    }

    /** fury T4: серия из 3 ударов, каждый +10% урона. */
    public boolean rampage(Player p, AbilityDef def) {
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
        castFx(p, "rampage", "ENTITY_PLAYER_ATTACK_SWEEP", "SWEEP_ATTACK", 0.7f, 1.1f, 12);
        double dmg = tdmg(p, def, 6.0, 0.5);
        for (int i = 0; i < 3; i++) {
            if (t.isDead()) {
                break;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg * (1.0 + 0.10 * i)));
            impactFx(t, "rampage", "ENTITY_PLAYER_HURT", "CRIT", 0.35f, 1.0f + i * 0.1f, 5);
        }
        return true;
    }

    /** fury T3: урон + STUN 1.5 с (CCService, категория STUN → DR работает). */
    public boolean concussiveBlow(Player p, AbilityDef def) {
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
        castFx(p, "concussive_blow", "ENTITY_PLAYER_ATTACK_STRONG", "CRIT", 0.7f, 0.8f, 10);
        double dmg = tdmg(p, def, 5.0, 0.4);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.STUN, 30); // 1.5 с
        impactFx(t, "concussive_blow", "BLOCK_ANVIL_LAND", "CRIT", 0.5f, 0.9f, 8);
        return true;
    }

    /** guard T2: урон + STUN 2 с. */
    public boolean shieldBash(Player p, AbilityDef def) {
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
        castFx(p, "shield_bash", "ENTITY_PLAYER_ATTACK_KNOCKBACK", "SWEEP_ATTACK", 0.7f, 0.9f, 10);
        double dmg = tdmg(p, def, 4.0, 0.3);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        plugin.getCC().tryApply(p, t, CCType.STUN, 40); // 2 с
        impactFx(t, "shield_bash", "ENTITY_SHIELD_BLOCK", "DAMAGE_INDICATOR", 0.5f, 0.9f, 8);
        return true;
    }

    /** guard T3: ROOT-агро: мобы в радиусе 6 таргетят воина + подсветка 5 с. */
    public boolean taunt(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 6.0);
        castFx(p, "taunt", "ENTITY_IRON_GOLEM_ROAR", "ANGRY_VILLAGER", 0.8f, 0.8f, 20);
        int taunted = 0;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Mob mob) || mob.isDead()) {
                continue;
            }
            if (!plugin.getCombat().canHit(p, mob)) {
                continue;
            }
            mob.setTarget(p);
            mob.setGlowing(true);
            taunted++;
        }
        if (taunted > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Entity e : p.getNearbyEntities(radius + 8, radius + 8, radius + 8)) {
                    if (e instanceof Mob mob && mob.isGlowing()) {
                        mob.setGlowing(false);
                    }
                }
            }, 5 * 20L);
        }
        p.sendMessage(Component.text("Вызов брошен: мобов — " + taunted, NamedTextColor.GREEN));
        return taunted > 0;
    }

    /** fury T6 (ульт): AoE-вихрь 4 с: 4 тика урона по радиусу 4. */
    public boolean bladestorm(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 4.0);
        double dmg = tdmg(p, def, 7.0, 0.6);
        UUID pid = p.getUniqueId();
        castFx(p, "bladestorm", "ENTITY_RAVAGER_ROAR", "SWEEP_ATTACK", 0.9f, 0.9f, 24);
        for (int tick = 0; tick < 4; tick++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player caster = plugin.getServer().getPlayer(pid);
                if (caster == null || !caster.isOnline() || caster.isDead()) {
                    return;
                }
                caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                        caster.getLocation().add(0.0, 1.0, 0.0), 12, radius * 0.6, 0.4, radius * 0.6, 0.02);
                for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
                    if (!(e instanceof LivingEntity t) || t.equals(caster) || t.isDead()) {
                        continue;
                    }
                    if (!plugin.getCombat().canHit(caster, t)) {
                        continue;
                    }
                    plugin.getCombat().dealDamage(t, caster, DamageProfile.physical(dmg));
                }
            }, tick * 20L);
        }
        return true;
    }

    /** guard T6 (ульт): щит-пул 25% formula-maxHP на 8 с через Absorption-сердца. */
    public boolean lastStand(Player p, AbilityDef def) {
        if (!treeUnlocked(p, def)) {
            return false;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 8);
        double formula = plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        double scale = plugin.getHpBarService().scale(p);
        double shieldFormula = formula * 0.25;
        double shieldCarrier = shieldFormula * scale;
        int absorptionHp = (int) Math.max(4.0, Math.round(shieldCarrier));
        int level = Math.max(0, (absorptionHp + 3) / 4 - 1); // 4 HP-сердца за уровень
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, secs * 20, level));
        castFx(p, "last_stand", "BLOCK_ANVIL_LAND", "ENCHANTED_HIT", 0.9f, 0.7f, 28);
        p.sendMessage(Component.text("Последний рубеж: щит " + absorptionHp + " HP на " + secs + " с",
                NamedTextColor.GREEN));
        return true;
    }
}
