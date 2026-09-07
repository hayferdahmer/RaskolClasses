// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.compat.AuthGate;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * ПКМ со свитком активки спеки = каст (слот 6).
 * 1.5.8: AuthGate.allowed на входе.
 */
public final class SpecBindListener implements Listener {

    private final RaskolClasses plugin;
    private final SpecToken token;

    public SpecBindListener(RaskolClasses plugin, SpecToken token) {
        this.plugin = plugin;
        this.token = token;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.getRaskolConfig().isBindEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        // 1.5.8: auth-гейт
        if (!AuthGate.allowed(plugin, player)) {
            return;
        }
        Spec spec = token.readSpec(player.getInventory().getItemInMainHand());
        if (spec == null) {
            return;
        }
        Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
        if (current != spec) {
            player.sendMessage(Component.text(
                    "Этот свиток не твоей специализации",
                    NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);
        // VFX каста играет SpecActiveCaster.tryCast внутри себя
        plugin.getSpecCaster().tryCast(player, spec);
    }
}
