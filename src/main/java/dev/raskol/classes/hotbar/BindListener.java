// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Слушатель свитков в хотбаре (1.5.x → 1.9.0-fix6).
 *
 * 1.9.0-fix6 (баг «дёргается рука»): вместо полного setCancelled(true) используем
 * точечные DENY: setUseInteractedBlock(DENY) + setUseItemInHand(DENY). Полный cancel
 * давал клиентский десинк предсказания использования предмета (визуальное дёргание
 * руки); точечные DENY гасят только взаимодействие с блоком/использование предмета,
 * не ломая событие для других плагинов и клиента.
 *
 * ПКМ — каст в себя (конвейер tryCast); ЛКМ по живой цели — точечный каст
 * (targeted-абилки); остальное не перехватываем.
 */
public final class BindListener implements Listener {

    private static final double TARGET_RANGE = 20.0;

    private final RaskolClasses plugin;
    private final AbilityToken tokens;

    public BindListener(RaskolClasses plugin, AbilityToken tokens) {
        this.plugin = plugin;
        this.tokens = tokens;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String abilityId = tokens.readId(item);
        if (abilityId == null) {
            return;
        }
        Player player = event.getPlayer();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-class-cast", "Класс не выбран — способности недоступны"), NamedTextColor.GRAY));
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
        if (def == null) {
            player.sendMessage(Component.text("Способность не найдена: " + abilityId, NamedTextColor.RED));
            return;
        }

        if (right) {
            denyUse(event);
            if (plugin.getAbilities().tryCast(player, def)) {
                plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
            }
            return;
        }

        if (!plugin.getAbilities().isTargeted(def.id())) {
            return;
        }
        Entity target = player.getTargetEntity((int) TARGET_RANGE);
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        denyUse(event);
        if (plugin.getAbilities().tryCastTargeted(player, living, def)) {
            plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
        }
    }

    /** 1.9.0-fix6: точечный отказ вместо полного cancel — без десинка руки. */
    private void denyUse(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }
}
