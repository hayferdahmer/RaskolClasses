// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.service.ItemSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 1.15.0: GUI-браузер предметов реестра (/rc item browser).
 * Показывает все доступные ItemDefinition с пагинацией.
 * Клик по предмету — выдача игроку (если есть право raskolclasses.item.give).
 */
public final class ItemBrowserMenu implements Listener {

    private static final int PAGE_SIZE = 45;

    private final RaskolClasses plugin;
    private final ItemSystem items;

    public ItemBrowserMenu(RaskolClasses plugin, ItemSystem items) {
        this.plugin = plugin;
        this.items = items;
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private final List<String> itemIds;
        private Inventory inventory;

        Holder(UUID owner, int page, List<String> itemIds) {
            this.owner = owner;
            this.page = page;
            this.itemIds = itemIds;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player, int page) {
        List<String> ids = items.registry().all().stream()
                .map(ItemDefinition::id).toList();
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(player.getUniqueId(), safe, ids);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("📦 Браузер предметов · " + (safe + 1) + "/" + pages, NamedTextColor.GOLD));
        holder.inventory = inv;

        int offset = safe * PAGE_SIZE;
        for (int i = offset; i < Math.min(offset + PAGE_SIZE, ids.size()); i++) {
            inv.setItem(i - offset, iconFor(ids.get(i)));
        }
        inv.setItem(45, nav(Material.ARROW, "Назад", safe > 0));
        inv.setItem(49, nav(Material.BARRIER, "Закрыть", true));
        inv.setItem(53, nav(Material.ARROW, "Далее", safe < pages - 1));
        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane());
            }
        }
        player.openInventory(inv);
    }

    private ItemStack iconFor(String itemId) {
        ItemDefinition def = items.registry().get(itemId);
        if (def == null) {
            return pane();
        }
        ItemStack icon = items.factory().create(def, 12345L);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ID: " + def.id(), NamedTextColor.GRAY));
        lore.add(Component.text("Уровень: " + def.itemLevel(), NamedTextColor.AQUA));
        lore.add(Component.text("Редкость: " + def.rarity(), NamedTextColor.YELLOW));
        if (def.playerClass() != null) {
            lore.add(Component.text("Класс: " + def.playerClass().getDisplayName(), NamedTextColor.GREEN));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Клик — получить предмет", NamedTextColor.WHITE));
        icon.editMeta(meta -> meta.lore(lore));
        return icon;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.owner)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            open(player, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = holder.page * PAGE_SIZE + slot;
        if (index >= holder.itemIds.size()) {
            return;
        }
        String itemId = holder.itemIds.get(index);
        if (player.hasPermission("raskolclasses.item.give")) {
            ItemStack created = items.create(itemId, items.registry().get(itemId).itemLevel());
            player.getInventory().addItem(created).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(Component.text("Получен предмет: " + itemId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
        }
        open(player, holder.page);
    }

    private static ItemStack nav(Material material, String name, boolean active) {
        ItemStack stack = new ItemStack(active ? material : Material.GRAY_DYE);
        stack.editMeta(meta -> meta.displayName(Component.text(name,
                active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)));
        return stack;
    }

    private static ItemStack pane() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.displayName(Component.text(" ")));
        return stack;
    }
}
