// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * 1.14.8 (Sprint 5, P2-3): ОБЩИЙ ФУНДАМЕНТ шести китов способностей.
 * До этого каждый кит (Warrior/Hunter/Rogue/Mage/Priest/Warlock) держал собственную
 * копию конфиг-хелперов, чисел с фолбэком TreeAbilities.*OrKit, уронного ядра
 * с spec2-хуками baseBonus/coeffMult, таргетинга и VFX — ~120 строк дублей на кит.
 *
 * Контракт базы:
 *  - числа способности: base/coeff/power/duration читаются с фолбэком
 *    treeAbilities → abilities → код-дефолт (1.14.0 Б11.1.2-A), t-варианты читают
 *    только секцию treeAbilities (древесные способности);
 *  - урон: abilityDamage(uuid, power, base + baseBonus, coeff × coeffMult) × postMult;
 *    postMult — hook классового бафф-множителя (Rogue buffMult, Mage combustion,
 *    Hunter trueShot); по умолчанию 1.0;
 *  - лечение: abilityHeal с теми же spec2-хуками (атрибуция целителя — на вызывающем,
 *    HpBarService.heal(target, amount, caster));
 *  - таргетинг и сообщения об отказе — единые для всех китов;
 *  - VFX конфиг-драйвен (vfx.<id>.cast-*/impact-*), resolveParticle безопасен к мусору.
 *
 * База НЕ знает про: CC-гейты (AbilityRegistry.castOn, P1-6), treeUnlocked-гейт
 * (удалён в P1-6), классовые реестры баффов (static-карты остаются в китах).
 */
public abstract class AbstractKit {

    protected final RaskolClasses plugin;
    protected final PlayerClass pc;
    private final String defaultPower;

    protected AbstractKit(RaskolClasses plugin, PlayerClass pc, String defaultPower) {
        this.plugin = plugin;
        this.pc = pc;
        this.defaultPower = defaultPower;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    protected double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    protected String cfgS(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null ? v : def;
    }

    protected int cfgI(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    /* --------------------- числа способности (фолбэк 1.14.0 Б11.1.2-A) --------------------- */

    protected double base(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, pc, def.id(), "base", defv);
    }

    protected double coeff(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, pc, def.id(), "coeff", defv);
    }

    protected String power(AbilityDef def) {
        return TreeAbilities.stringOrKit(plugin, pc, def.id(), "power", defaultPower);
    }

    protected int duration(AbilityDef def, int defv) {
        return TreeAbilities.durationOrKit(plugin, pc, def.id(), defv);
    }

    /** Древесные числа: только секция classes.<PC>.treeAbilities.<id>.* */
    protected double tbase(AbilityDef def, double defv) {
        return cfgD("classes." + pc.name() + ".treeAbilities." + def.id() + ".base", defv);
    }

    protected double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes." + pc.name() + ".treeAbilities." + def.id() + ".coeff", defv);
    }

    protected String tpower(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes." + pc.name() + ".treeAbilities." + def.id() + ".power", defaultPower);
    }

    /* ------------------------------ урон / лечение ------------------------------ */

    /**
     * Классовый бафф-множитель урона способностей (hook).
     * По умолчанию 1.0; киты с временными баффами переопределяют
     * (Rogue: cold_blood/shadow_blades; Mage: combustion; Hunter: true_shot).
     */
    protected double postMult(UUID uuid) {
        return 1.0;
    }

    /** Урон китовой способности: base/coeff + spec2-хуки × postMult. */
    protected double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c) * postMult(uuid);
    }

    /** Урон древесной способности: tbase/tcoeff + spec2-хуки × postMult. */
    protected double tdmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = p.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, tpower(def), b, c) * postMult(uuid);
    }

    /** Лечение китовой способности (formula-единицы); атрибуция — на вызывающем. */
    protected double healAmount(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityHeal(uuid, b, c);
    }

    /** Лечение древесной способности (formula-единицы). */
    protected double thealAmount(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityHeal(uuid, b, c);
    }

    /* ------------------------------ таргетинг ------------------------------ */

    protected LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    protected void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    protected void allyTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
    }

    /* ------------------------------ VFX ------------------------------ */

    protected void castFx(Player p, String id, String soundDef, String particleDef,
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

    protected void impactFx(LivingEntity target, String id,
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

    protected void pointFx(Location loc, String id, String soundDef, String particleDef,
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

    protected Particle resolveParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
