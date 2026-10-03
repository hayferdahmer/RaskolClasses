// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.13.0 (Б2): блокировка действий под CC.
 * 1.13.0 (Б3): промах под BLIND (шанс из cc.types.BLIND.miss-chance).
 * 1.14.0-fix: СТАН/РУТ теперь реально держат цель — PlayerMoveEvent с setTo(from):
 *         velocity-ноль не останавливал клиентский ввод ходьбы. Позиция锁定,
 *         повороты головы разрешены (yaw/pitch из to). FEAR не локируется
 *         (там принудительный бег через velocity в CCService.tick).
 * STUN: атаки/хотбар/блоки/сущности/зелья + движение. FEAR: атаки/предметы.
 * DISARM: оружие (кулак разрешён). SILENCE: только гейт каста (CastGuard).
 */
public final class CCGuard implements Listener {

    private final RaskolClasses plugin;

    public CCGuard(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 1.14.0-fix: лок позиции под STUN/ROOT; голова поворачивается свободно. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        UUID id = player.getUniqueId();
        CCService cc = plugin.getCC();
        if (!cc.has(id, CCType.STUN) && !cc.has(id, CCType.ROOT)) {
            return;
        }
        Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return; // только поворот головы — пропускаем дёшево
        }
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
    }

    /** STUN/FEAR блокируют атаки; BLIND даёт промах; DISARM снимает оружие. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        CCService cc = plugin.getCC();
        UUID id = attacker.getUniqueId();
        if (cc.has(id, CCType.STUN) || cc.has(id, CCType.FEAR)) {
            event.setCancelled(true);
            return;
        }
        if (cc.has(id, CCType.BLIND)
                && ThreadLocalRandom.current().nextDouble() < cc.blindMissChance()) {
            event.setCancelled(true);
            return;
        }
        if (cc.has(id, CCType.DISARM)) {
            if (attacker.getInventory().getItemInMainHand().getType().isAir()) {
                return; // кулак разрешён
            }
            event.setCancelled(true);
        }
    }

    /** STUN/FEAR блокируют предметы и блоки. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        CCService cc = plugin.getCC();
        UUID id = player.getUniqueId();
        if (cc.has(id, CCType.STUN) || cc.has(id, CCType.FEAR)) {
            if (event.getItem() != null || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    /** STUN блокирует взаимодействие с сущностями. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCC().has(player.getUniqueId(), CCType.STUN)) {
            event.setCancelled(true);
        }
    }

    /** STUN блокирует питьё/еду. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCC().has(player.getUniqueId(), CCType.STUN)) {
            event.setCancelled(true);
        }
    }
}
