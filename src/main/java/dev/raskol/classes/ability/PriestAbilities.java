// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.passive.PassiveListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.7.4: КИТ ЖРЕЦА (католика/паладинство). Хилы = base + HPow×coeff, финишер от SP.
 * Таргет-хилы: себя или союзника; полный HP → отказ + refund.
 * 1.8.1: canHit-гейт на «Каре Небес»; execute-тег tag.execute-priest.
 * 1.9.0: талантовые хуки baseBonus/coeffMult; markHealer перед heal()
 * (благодать в PassiveListener + ресурс за лечение в ResourceService).
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

    /** 1.9.0: хил с талантовыми хуками. */
    private double healAmount(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = base(def, defBase) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getTalentService().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityHeal(uuid, b, c);
    }

    /** 1.9.0: урон с талантовыми хуками. */
    private double dmg(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = base(def, defBase) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getTalentService().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c);
    }

    private void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    private void allyTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
    }

    /* ------------------------------ союзники / хилы ------------------------------ */

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
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        double max = maxOf(target);
        double missing = max - target.getHealth();
        if (missing <= 0.0) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        double amount = Math.min(healAmount(caster, def, defBase, defCoeff), missing);
        // 1.9.0: маркер хилера — благодать (PassiveListener) и ресурс за лечение (ResourceService)
        PassiveListener.markHealer(caster.getUniqueId());
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
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        return applyHeal(caster, tp, def, 10.0, 0.35);
    }

    /** 2. «Слово Жизни» — сильный таргет-хил себя/союзника. */
    public boolean wordOfLife(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        return applyHeal(caster, tp, def, 20.0, 0.6);
    }

    /** 3. «Эгида Веры» — грант ФИЗ+МАГ резиста (self). 1.9.0: талантовые хуки. */
    public boolean aegisFaith(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 12.0) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, 0.04) * plugin.getTalentService().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().healPower(uuid) * c;
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(uuid, def.id(), grant, grant, secs * 1000L);
        return true;
    }

    /** 4. «Круг Элизия» — AoE-хил себя + союзников радиус 6. */
    public boolean circleElysium(Player p, AbilityDef def) {
        double radius = cfgD("classes.PRIEST.abilities." + def.id() + ".radius", 6.0);
        boolean healed = applyHeal(p, p, def, 15.0, 0.45);
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

    /** 5. «Кара Небес» — execute-финишер от SP. 1.8.1: гейт союзника; тег execute-priest. */
    public boolean wrathHeaven(Player p, AbilityDef def) {
        Entity e = p.getTargetEntity(20);
        if (!(e instanceof LivingEntity t)) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        double threshold = cfgD("classes.PRIEST.abilities." + def.id() + ".threshold", 0.25);
        double max = maxOf(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.6);
        if (frac < threshold) {
            dmg *= cfgD("classes.PRIEST.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute-priest", "Кара Небес ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        }
        return true;
    }
}
