// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.listen;

import dev.raskol.classes.RaskolClasses;
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
 * Proc-узлы деревьев (second_wind/trance/bleed_on_crit) — Батч 3.
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

    /** TANK: +5% получаемого лечения; HEALER: +5% исходящего лечения. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.isCancelled() || event.getAmount() <= 0.0) {
            return;
        }
        if (event.getEntity() instanceof Player target
                && roleOf(target.getUniqueId()) == SpecRole.TANK) {
            double mult = 1.0 + cfgD("spec2.role-passives.TANK.heal-received", 0.05);
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
