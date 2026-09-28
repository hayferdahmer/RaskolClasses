// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

/**
 * 1.11.4 (P1): Чернокнижник — «Чёрная Месса» (black_mass).
 * Лифстил 6.66% только с урона способностей; рефлект 6.66% полученного
 * чистым уроном по врагам в r8 (союзники-игроки защищены гейтом).
 * S4: recoil/overflow/плата Слова идут через setHealth без события → рефлект молчит;
 * рефлект-урон помечен beginReflect/endReflect → без цепочек и без отката.
 */
public final class WarlockPassives extends BaseClassPassive {

    public WarlockPassives(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public PlayerClass playerClass() {
        return PlayerClass.WARLOCK;
    }

    @Override
    public void onDamageOut(EntityDamageByEntityEvent event, Player attacker,
                            LivingEntity target, double damage) {
        if (!enabled("black_mass") || CombatService.reflectSuppressed()) {
            return;
        }
        if (!attacker.getUniqueId().equals(CombatService.abilitySourceMark())) {
            return; // только урон способностей, не авто-атаки
        }
        double ls = damage * cfgD("black_mass", "lifesteal", 0.0666);
        double scale = plugin.getAttributes().scale(attacker);
        if (scale > 0.0 && ls > 0.0) {
            plugin.getHpBarService().heal(attacker, ls / scale);
            plugin.getFx().procByKey(attacker, "☾ Чёрная Месса", "black_mass");
        }
    }

    @Override
    public void onDamageIn(EntityDamageByEntityEvent event, Player victim, double damage) {
        if (!enabled("black_mass") || CombatService.reflectSuppressed()) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity)) {
            return;
        }
        UUID vid = victim.getUniqueId();
        int cd = cfgI("black_mass", "cooldown-seconds", 1);
        if (!procCdOk(vid, "black_mass_reflect", cd)) {
            return;
        }
        double reflCarrier = damage * cfgD("black_mass", "reflect", 0.0666);
        double radius = cfgD("black_mass", "reflect-radius", 8.0);
        double scaleV = plugin.getAttributes().scale(victim);
        double reflFormula = scaleV > 0.0 ? reflCarrier / scaleV : reflCarrier;
        if (reflFormula <= 0.0) {
            return;
        }
        CombatService.beginReflect();
        try {
            for (Entity e : victim.getWorld().getNearbyEntities(
                    victim.getLocation(), radius, radius, radius)) {
                if (!(e instanceof LivingEntity t) || t.equals(victim) || t.isDead()) {
                    continue;
                }
                if (!plugin.getCombat().canHit(victim, t)) {
                    continue;
                }
                plugin.getCombat().dealDamage(t, victim,
                        new DamageProfile(0.0, 0.0, reflFormula));
            }
        } finally {
            CombatService.endReflect();
        }
        plugin.getFx().procByKey(victim, "☾ Чёрная Месса", "black_mass");
    }
}
