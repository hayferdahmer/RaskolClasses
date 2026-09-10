// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * 1.7.2: КИТ ВОИНА (нордика). Все способности масштабируются от Силы оружия (WP):
 * урон/хил/гранты = base + WP × coeff (конфиг classes.WARRIOR.abilities.<id>.*).
 *
 * Кит (слоты 1–5):
 *  1. «Удар Тира» (tyr_strike)     — одиночный физ-нуку base+WP×0.6, рей 4 блока;
 *  2. «Шкура Бальдра» (balder_skin)— грант физ-резиста (15 + WP×0.05)% на duration;
 *  3. «Берсеркерганг» (berserkergang) — Сила II + Сопротивление I на duration;
 *  4. «Кровь Фенрира» (fenrir_blood) — мгновенный self-хил base+WP×0.5 (Victory Rush-вайб);
 *  5. «Рагнарёк» (ragnarok)        — execute-финишер: цель <25% HP → ×3 урона через
 *                                    dealDamage(allowOverCap=true); иначе base+WP×1.8.
 * Плоских чисел урона больше нет: всё через PowerService.abilityDamage(power="wp").
 */
public final class WarriorAbilities {

    private static final PlayerClass PC = PlayerClass.WARRIOR;

    private final RaskolClasses plugin;

    public WarriorAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
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

    /** 1. «Удар Тира» — одиночный физ-нуку ближнего боя. */
    public boolean tyrStrike(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 10.0, 0.6);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        return true;
    }

    /** 2. «Шкура Бальдра» — грант физ-резиста, масштабируется от WP. */
    public boolean balderSkin(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double wp = plugin.getCombat().powers().weaponPower(uuid);
        double grant = base(def, 15.0) + wp * coeff(def, 0.05);
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(uuid, def.id(), grant, 0.0, secs * 1000L);
        return true;
    }

    /** 3. «Берсеркерганг» — бафф урона/стойкости ванильными эффектами. */
    public boolean berserkergang(Player p, AbilityDef def) {
        int secs = duration(def, 6);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 0));
        return true;
    }

    /** 4. «Кровь Фенрира» — мгновенный self-хил от WP (Victory Rush-вайб). */
    public boolean fenrirBlood(Player p, AbilityDef def) {
        double heal = dmg(p, def, 15.0, 0.5);
        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double now = p.getHealth();
        if (now >= max) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        p.heal(Math.min(heal, max - now));
        return true;
    }

    /** 5. «Рагнарёк» — execute-финишер: цель <25% HP → ×3 через allowOverCap. */
    public boolean ragnarok(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 4);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double threshold = cfgD("classes.WARRIOR.abilities." + def.id() + ".threshold", 0.25);
        AttributeInstance maxAttr = t.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.8);
        if (frac < threshold) {
            dmg *= cfgD("classes.WARRIOR.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg), true);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        }
        return true;
    }
}
