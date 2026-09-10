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

/**
 * 1.7.4: КИТ ЖРЕЦА (католика/паладинство). Закрытие боли «хилы ни о чём»:
 * все лечения масштабируются от Силы исцеления (HPow = база + INT×1.4),
 * урон-финишер — от Силы заклинаний (SP).
 *
 * Кит (слоты 1–5):
 *  1. «Слеза Святой» (saint_tear)    — таргет-хил себя/союзника: base + HPow×0.35;
 *  2. «Слово Жизни» (word_of_life)   — сильный таргет-хил: base + HPow×0.6;
 *  3. «Эгида Веры» (aegis_faith)     — грант ФИЗ+МАГ резиста (base + HPow×0.04)% на 5 с;
 *  4. «Круг Элизия» (circle_elysium) — AoE-хил себя + союзников радиус 6: base + HPow×0.45;
 *  5. «Кара Небес» (wrath_heaven)    — execute: маг-урон base + SP×1.2; цель <25% HP → ×3
 *                                      через dealDamage(allowOverCap=true).
 *
 * Таргет-хилы: только себя или союзника (одна фракция); при combat.friendly-fire=true
 * лечат кого угодно. Полный HP цели → отказ с возвратом ресурса (castOn refund).
 * Хил идёт через LivingEntity.heal → RegainHealthEvent → ресурс жреца +on-heal (дизайн).
 */
public final class PriestAbilities {

    private static final PlayerClass PC = PlayerClass.PRIEST;

    private final RaskolClasses plugin;

    public PriestAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.PRIEST.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.PRIEST.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.PRIEST.abilities." + def.id() + ".power", "hpow");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.PRIEST.abilities." + def.id() + ".duration", defv);
        return v > 0 ? v : defv;
    }

    private double healAmount(Player caster, AbilityDef def, double defBase, double defCoeff) {
        return plugin.getCombat().powers().abilityHeal(
                caster.getUniqueId(), base(def, defBase), coeff(def, defCoeff));
    }

    private double dmg(Player caster, AbilityDef def, double defBase, double defCoeff) {
        return plugin.getCombat().powers().abilityDamage(
                caster.getUniqueId(), power(def), base(def, defBase), coeff(def, defCoeff));
    }

    private void msg(Player p, String key, String def) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(key, def),
                NamedTextColor.GRAY));
    }

    /* ------------------------------ союзники / хилы ------------------------------ */

    /** Себя или союзник (одна непустая фракция); при friendly-fire=true — кого угодно. */
    private boolean isAllyOrSelf(Player caster, Player target) {
        if (caster.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        if (plugin.getConfig().getBoolean("combat.friendly-fire", false)) {
            return true;
        }
        String f1 = plugin.getFactionHook().factionOf(caster.getUniqueId());
        String f2 = plugin.getFactionHook().factionOf(target.getUniqueId());
        return f1 != null && !f1.isEmpty() && f1.equals(f2);
    }

    private double maxOf(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    /** Применить хил к цели-игроку; false если не союзник или полный HP. */
    private boolean applyHeal(Player caster, Player target, AbilityDef def,
                              double defBase, double defCoeff) {
        if (!isAllyOrSelf(caster, target)) {
            msg(caster, "ally.no-heal", "Цель не союзник");
            return false;
        }
        double max = maxOf(target);
        double missing = max - target.getHealth();
        if (missing <= 0.0) {
            msg(caster, "target-full-hp", "Цель здорова");
            return false;
        }
        double amount = Math.min(healAmount(caster, def, defBase, defCoeff), missing);
        target.heal(amount);
        if (!target.getUniqueId().equals(caster.getUniqueId())) {
            target.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("healed-you", "{caster} исцелил тебя")
                    .replace("{caster}", caster.getName()), NamedTextColor.GREEN));
        }
        return true;
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Слеза Святой» — таргет-хил себя/союзника. */
    public boolean saintTear(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            msg(caster, "ally.no-heal", "Цель не союзник");
            return false;
        }
        return applyHeal(caster, tp, def, 10.0, 0.35);
    }

    /** 2. «Слово Жизни» — сильный таргет-хил себя/союзника. */
    public boolean wordOfLife(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            msg(caster, "ally.no-heal", "Цель не союзник");
            return false;
        }
        return applyHeal(caster, tp, def, 20.0, 0.6);
    }

    /** 3. «Эгида Веры» — грант ФИЗ+МАГ резиста, скалируется от HPow. */
    public boolean aegisFaith(Player p, AbilityDef def) {
        double hpow = plugin.getCombat().powers().healPower(p.getUniqueId());
        double grant = base(def, 12.0) + hpow * coeff(def, 0.04);
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(
                p.getUniqueId(), def.id(), grant, grant, secs * 1000L);
        return true;
    }

    /** 4. «Круг Элизия» — AoE-хил себя + союзников радиус 6. */
    public boolean circleElysium(Player p, AbilityDef def) {
        double radius = cfgD("classes.PRIEST.abilities." + def.id() + ".radius", 6.0);
        boolean healed = false;
        // себя — явно (getNearbyEntities не включает самого себя)
        healed |= applyHeal(p, p, def, 15.0, 0.45);
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player t) || t.getUniqueId().equals(p.getUniqueId())) {
                continue;
            }
            if (applyHeal(p, t, def, 15.0, 0.45)) {
                healed = true;
            }
        }
        return healed;
    }

    /** 5. «Кара Небес» — execute-финишер: цель <25% HP → ×3 через allowOverCap. */
    public boolean wrathHeaven(Player p, AbilityDef def) {
        Entity e = p.getTargetEntity(20);
        if (!(e instanceof LivingEntity t)) {
            msg(p, "cheap-shot-no-target", "Нет цели в радиусе действия");
            return false;
        }
        double threshold = cfgD("classes.PRIEST.abilities." + def.id() + ".threshold", 0.25);
        double max = maxOf(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.2);
        if (frac < threshold) {
            dmg *= cfgD("classes.PRIEST.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute", "Казнь ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        }
        return true;
    }
}
