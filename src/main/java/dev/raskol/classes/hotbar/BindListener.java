// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Пакет 6: ПКМ со свитком = каст в себя. Пакет 1.3.1: ЛКМ со свитком точечной
 * способности по игроку = каст в цель (атака отменяется); ЛКМ по мобу =
 * обычная атака (свиток не мешает PvE).
 */
public final class BindListener implements Listener {

    private final RaskolClasses plugin;
    private final AbilityToken token;

    public BindListener(RaskolClasses plugin, AbilityToken token) {
        this.plugin = plugin;
        this.token = token;
    }

    /** ПКМ: каст (точечные — всегда в себя). */
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
        String id = token.readId(player.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        event.setCancelled(true);

        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(
                    plugin.getRaskolConfig().message("no-class-cast",
                            "Класс не выбран — способности недоступны"),
                    NamedTextColor.GRAY));
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, id);
        if (def == null) {
            player.sendMessage(Component.text("Этот свиток не твоего класса ("
                    + pc.getDisplayName() + ")", NamedTextColor.RED));
            return;
        }
        plugin.getAbilities().tryCast(player, def);
    }

    /** 1.3.1: ЛКМ по игроку со свитком точечной способности = каст в цель. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!plugin.getRaskolConfig().isTargetCastEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return; // ЛКМ по мобу — обычная атака
        }
        String id = token.readId(player.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, id);
        if (def == null || !plugin.getAbilities().isTargeted(def.id())) {
            return; // чужой класс или не-точечная способность — атака как обычно
        }
        event.setCancelled(true);
        plugin.getAbilities().tryCastTargeted(player, target, def);
    }
}
