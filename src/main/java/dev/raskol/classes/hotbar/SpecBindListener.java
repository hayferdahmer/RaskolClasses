// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * ПКМ со свитком спеки = каст активки (1.4.0, Пакет 2.1).
 * Свиток чужой спеки не работает (защита от передачи/подмены).
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
        Player player = event.getPlayer();
        Spec spec = token.readSpec(player.getInventory().getItemInMainHand());
        if (spec == null) {
            return;
        }
        event.setCancelled(true);

        Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
        if (current != spec) {
            player.sendMessage(Component.text(
                    "Этот свиток — для другой специализации", NamedTextColor.RED));
            return;
        }
        plugin.getSpecCaster().tryCast(player, spec);
    }
}
