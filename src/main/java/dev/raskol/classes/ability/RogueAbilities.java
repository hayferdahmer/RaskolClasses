// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
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

import java.util.Locale;
import java.util.UUID;

/**
 * 1.7.3: КИТ РАЗБОЙНИКА (средневековый реализм). Урон = base + WP×coeff.
 * 1.8.1: canHit-гейты на однотargetных урон-абилках (до наложения blind/slow/poison).
 * 1.9.0: талантовые хуки baseBonus/coeffMult.
 * 1.12.3 (Батч 6): школы SHADOW/PHYSICAL/NATURE, каст/impact-VFX конфиг-драйвен.
 * 1.12.5: яд = школьный DoT poison (NATURE), удушение добавляет DoT bleed (PHYSICAL);
 *         ванильный PotionEffect POISON убран (атрибуция/стеки/капы через DotService).
 */
public final class RogueAbilities {

    private static final PlayerClass PC = PlayerClass.ROGUE;

    private final RaskolClasses plugin;

    public RogueAbilities(RaskolClasses plugin) {
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

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.ROGUE.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.ROGUE.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.ROGUE.abilities." + def.id() + ".power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.ROGUE.abilities." + def.id() + ".duration", defv);
        return v > 0 ? v : defv;
    }

    /** 1.9.0: base/coeff с талантовыми хуками. */
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

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Плащ теней» — Невидимость 15 с (self) + дымовой уход. */
    public boolean shadowCloak(Player p, AbilityDef def) {
        int secs = duration(def, 15);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, secs * 20, 0));
        castFx(p, "shadow_cloak", "ENTITY_PHANTOM_FLAP", "SMOKE", 0.5f, 0.9f, 16);
        impactFx(p, "shadow_cloak", "ENTITY_ENDERMAN_TELEPORT", "SMOKE", 0.4f, 1.2f, 12);
        return true;
    }

    /** 2. «Веер клинков» — AoE физ радиус 3 (LOS + фракционный фильтр). */
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
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            impactFx(t, "blade_fan", "ENTITY_PLAYER_HURT", "DAMAGE_INDICATOR", 0.4f, 1.0f, 8);
            hit = true;
        }
        return hit;
    }

    /** 3. «Удушение палача» — урон + Blind + Slowness + DoT bleed (1.12.5). */
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
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        impactFx(t, "strangle", "ENTITY_PLAYER_HURT", "SMOKE", 0.4f, 0.8f, 10);
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 1));
        plugin.getCombat().dots().applyById(p, t, "bleed");
        return true;
    }

    /** 4. «Яд Борджа» — урон + DoT poison (1.12.5, вместо ванильного POISON). */
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
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        impactFx(t, "borgia_poison", "ENTITY_SPIDER_HURT", "COMPOSTER", 0.4f, 0.9f, 12);
        plugin.getCombat().dots().applyById(p, t, "poison");
        return true;
    }

    /** 5. «Танец теней» — +30 ЛОВКОСТИ на 4 с (self, всплеск уклонения). */
    public boolean shadowDance(Player p, AbilityDef def) {
        double agiBonus = base(def, 30.0);
        int secs = duration(def, 4);
        castFx(p, "shadow_dance", "ENTITY_ENDERMAN_TELEPORT", "CLOUD", 0.6f, 1.1f, 20);
        plugin.getAttributes().addTimedModifier(
                p.getUniqueId(), def.id(), 0.0, agiBonus, 0.0, secs * 1000L);
        impactFx(p, "shadow_dance", "ENTITY_ENDERMAN_TELEPORT", "CLOUD", 0.4f, 1.3f, 16);
        return true;
    }
}
