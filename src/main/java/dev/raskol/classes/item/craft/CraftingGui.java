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
 * 1.15.5-fix: навигация (45/49/53) и освежение после крафта — через scheduleOpen
 * (следующий тик): синхронный openInventory внутри InventoryClickEvent
 * рассинхронизирует курсор клиента («кнопка прилипает к курсору»).
 */
public final class CraftingGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;
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
        inv.setItem(SLOT_PREV, nav(Material.ARROW, "Назад", safe > 0));
        inv.setItem(SLOT_CLOSE, nav(Material.BARRIER, "Закрыть", true));
        inv.setItem(SLOT_NEXT, nav(Material.ARROW, "Далее", safe < pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane());
            }
        }
        player.openInventory(inv);
    }

    /** 1.15.5-fix: открытие GUI только на следующем тике. */
    private void scheduleOpen(Player player, int page) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                open(player, page);
            }
        });
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
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV) {
            scheduleOpen(player, holder.page - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            scheduleOpen(player, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = holder.page * PAGE_SIZE + slot;
        if (index >= holder.recipeIds.size()) {
            return;
        }
        String recipeId = holder.recipeIds.get(index);
        CraftingService.Recipe recipe = crafting.recipe(recipeId);
        CraftingService.CraftResult result = crafting.craft(player, recipeId);
        if (result != CraftingService.CraftResult.OK) {
            crafting.notifyFailure(player, result, recipe);
        }
        scheduleOpen(player, holder.page); // освежить счётчики фрагментов в lore
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
