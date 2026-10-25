// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.craft;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
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
 * 1.15.4: GUI крафта (/rc item craft): страницы рецептов из crafting.yml.
 * Иконка — превью цели (factory.create с фикс-seed 777, без ItemCreatedEvent),
 * lore — стоимость (фрагменты + монеты) и подсказка клика. Клик = попытка крафта
 * с сообщением результата/отказа (CraftingService.notifyFailure).
 * Регистрация слушателя — в ItemSystem.start() (подшаг B).
 */
public final class CraftingGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final long PREVIEW_SEED = 777L;

    private final RaskolClasses plugin;
    private final CraftingService crafting;
    private final FragmentRegistry fragments;

    public CraftingGui(RaskolClasses plugin, CraftingService crafting, FragmentRegistry fragments) {
        this.plugin = plugin;
        this.crafting = crafting;
        this.fragments = fragments;
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private final List<String> recipeIds;
        private Inventory inventory;

        Holder(UUID owner, int page, List<String> recipeIds) {
            this.owner = owner;
            this.page = page;
            this.recipeIds = recipeIds;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player, int page) {
        List<String> ids = crafting.recipes().stream().map(CraftingService.Recipe::id).toList();
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(player.getUniqueId(), safe, ids);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("⚒ Крафт · " + (safe + 1) + "/" + pages, NamedTextColor.DARK_PURPLE));
        holder.inventory = inv;

        int offset = safe * PAGE_SIZE;
        for (int i = offset; i < Math.min(offset + PAGE_SIZE, ids.size()); i++) {
            inv.setItem(i - offset, iconFor(player, crafting.recipe(ids.get(i))));
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

    private ItemStack iconFor(Player player, CraftingService.Recipe recipe) {
        if (recipe == null) {
            return pane();
        }
        ItemDefinition def = plugin.getItems().registry().get(recipe.target());
        if (def == null) {
            return pane();
        }
        ItemStack icon = plugin.getItems().factory().create(def, PREVIEW_SEED);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Цель: " + def.displayName(), NamedTextColor.GOLD));
        recipe.fragments().forEach((fragId, count) -> {
            Fragment fragment = fragments.byId(fragId);
            int have = fragment == null ? 0 : fragments.countInInventory(player, fragment);
            NamedTextColor color = have >= count ? NamedTextColor.GREEN : NamedTextColor.RED;
            lore.add(Component.text("  " + (fragment == null ? fragId : fragment.displayName())
                    + ": " + have + "/" + count, color));
        });
        if (recipe.coins() > 0) {
            lore.add(Component.text("  Монеты: " + recipe.coins(), NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Клик — создать предмет", NamedTextColor.AQUA));
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
        if (index >= holder.recipeIds.size()) {
            return;
        }
        CraftingService.Recipe recipe = crafting.recipe(holder.recipeIds.get(index));
        CraftingService.CraftResult result = crafting.craft(player, holder.recipeIds.get(index));
        if (result != CraftingService.CraftResult.OK) {
            crafting.notifyFailure(player, result, recipe);
        }
        open(player, holder.page); // освежить счётчики фрагментов в lore
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
