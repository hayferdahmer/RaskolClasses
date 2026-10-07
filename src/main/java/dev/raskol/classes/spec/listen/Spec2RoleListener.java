// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.listen;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.event.CustomHealEvent;
import dev.raskol.classes.spec.SpecRole;
import dev.raskol.classes.spec.SpecRoles;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * 1.14.0 «Спек 2.0» (Б2): роли-пассивки основной спеки (дизайн-док, раздел 6):
 *  - FIGHTER: +2% исходящего урона (spec2.role-passives.FIGHTER.damage-mult);
 *  - TANK:    +5% ccResist (читает CCService из Spec2Service.ccResistBonus)
 *             и +5% получаемого лечения;
 *  - HEALER:  +5% исходящего лечения.
 * Плюс восстановление spec2-модификаторов на join и снятие на quit
 * (ResistService/AttributeService чистят модификаторы на quit).
 * Proc-узлы деревьев (second_wind/trance/bleed_on_crit) — Батч 3 (волна 3C).
 *
 * 1.14.1 (Волна 1): исходящее лечение ролей/узлов собрано в CustomHealEvent.
 * 1.14.3 (Волна 3, 3A): ЕДИНАЯ точка композиции лечения:
 *   исходящие = heal_out_pct (узлы) × роль HEALER (конфиг);
 *   входящие  = heal_received_pct (узлы) + роль TANK (конфиг).
 *   Применяется и к китовым хилам (CustomHealEvent), и к ванильным
 *   (EntityRegainHealthEvent). Двойной счёт роли HEALER устранён: пред-множитель
 *   убран из PriestAbilities.applyHealWith, владелец — этот слушатель.
 */
public final class Spec2RoleListener implements Listener {

    private final RaskolClasses plugin;

    public Spec2RoleListener(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    private SpecRole roleOf(UUID uuid) {
        String main = plugin.getSpec2Service().mainSpec(uuid);
        return main == null ? null : SpecRoles.roleOf(main);
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getSpec2Service().reconcile(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSpec2Applier().remove(event.getPlayer().getUniqueId());
        plugin.getSpec2Service().clear(event.getPlayer().getUniqueId());
    }

    /** FIGHTER: множитель исходящего урона. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || event.getDamage() <= 0.0) {
            return;
        }
        if (roleOf(attacker.getUniqueId()) != SpecRole.FIGHTER) {
            return;
        }
        double mult = 1.0 + cfgD("spec2.role-passives.FIGHTER.damage-mult", 0.02);
        event.setDamage(event.getDamage() * mult);
    }

    /**
     * 1.14.3 (3A): входящее лечение (ванильные regain-события: реген, зелья, яблоки).
     * TANK-роль (конфиг) + heal_received_pct (узлы guard) аддитивно в множителе.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.isCancelled() || event.getAmount() <= 0.0) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        UUID tid = target.getUniqueId();
        double mult = 1.0;
        if (roleOf(tid) == SpecRole.TANK) {
            mult += cfgD("spec2.role-passives.TANK.heal-received", 0.05);
        }
        mult += plugin.getSpec2Service().healReceivedPercent(tid) / 100.0;
        if (mult != 1.0) {
            event.setAmount(event.getAmount() * mult);
        }
    }

    /**
     * 1.14.3 (3A): композиция КАСТОМНОГО лечения (кит-хилы через HpBarService.heal).
     * Исходящая сторона: heal_out_pct (узлы discipline/holy/arms) × роль HEALER.
     * Входящая сторона: heal_received_pct (узлы guard) + роль TANK.
     * Порядок мультипликативный; все слагаемые — проценты агрегата /100.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomHeal(CustomHealEvent event) {
        if (event.isCancelled() || event.getAmount() <= 0.0) {
            return;
        }
        double mult = 1.0;

        // Исходящие модификаторы целителя.
        Player healer = event.getHealer();
        if (healer != null) {
            UUID hid = healer.getUniqueId();
            mult *= 1.0 + plugin.getSpec2Service().healOutPercent(hid) / 100.0;
            if (roleOf(hid) == SpecRole.HEALER) {
                mult *= 1.0 + cfgD("spec2.role-passives.HEALER.heal-mult", 0.05);
            }
        }

        // Входящие модификаторы цели (только игроки-цели).
        if (event.getTarget() instanceof Player target) {
            UUID tid = target.getUniqueId();
            double recv = 1.0;
            if (roleOf(tid) == SpecRole.TANK) {
                recv += cfgD("spec2.role-passives.TANK.heal-received", 0.05);
            }
            recv += plugin.getSpec2Service().healReceivedPercent(tid) / 100.0;
            mult *= recv;
        }

        if (mult != 1.0) {
            event.setAmount(event.getAmount() * mult);
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
