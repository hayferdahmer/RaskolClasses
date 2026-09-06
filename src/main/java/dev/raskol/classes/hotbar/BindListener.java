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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * ПКМ со свитком = каст в себя; ЛКМ по союзнику = каст в цель.
 * 1.5.0 / Пакет 3: после tryCast запускаем fx.onAttempt — звук/партикл
 * каста свитком (раньше VFX были только у /rc 1–5).
 */
public final class BindListener implements Listener {

    private final RaskolClasses plugin;
    private final AbilityToken token;

    public BindListener(RaskolClasses plugin, AbilityToken token) {
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
        AbilityDef def = token.readAbility(player.getInventory().getItemInMainHand());
        if (def == null) {
            return;
        }
        event.setCancelled(true);
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(
                    "Класс не выбран — способности недоступны", NamedTextColor.RED));
            return;
        }
        if (!plugin.getAbilities().tryCast(player, def)) {
            return;
        }
        // 1.5.0 / Пакет 3: VFX каста свитком
        plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        AbilityDef def = token.readAbility(player.getInventory().getItemInMainHand());
        if (def == null) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        if (!def.isTargeted()) {
            return;
        }
        event.setCancelled(true);
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(
                    "Класс не выбран — способности недоступны", NamedTextColor.RED));
            return;
        }
        if (!plugin.getAbilities().tryCastOn(player, def, target)) {
            return;
        }
        // 1.5.0 / Пакет 3: VFX каста свитком в цель
        plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        AbilityDef def = token.readAbility(player.getInventory().getItemInMainHand());
        if (def == null || !def.isTargeted()) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.getAbilities().tryCastOn(player, def, target)) {
            return;
        }
        // 1.5.0 / Пакет 3: VFX каста свитком в цель через ЛКМ
        plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
    }
}
