// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * ПКМ со свитком = каст в себя (1.3.1 + 1.5.0 Пакет 3).
 * Свиток определяется по PDC-ключу raskolclasses.ability.
 * 1.5.0 / Пакет 3: после tryCast запускаем fx.onAttempt — звук/партикл каста.
 */
public final class BindListener implements Listener {

    private final RaskolClasses plugin;
    private final NamespacedKey abilityKey;

    public BindListener(RaskolClasses plugin, dev.raskol.classes.hotbar.AbilityToken token) {
        this.plugin = plugin;
        this.abilityKey = new NamespacedKey(plugin, "ability");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        AbilityDef def = readAbility(item);
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

    private AbilityDef readAbility(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(abilityKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        // Формат PDC: "<CLASS>:<ability_id>"
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            PlayerClass pc = PlayerClass.valueOf(parts[0]);
            return plugin.getAbilities().getById(pc, parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
