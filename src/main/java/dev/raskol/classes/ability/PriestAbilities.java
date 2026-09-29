// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.passive.PassiveListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.7.4: КИТ ЖРЕЦА. 1.9.3 (план B): maxOf() читает formulaMaxHp();
 * heal() через HpBarService.heal().
 * 1.12.3 (Батч 4): школа HOLY, каст/impact/execute-VFX конфиг-драйвен, аура Эгиды.
 * 1.12.5: очищение — успешный хил снимает Dot'ы школ NATURE и SHADOW с цели.
 */
public final class PriestAbilities {

    private static final PlayerClass PC = PlayerClass.PRIEST;
    private final RaskolClasses plugin;

    public PriestAbilities(RaskolClasses plugin) {
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
        UUID uuid = caster.getUniqueId();
        double b = base(def, defBase) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getTalentService().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityHeal(uuid, b, c);
    }

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

    /** 1.9.3 (план B): читает formulaMaxHp() из HpBarService. */
    private double maxOf(LivingEntity e) {
        if (e instanceof Player p) {
            return plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        }
        org.bukkit.attribute.AttributeInstance attr =
                e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
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

    private void executeFx(LivingEntity target) {
        Sound sound = plugin.getFx().resolveSound(
                cfgS("vfx.wrath_heaven.execute-sound", "ENTITY_LIGHTNING_BOLT_IMPACT"));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, 0.9f, 0.7f);
        }
        Particle particle = resolveParticle(
                cfgS("vfx.wrath_heaven.execute-particle", "FLASH"));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.3, 0.2, 0.0);
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

    /**
     * 1.9.3 (план B): heal() через HpBarService.heal().
     * 1.12.5: очищение — снимает Dot'ы NATURE и SHADOW с цели + искра-партикл.
     */
    private boolean applyHeal(Player caster, Player target, AbilityDef def,
                              double defBase, double defCoeff) {
        if (!isAllyOrSelf(caster, target)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        double formula = plugin.getHpBarService().formulaMaxHp(target.getUniqueId());
        double scale = plugin.getHpBarService().scale(target);
        double hpFormula = scale > 0.0 ? target.getHealth() / scale : target.getHealth();
        double missing = formula - hpFormula;
        if (missing <= 0.0) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        double amount = Math.min(healAmount(caster, def, defBase, defCoeff), missing);
        PassiveListener.markHealer(caster.getUniqueId());
        plugin.getHpBarService().heal(target, amount);

        // 1.12.5: очищение святой водой — яды и проклятия сгорают
        UUID targetUuid = target.getUniqueId();
        int before = plugin.getCombat().dots().activeOn(targetUuid);
        plugin.getCombat().dots().removeSchoolOn(targetUuid, School.NATURE);
        plugin.getCombat().dots().removeSchoolOn(targetUuid, School.SHADOW);
        int after = plugin.getCombat().dots().activeOn(targetUuid);
        if (after < before) {
            target.getWorld().spawnParticle(Particle.ENCHANT,
                    target.getLocation().add(0.0, 1.0, 0.0), 6, 0.3, 0.4, 0.3, 0.01);
        }

        if (!target.getUniqueId().equals(caster.getUniqueId())) {
            target.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("healed-you", "{caster} исцелил тебя")
                    .replace("{caster}", caster.getName()), NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean saintTear(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        boolean ok = applyHeal(caster, tp, def, 10.0, 0.35);
        if (!ok) {
            return false;
        }
        castFx(caster, "saint_tear", "BLOCK_BELL_USE", "HEART", 0.5f, 1.1f, 10);
        impactFx(tp, "saint_tear", "BLOCK_BELL_USE", "HEART", 0.4f, 1.2f, 8);
        return true;
    }

    public boolean wordOfLife(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        boolean ok = applyHeal(caster, tp, def, 20.0, 0.6);
        if (!ok) {
            return false;
        }
        castFx(caster, "word_of_life", "BLOCK_AMETHYST_BLOCK_CHIME", "HEART", 0.6f, 1.0f, 14);
        impactFx(tp, "word_of_life", "BLOCK_AMETHYST_BLOCK_CHIME", "HEART", 0.5f, 1.1f, 12);
        return true;
    }

    public boolean aegisFaith(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 12.0) + plugin.getTalentService().baseBonus(uuid, def.id());
        double c = coeff(def, 0.04) * plugin.getTalentService().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().healPower(uuid) * c;
        int secs = duration(def, 5);
        plugin.getResists().addTimedModifier(uuid, def.id(), grant, grant, secs * 1000L);
        castFx(p, "aegis_faith", "ITEM_ARMOR_EQUIP_DIAMOND", "ENCHANTED_HIT", 0.6f, 1.0f, 20);
        plugin.getFx().startAura(uuid, Particle.ENCHANTED_HIT, secs * 20, 3,
                cfgS("vfx.aegis_faith.expire-sound", "BLOCK_AMETHYST_BLOCK_CHIME"));
        p.sendMessage(Component.text("Эгида Веры: +" + (int) grant
                + "% физ/маг резиста на " + secs + " с", NamedTextColor.YELLOW));
        return true;
    }

    public boolean circleElysium(Player p, AbilityDef def) {
        double radius = cfgD("classes.PRIEST.abilities." + def.id() + ".radius", 6.0);
        List<Player> healed = new ArrayList<>();
        if (applyHeal(p, p, def, 15.0, 0.45)) {
            healed.add(p);
        }
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player t) || t.getUniqueId().equals(p.getUniqueId())) {
                continue;
            }
            if (applyHeal(p, t, def, 15.0, 0.45)) {
                healed.add(t);
            }
        }
        if (healed.isEmpty()) {
            return false;
        }
        castFx(p, "circle_elysium", "BLOCK_BEACON_ACTIVATE", "HEART", 0.7f, 0.9f, 24);
        for (Player t : healed) {
            impactFx(t, "circle_elysium", "ENTITY_EXPERIENCE_ORB_PICKUP", "HEART", 0.4f, 1.2f, 8);
        }
        return true;
    }

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
        castFx(p, "wrath_heaven", "ENTITY_LIGHTNING_BOLT_THUNDER", "FLASH", 0.8f, 0.8f, 10);
        double threshold = cfgD("classes.PRIEST.abilities." + def.id() + ".threshold", 0.25);
        double max = maxOf(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.6);
        if (frac < threshold) {
            dmg *= cfgD("classes.PRIEST.abilities." + def.id() + ".execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            executeFx(t);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute-priest", "Кара Небес ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
            impactFx(t, "wrath_heaven", "ENTITY_FIREWORK_ROCKET_BLAST", "FLASH", 0.6f, 0.9f, 10);
        }
        return true;
    }
}
