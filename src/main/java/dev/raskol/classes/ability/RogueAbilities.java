// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * 1.7.3: КИТ РАЗБОЙНИКА (средневековый реализм). Урон = base + WP×coeff.
 * 1.8.1: canHit-гейты на однотargetных урон-абилках (до наложения blind/slow/poison).
 * 1.9.0: талантовые хуки baseBonus/coeffMult.
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

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Плащ теней» — Невидимость 15 с (self). */
    public boolean shadowCloak(Player p, AbilityDef def) {
        int secs = duration(def, 15);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, secs * 20, 0));
        return true;
    }

    /** 2. «Веер клинков» — AoE физ радиус 3 (LOS + фракционный фильтр). */
    public boolean bladeFan(Player p, AbilityDef def) {
        double radius = cfgD("classes.ROGUE.abilities." + def.id() + ".radius", 3.0);
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
            hit = true;
        }
        return hit;
    }

    /** 3. «Удушение палача» — урон + Blind + Slowness. 1.8.1: гейт союзника ДО дебаффов. */
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
        double dmg = dmg(p, def, 10.0, 0.9);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 1));
        return true;
    }

    /** 4. «Яд Борджа» — урон + Яд I 5 с. 1.8.1: гейт союзника ДО яда. */
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
        double dmg = dmg(p, def, 5.0, 0.3);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 5 * 20, 0));
        return true;
    }

    /** 5. «Танец теней» — +30 ЛОВКОСТИ на 4 с (self, всплеск уклонения). */
    public boolean shadowDance(Player p, AbilityDef def) {
        double agiBonus = base(def, 30.0);
        int secs = duration(def, 4);
        plugin.getAttributes().addTimedModifier(
                p.getUniqueId(), def.id(), 0.0, agiBonus, 0.0, secs * 1000L);
        return true;
    }
}
