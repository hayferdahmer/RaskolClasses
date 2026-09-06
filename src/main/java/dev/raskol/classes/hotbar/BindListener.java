// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Свитки способностей (bind 1–5).
 * FIX 1.5.1:
 *  - таргет-каст не тратится на цели в creative/spectator (раньше съедал ресурс и кд);
 *  - на quit чистится анти-спам карта и состояние ready-notify спеков.
 */
public final class BindListener implements Listener {

    private final RaskolClasses plugin;
    private final AbilityToken token;

    public BindListener(RaskolClasses plugin, AbilityToken token) {
        this.plugin = plugin;
        this.token = token;
    }

    /** ПКМ: каст в себя (точечные — тоже в себя). */
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
            player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-class-cast", "Класс не выбран — способности недоступны"),
                    NamedTextColor.GRAY));
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, id);
        if (def == null) {
            player.sendMessage(Component.text("Этот свиток не твоего класса ("
                    + pc.getDisplayName() + ")", NamedTextColor.RED));
            return;
        }
        if (plugin.getAbilities().tryCast(player, def)) {
            plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
        }
    }

    /** ЛКМ по игроку со свитком точечной способности = каст в цель. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!plugin.getRaskolConfig().isTargetCastEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        // FIX 1.5.1: креатив/спектор — не цель лечения, каст не тратим
        if (target.getGameMode() == GameMode.CREATIVE
                || target.getGameMode() == GameMode.SPECTATOR) {
            dbg(player, "обрыв: цель в creative/spectator");
            return;
        }
        String id = token.readId(player.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            dbg(player, "обрыв: нет класса");
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, id);
        if (def == null) {
            dbg(player, "обрыв: свиток чужого класса");
            return;
        }
        if (!plugin.getAbilities().isTargeted(def.id())) {
            dbg(player, "обрыв: абилка не точечная");
            return;
        }
        event.setCancelled(true);
        if (plugin.getAbilities().tryCastTargeted(player, target, def)) {
            plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
            dbg(player, "каст в цель: " + def.id());
        } else {
            dbg(player, "обрыв: tryCastTargeted=false");
        }
    }

    /** FIX 1.5.1: чистка per-player состояния на выход. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAbilities().clearAttempts(event.getPlayer().getUniqueId());
        plugin.getSpecService().clearNotifyState(event.getPlayer().getUniqueId());
    }

    private void dbg(Player player, String msg) {
        if (plugin.getRaskolConfig().isTargetCastDebug()) {
            player.sendMessage(Component.text("[dbg] " + msg, NamedTextColor.DARK_GRAY));
        }
    }
}
