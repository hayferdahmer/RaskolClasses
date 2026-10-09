// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability.passive;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Диспетчер классовых пассивок (1.7.x): слушает боевые события и дёргает
 * ClassPassive-обработчики шести классов.
 *
 * 1.14.1 (Волна 1): universal heal_out_pct применялся здесь к ванильным
 * EntityRegainHealthEvent с маркером хилера.
 * 1.14.7 (Спринт 2, P1-1/P1-2): ветка heal_out/HEALER ИЗ onRegainHealth УДАЛЕНА —
 *   лечение компонуется в ОДНОЙ точке (Spec2RoleListener.onCustomHeal для кит-хилов,
 *   Spec2RoleListener.onRegain для входящих). Ванильный regain-путь с маркером был
 *   рудиментом: kit-хилы идут через setHealth без regain-события, а stale-маркер
 *   (static volatile UUID, peek без сброса) приписывал чужие регены жрецу.
 *   markHealer() оставлен как deprecated no-op: вызовы из PriestAbilities
 *   компилируются до Sprint 4, где будут вычищены вместе с дублями китов.
 *   Пассивки урона/проков (execute_passive, predator, grace-множитель хилов НЕ здесь)
 *   работают как прежде.
 */
public final class PassiveListener implements Listener {

    private final RaskolClasses plugin;

    /** 1.14.7 (P1-2): deprecated no-op — атрибуция лечения живёт в CustomHealEvent. */
    @Deprecated
    private static volatile UUID healerMark;

    public PassiveListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * 1.14.7 (P1-2): no-op. Оставлен для совместимости с вызовами PriestAbilities
     * (applyHealWith/groupHeal) до вычистки в Sprint 4.
     */
    @Deprecated
    public static void markHealer(UUID healer) {
        // no-op: атрибуция лечения передаётся через CustomHealEvent.getHealer()
    }

    /** 1.14.7 (P1-2): no-op-чтение (всегда null) — ResourceService больше не поллит маркер. */
    @Deprecated
    public static UUID pollHealerMark() {
        UUID m = healerMark;
        healerMark = null;
        return m;
    }

    @Deprecated
    public static UUID peekHealerMark() {
        return healerMark;
    }

    /* ------------------------------ урон: пассивки классов ------------------------------ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(attacker);
        if (pc == null) {
            return;
        }
        ClassPassive passive = ClassPassive.of(pc);
        if (passive == null) {
            return;
        }
        double bonus = passive.damageBonus(plugin, attacker, target, event.getDamage());
        if (bonus != 0.0) {
            event.setDamage(event.getDamage() + bonus);
            passive.feedback(plugin, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(victim);
        if (pc == null) {
            return;
        }
        ClassPassive passive = ClassPassive.of(pc);
        if (passive == null) {
            return;
        }
        double reduction = passive.damageReduction(plugin, victim, event.getDamage());
        if (reduction > 0.0) {
            event.setDamage(Math.max(0.0, event.getDamage() * (1.0 - reduction)));
        }
    }

    /**
     * 1.14.7 (P1-1): ванильные regain-события БОЛЬШЕ не домножаются на heal_out/HEALER.
     * Оставлен пустым обработчиком только для того, чтобы не ломать регистрацию
     * слушателя в RaskolClasses; логика лечения — в Spec2RoleListener.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        // no-op: композиция лечения в Spec2RoleListener (CustomHealEvent + onRegain)
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
