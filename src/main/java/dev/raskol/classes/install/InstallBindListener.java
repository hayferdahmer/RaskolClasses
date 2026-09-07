// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.compat.AuthGate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * ПКМ со свитком инсталляции = постановка.
 * 1.5.8: AuthGate.allowed на входе (creative/граница/пустота проверяются в tryPlace).
 */
public final class InstallBindListener implements Listener {

    private final RaskolClasses plugin;
    private final InstallToken token;

    public InstallBindListener(RaskolClasses plugin, InstallToken token) {
        this.plugin = plugin;
        this.token = token;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // 1.5.8: auth-гейт
        if (!AuthGate.allowed(plugin, player)) {
            return;
        }
        InstallationType type = token.readType(player.getInventory().getItemInMainHand());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        plugin.getInstallations().tryPlace(player);
    }
}
