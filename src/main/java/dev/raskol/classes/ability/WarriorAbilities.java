// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
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

import java.util.UUID;

/**
 * 1.7.2: КИТ ВОИНА. 1.9.3 (план B): effectiveMaxHp() читает formulaMaxHp();
 * fenrirBlood() использует HpBarService.heal().
 * 1.12.3: школа PHYSICAL идёт из AbilityDef через cast-контекст AbilityRegistry;
 *         VFX (cast-sound/cast-particle/impact-sound/impact-particle) берётся
 *         из vfx.<id>.* конфига с дефолтами.
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
        return cfgD("classes.WARRIOR.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.WARRIOR.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.WARRIOR.abilities." + def.id() + ".power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.WARRIOR.abilities." + def.id() + ".duration", defv);
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

    /** 1.9.3 (план B): формульный maxHp из HpBarService. */
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

    /** Каст-VFX: звук + партикл в точке кастера (голова). */
    private void castFx(Player p, String id, String soundDef, String particleDef,
                        float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(cfgS("vfx." + id + ".cast-sound", soundDef));
        Location loc = p.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(
                cfgS("vfx." + id + ".cast-particle", particleDef));
        if (particle != null) {
            p.getWorld().spawnParticle(particle, loc, count, 0.4, 0.6, 0.4, 0.02);
        }
    }

    /** Impact-VFX: звук + партикл в точке цели. */
    private void impactFx(LivingEntity target, String id,
                          String soundDef, String particleDef,
                          float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(
                cfgS("vfx." + id + ".impact-sound", soundDef));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(
                cfgS("vfx." + id + ".impact-particle", particleDef));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, count, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Execute-VFX: отдельный звук и крупные партиклы для Рагнарёка/Казни. */
    private void executeFx(Player caster, LivingEntity target) {
        Sound sound = plugin.getFx().resolveSound(
                cfgS("vfx.ragnarok.execute-sound", "ENTITY_GENERIC_EXPLODE"));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, 0.9f, 0.7f);
        }
        Particle particle = resolveParticle(
                cfgS("vfx.ragnarok.execute-particle", "EXPLOSION"));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.3, 0.2, 0.0);
        }
    }

    /**
     * Безопасный резолв Particle по имени: неизвестное имя → null (без падения).
     * FxService не имеет универсального resolveParticle — делаем локально.
     */
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

    /* -------------------------------- способности -------------------------------- */

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
        castFx(p, "tyr_strike", "ENTITY_IRON_GOLEM_ATTACK", "SWEEP_ATTACK",
                0.7f, 0.9f, 12);
        double dmg = dmg(p, def, 10.0, 0.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        impactFx(t, "tyr_strike", "ENTITY_PLAYER_ATTACK_CRIT", "CRIT",
                0.5f, 1.0f, 10);
        return true;
    }

    public boolean balderSkin(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 15.0) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, 0.05) * plugin.getTalentService().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().weaponPower(uuid) * c;
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(uuid, def.id(), grant, 0.0, secs * 1000L);
        castFx(p, "balder_skin", "ITEM_ARMOR_EQUIP_GOLD", "ENCHANT",
                0.6f, 1.0f, 20);
        return true;
    }

    public boolean berserkergang(Player p, AbilityDef def) {
        int secs = duration(def, 6);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 0));
        castFx(p, "berserkergang", "ENTITY_RAVAGER_ROAR", "CRIMSON_SPORE",
                0.8f, 0.8f, 24);
        return true;
    }

    /** 1.9.3 (план B): heal() через HpBarService. */
    public boolean fenrirBlood(Player p, AbilityDef def) {
        double formula = plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        double scale = plugin.getHpBarService().scale(p);
        double hpFormula = scale > 0.0 ? p.getHealth() / scale : p.getHealth();
        if (hpFormula >= formula - 0.001) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        castFx(p, "fenrir_blood", "ENTITY_WOLF_AMBIENT", "CRIMSON_SPORE",
                0.6f, 1.0f, 16);
        double amount = dmg(p, def, 15.0, 0.5);
        plugin.getHpBarService().heal(p, amount);
        // heal-impact: сердечки
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
        castFx(p, "ragnarok", "ENTITY_LIGHTNING_BOLT_THUNDER", "EXPLOSION",
                0.9f, 0.7f, 8);
        double threshold = cfgD("classes.WARRIOR.abilities." + def.id() + ".threshold", 0.25);
        double max = effectiveMaxHp(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.8);
        if (frac < threshold) {
            dmg *= cfgD("classes.WARRIOR.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg), true);
            executeFx(p, t);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            impactFx(t, "ragnarok", "ENTITY_GENERIC_EXPLODE", "LARGE_SMOKE",
                    0.6f, 0.9f, 6);
        }
        return true;
    }
}
