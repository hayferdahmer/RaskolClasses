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
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * ПКМ со свитком = каст в себя; ЛКМ по игроку со свитком точечной абилки = в цель.
 * 1.5.0 / Пакет 3: после каста запускаем fx.onAttempt — звук/партикл.
 * FIX 1.5.0.8: ловим оба события ЛКМ (PlayerInteractEntityEvent +
 * PlayerInteractAtEntityEvent) с приоритетом HIGHEST — иначе Paper 1.21.4
 * стреляет только At-событием для игроков в броне/со щитом, и ЛКМ-бинд
 * жреца молча не срабатывает.
 */
public final class BindListener implements Listener {

    private final RaskolClasses plugin;
    private final AbilityToken token;

    public BindListener(RaskolClasses plugin, AbilityToken token) {
        this.plugin = plugin;
        this.token = token;
    }

    /** ПКМ: каст в себя. */
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
            return;
        }
        if (!plugin.getAbilities().tryCast(player, def)) {
            return;
        }
        plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
    }

    /**
     * ЛКМ по игроку: каст точечной абилки в цель (жрец: хилы/щит).
     * FIX 1.5.0.8: HIGHEST приоритет + обработка обоих событий.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleTargetedCast(event.getPlayer(), event.getRightClicked(),
                event.getHand(), event);
    }

    /**
     * Альтернативное событие ЛКМ (броня/щит/стойка) — то же самое действие.
     * Paper 1.21.4 часто стреляет только этим событием.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleTargetedCast(event.getPlayer(), event.getRightClicked(),
                event.getHand(), event);
    }

    private void handleTargetedCast(Player caster, org.bukkit.entity.Entity clicked,
                                    EquipmentSlot hand, org.bukkit.event.Cancellable cancellable) {
        // Срабатываем только на MAIN_HAND, иначе событие стреляет дважды
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        if (!plugin.getRaskolConfig().isBindEnabled()) {
            return;
        }
        if (!(clicked instanceof Player target)) {
            return;
        }
        // Нельзя лечить/щитовать самого себя через ЛКМ — для этого есть ПКМ
        if (target.equals(caster)) {
            return;
        }
        String id = token.readId(caster.getInventory().getItemInMainHand());
        if (id == null) {
            return;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(caster);
        if (pc == null) {
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, id);
        if (def == null) {
            return;
        }
        if (!plugin.getAbilities().isTargeted(def.id())) {
            return; // не точечная — пусть обрабатывается обычным ПКМ
        }
        cancellable.setCancelled(true);
        if (!plugin.getAbilities().tryCastOn(caster, def, target)) {
            return;
        }
        // 1.5.0 / Пакет 3: VFX каста свитком в цель
        plugin.getFx().onAttempt(caster, def.id(), def.cooldownMillis());
    }
}
