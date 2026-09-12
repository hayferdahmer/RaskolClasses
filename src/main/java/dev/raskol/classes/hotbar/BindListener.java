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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 1.9.0-fix: слушатель клика по свитку в хотбаре.
 * Вызывает AbilityRegistry.tryCast (конвейер со списанием ресурса/кулдауном),
 * а не кастер напрямую.
 */
public final class BindListener implements Listener {

    private final RaskolClasses plugin;
    private final AbilityToken tokens;

    public BindListener(RaskolClasses plugin, AbilityToken tokens) {
        this.plugin = plugin;
        this.tokens = tokens;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
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
        // 1.9.0-fix: вызов через конвейер (списание ресурса, кулдаун, гейты)
        if (plugin.getAbilities().tryCast(player, def)) {
            plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
        }
    }
}
