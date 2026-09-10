// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * 1.7.3: КИТ РАЗБОЙНИКА (средневековый реализм). Всё масштабируется от Силы оружия (WP):
 * урон = base + WP × coeff (конфиг classes.ROGUE.abilities.<id>.*).
 *
 * Кит (слоты 1–5):
 *  1. «Плащ теней» (shadow_cloak)     — Невидимость 15 с;
 *  2. «Веер клинков» (blade_fan)      — AoE радиус 3 (LOS): физ-урон;
 *  3. «Удушение палача» (strangle)    — одиночный: физ + Blind 2 с + Slowness 2 с;
 *  4. «Яд Борджа» (borgia_poison)     — Яд I 5 с + малый физ-урон;
 *  5. «Танец теней» (shadow_dance)    — timed-модификатор +AGI (всплеск dodge через avoidance).
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

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        return plugin.getCombat().powers().abilityDamage(
                p.getUniqueId(), power(def), base(def, defBase), coeff(def, defCoeff));
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    private void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.RED));
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Плащ теней» — Невидимость 15 с. */
    public boolean shadowCloak(Player p, AbilityDef def) {
        int secs = duration(def, 15);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, secs * 20, 0));
        return true;
    }

    /** 2. «Веер клинков» — AoE физ-урон радиус 3 (LOS). */
    public boolean bladeFan(Player p, AbilityDef def) {
        double radius = cfgD("classes.ROGUE.abilities." + def.id() + ".radius", 3.0);
        double dmg = dmg(p, def, 8.0, 0.5);
        boolean hit = false;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || e.equals(p)) {
                continue;
            }
            if (!dev.raskol.classes.combat.Targeting.isValidDamageTarget(plugin, p, t)) {
                continue;
            }
            if (!dev.raskol.classes.combat.Targeting.hasLineOfSight(plugin, p, t)) {
                continue;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            hit = true;
        }
        return hit;
    }

    /** 3. «Удушение палача» — одиночный: физ + Blind + Slowness. */
    public boolean strangle(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 10.0, 0.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 1));
        return true;
    }

    /** 4. «Яд Борджа» — Яд I 5 с + малый физ-урон. */
    public boolean borgiaPoison(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 5.0, 0.3);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 5 * 20, 0));
        return true;
    }

    /** 5. «Танец теней» — timed-модификатор +AGI → всплеск dodge через avoidance. */
    public boolean shadowDance(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double agiBonus = base(def, 30.0);
        int secs = duration(def, 4);
        plugin.getAttributes().addTimedModifier(uuid, def.id(), 0.0, agiBonus, 0.0, secs * 1000L);
        return true;
    }
}
