// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.listener;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.service.ItemStatAggregator;
import dev.raskol.classes.item.service.ItemSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 1.15.1-D: запреты экипировки по классу/спеке/уровню (пункт 5 дорожной карты).
 *
 * Точки перехвата: прямой клик в armor/offhand-слот, shift-автоэкипировка,
 * drag в armor-слот, F-swap в offhand. Main-hand НЕ ограничиваем: держать
 * чужое оружие можно, статы оно не даёт (ItemStatAggregator.eligible в reconcile).
 *
 * Grace-правило: уже надетый предмет не снимается принудительно при смене
 * класса/спека — он перестаёт давать статы (unmet в снапшоте) и не может быть
 * надет снова после снятия. Предметы не теряются.
 *
 * Сообщения игроку — inline до выноса в messages.yml (1.15.2): причина
 * дифференцируется (класс / спек / уровень).
 */
public final class EquipGuard implements Listener {

    private final RaskolClasses plugin;
    private final ItemSystem items;

    public EquipGuard(RaskolClasses plugin, ItemSystem items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // прямой клик курсором в экипировочный слот
        if (isEquipSlot(event.getView().getTopInventory(), event.getRawSlot())
                && blocked(player, event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        // shift-клик: автоэкипировка брони из инвентаря
        if (event.isShiftClick() && event.getClickedInventory() instanceof PlayerInventory
                && blocked(player, event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (isEquipSlot(event.getView().getTopInventory(), entry.getKey())
                    && blocked(player, entry.getValue())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (blocked(event.getPlayer(), event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /** true если предмет наш и игрок не удовлетворяет требованиям (сообщение уже отправлено). */
    private boolean blocked(Player player, ItemStack stack) {
        if (stack == null || !items.pdc().isItem(stack)) {
            return false;
        }
        ItemDefinition def = items.definitionOf(stack);
        if (def == null) {
            return false;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return false; // без класса статы не начисляются; экипировку не блокируем
        }
        int level = plugin.getCharacterLevels().characterLevel(player.getUniqueId());
        String spec = plugin.getSpec2Service().mainSpec(player.getUniqueId());
        if (ItemStatAggregator.eligible(def, pc, spec, level)) {
            return false;
        }
        player.sendMessage(Component.text("⛔ «" + def.displayName() + "»: " + reason(def, pc, spec, level),
                NamedTextColor.RED));
        return true;
    }

    private static String reason(ItemDefinition def, PlayerClass pc, String spec, int level) {
        if (def.playerClass() != null && def.playerClass() != pc) {
            return "примет только " + def.playerClass().getDisplayName();
        }
        if (def.spec() != null && !def.spec().equalsIgnoreCase(spec)) {
            return "требуется спек " + def.spec();
        }
        return "требуется уровень " + def.levelRequirement() + " (у вас " + level + ")";
    }

    /** Armor-слоты 5–8 и offhand 45 в окне крафта игрока (CRAFTING-view). */
    private static boolean isEquipSlot(org.bukkit.inventory.Inventory top, int rawSlot) {
        if (top.getType() != InventoryType.CRAFTING) {
            return false;
        }
        return (rawSlot >= 5 && rawSlot <= 8) || rawSlot == 45;
    }
}
