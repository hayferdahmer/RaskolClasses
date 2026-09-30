// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
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

import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.13.0 (Б2): блокировка действий под CC.
 * 1.13.0 (Б3): + промах под BLIND — атакующий с CCType.BLIND с шансом
 *         cc.types.BLIND.miss-chance (дефолт 50%) не наносит урон (событие гасится).
 * STUN: запрет атак/хотбара/блоков/сущностей/зелий. FEAR: запрет атак/предметов.
 * DISARM: запрет атак оружием, кулак разрешён. SILENCE: только гейт каста (CastGuard).
 */
public final class CCGuard implements Listener {

    private final RaskolClasses plugin;

    public CCGuard(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        CCService cc = plugin.getCC();
        UUID-ish: // placeholder removed
        if (cc.has(attacker.getUniqueId(), CCType.STUN)
                || cc.has(attacker.getUniqueId(), CCType.FEAR)) {
            event.setCancelled(true);
            return;
        }
        // 1.13.0 (Б3): слепота — шанс промаха
        if (cc.has(attacker.getUniqueId(), CCType.BLIND)
                && ThreadLocalRandom.current().nextDouble() < cc.blindMissChance()) {
            event.setCancelled(true);
            return;
        }
        if (cc.has(attacker.getUniqueId(), CCType.DISARM)) {
            if (attacker.getInventory().getItemInMainHand().getType().isAir()) {
                return; // кулак: разрешено (штраф урона — задача кита/спек, не ядра)
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        CCService cc = plugin.getCC();
        if (cc.has(player.getUniqueId(), CCType.STUN)
                || cc.has(player.getUniqueId(), CCType.FEAR)) {
            if (event.getItem() != null || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCC().has(player.getUniqueId(), CCType.STUN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCC().has(player.getUniqueId(), CCType.STUN)) {
            event.setCancelled(true);
        }
    }
}
