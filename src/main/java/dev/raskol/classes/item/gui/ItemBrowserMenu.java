// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.15.0: GUI-браузер предметов реестра (/rc item browser [запрос]).
 * 1.15.5: фильтры класс/редкость кнопками-циклами (слоты 46/47), запрос из
 * аргумента команды (слот 48 показывает и сбрасывает), пагинация 45/53.
 * Chat-capture НЕ используется: поиск только через аргумент команды —
 * меньше состояний сессии, нет утечек при выходе игрока из чата.
 * Клик по предмету — выдача (право raskolclasses.item.give).
 * Pure-фильтр matches() вынесен статиком для selftest 139.
 */
public final class ItemBrowserMenu implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLASS = 46;
    private static final int SLOT_RARITY = 47;
    private static final int SLOT_QUERY = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    private final RaskolClasses plugin;
    private final ItemSystem items;

    public ItemBrowserMenu(RaskolClasses plugin, ItemSystem items) {
        this.plugin = plugin;
        this.items = items;
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private final String query;
        private final String classFilter;
        private final String rarityFilter;
        private final List<String> itemIds;
        private Inventory inventory;

        Holder(UUID owner, int page, String query, String classFilter,
               String rarityFilter, List<String> itemIds) {
            this.owner = owner;
            this.page = page;
            this.query = query;
            this.classFilter = classFilter;
            this.rarityFilter = rarityFilter;
            this.itemIds = itemIds;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Pure: проходит ли определение через фильтры (query/class/rarity). Selftest 139. */
    public static boolean matches(ItemDefinition def, String query, String classFilter, String rarityFilter) {
        if (def == null) {
            return false;
        }
        if (classFilter != null && !"ALL".equals(classFilter)) {
            if (def.playerClass() == null || !def.playerClass().name().equals(classFilter)) {
                return false;
            }
        }
        if (rarityFilter != null && !"ALL".equals(rarityFilter)) {
            if (!def.rarity().name().equals(rarityFilter)) {
                return false;
            }
        }
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            if (!def.id().toLowerCase(Locale.ROOT).contains(q)
                    && !def.displayName().toLowerCase(Locale.ROOT).contains(q)) {
                return false;
            }
        }
        return true;
    }

    public void open(Player player, int page, String query, String classFilter, String rarityFilter) {
        List<String> ids = items.registry().all().stream()
                .filter(def -> matches(def, query, classFilter, rarityFilter))
                .map(ItemDefinition::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(player.getUniqueId(), safe, query, classFilter, rarityFilter, ids);
        String titleSuffix = query == null || query.isBlank() ? "" : " · «" + query + "»";
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("📦 Браузер · " + (safe + 1) + "/" + pages + titleSuffix, NamedTextColor.GOLD));
        holder.inventory = inv;

        int offset = safe * PAGE_SIZE;
        for (int i = offset; i < Math.min(offset + PAGE_SIZE, ids.size()); i++) {
            inv.setItem(i - offset, iconFor(ids.get(i), classFilter, rarityFilter));
        }
        inv.setItem(SLOT_PREV, nav(Material.ARROW, "Назад", safe > 0));
        inv.setItem(SLOT_CLASS, filterButton(Material.NAME_TAG,
                "Класс: " + classFilter, NamedTextColor.AQUA));
        inv.setItem(SLOT_RARITY, filterButton(Material.NETHER_STAR,
                "Редкость: " + rarityFilter, NamedTextColor.LIGHT_PURPLE));
        inv.setItem(SLOT_QUERY, filterButton(Material.PAPER,
                query == null || query.isBlank() ? "Поиск: выключен (клик — сброс)" : "Поиск: «" + query + "» (клик — сброс)",
                NamedTextColor.YELLOW));
        inv.setItem(SLOT_CLOSE, nav(Material.BARRIER, "Закрыть", true));
        inv.setItem(SLOT_NEXT, nav(Material.ARROW, "Далее", safe < pages - 1));
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane());
            }
        }
        player.openInventory(inv);
    }

    private ItemStack iconFor(String itemId, String classFilter, String rarityFilter) {
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
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREV) {
            open(player, holder.page - 1, holder.query, holder.classFilter, holder.rarityFilter);
            return;
        }
        if (slot == SLOT_NEXT) {
            open(player, holder.page + 1, holder.query, holder.classFilter, holder.rarityFilter);
            return;
        }
        if (slot == SLOT_CLASS) {
            open(player, 0, holder.query, nextClass(holder.classFilter), holder.rarityFilter);
            return;
        }
        if (slot == SLOT_RARITY) {
            open(player, 0, holder.query, holder.classFilter, nextRarity(holder.rarityFilter));
            return;
        }
        if (slot == SLOT_QUERY) {
            open(player, 0, null, holder.classFilter, holder.rarityFilter);
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
        ItemDefinition def = items.registry().get(itemId);
        if (def == null) {
            return;
        }
        if (player.hasPermission("raskolclasses.item.give")) {
            ItemStack created = items.create(itemId, def.itemLevel());
            player.getInventory().addItem(created).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage(ItemHover.withPreview(
                    Component.text("Получен предмет: " + def.displayName(), NamedTextColor.GREEN), created));
        } else {
            player.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
        }
        open(player, holder.page, holder.query, holder.classFilter, holder.rarityFilter);
    }

    /** Цикл: ALL → классы по порядку → ALL. */
    private static String nextClass(String current) {
        List<String> cycle = new ArrayList<>();
        cycle.add("ALL");
        for (PlayerClass pc : PlayerClass.values()) {
            cycle.add(pc.name());
        }
        int idx = cycle.indexOf(current);
        return cycle.get((idx + 1) % cycle.size());
    }

    /** Цикл: ALL → редкости по порядку → ALL. */
    private static String nextRarity(String current) {
        List<String> cycle = new ArrayList<>();
        cycle.add("ALL");
        for (ItemRarity r : ItemRarity.values()) {
            cycle.add(r.name());
        }
        int idx = cycle.indexOf(current);
        return cycle.get((idx + 1) % cycle.size());
    }

    private static ItemStack filterButton(Material material, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(Component.text(name, color)));
        return stack;
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
