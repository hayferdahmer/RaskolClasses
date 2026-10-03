// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.gui.book.AbilitiesTab;
import dev.raskol.classes.gui.book.BookItems;
import dev.raskol.classes.gui.book.BookSlots;
import dev.raskol.classes.gui.book.BookTabView;
import dev.raskol.classes.gui.book.ClassTab;
import dev.raskol.classes.gui.book.GearTab;
import dev.raskol.classes.gui.book.RenderCtx;
import dev.raskol.classes.gui.book.SpecsTab;
import dev.raskol.classes.gui.book.TalentsTab;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Книга класса: фасад + каркас (рамка, эмблема, навигация) и роутинг кликов.
 * 1.11.4 (P4b): контент вкладок вынесен в gui/book/*Tab.
 * 1.14.0 (Б3): SpecsTab и TalentsTab переключены на Spec2.
 */
public final class ClassBook implements InventoryHolder {

    public enum Tab { ABILITIES, SPECS, CLASS, TALENTS, GEAR }

    private static final Map<Tab, BookTabView> VIEWS = Map.of(
            Tab.ABILITIES, new AbilitiesTab(),
            Tab.SPECS, new SpecsTab(),
            Tab.CLASS, new ClassTab(),
            Tab.TALENTS, new TalentsTab(),
            Tab.GEAR, new GearTab());

    private Inventory inventory;
    private final UUID owner;
    private final Tab tab;

    private ClassBook(UUID owner, Tab tab) {
        this.owner = owner;
        this.tab = tab;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(RaskolClasses plugin, Player player, Tab tab) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-class", "Класс не выбран — посетите герольда"), NamedTextColor.GRAY));
            return;
        }
        ClassBook book = new ClassBook(player.getUniqueId(), tab);
        Inventory inv = Bukkit.createInventory(book, BookSlots.SIZE,
                Component.text(plugin.getRaskolConfig().message("book.title", "Книга класса: "))
                        .append(Component.text(pc.getDisplayName(), pc.getColor())));
        book.inventory = inv;
        book.fill(plugin, player, pc);
        player.openInventory(inv);
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        if (theme != null && theme.sound() != null) {
            plugin.getFx().playSound(player.getLocation(), theme.sound(), 0.5f, 1.1f);
        }
    }

    public void refresh(RaskolClasses plugin, Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null || inventory == null) {
            return;
        }
        fill(plugin, player, pc);
    }

    private void fill(RaskolClasses plugin, Player player, PlayerClass pc) {
        for (int i = 0; i < BookSlots.SIZE; i++) {
            inventory.setItem(i, BookItems.filler());
        }
        inventory.setItem(BookSlots.SLOT_EMBLEM, BookItems.emblem(plugin, player, pc));
        inventory.setItem(BookSlots.SLOT_TAB_ABILITIES, BookItems.tabIcon(plugin, Material.BOOK,
                "book.tab.abilities", "Способности", tab == Tab.ABILITIES));
        inventory.setItem(BookSlots.SLOT_TAB_SPECS, BookItems.tabIcon(plugin, Material.NETHER_STAR,
                "book.tab.specs", "Специализации", tab == Tab.SPECS));
        inventory.setItem(BookSlots.SLOT_TAB_CLASS, BookItems.tabIcon(plugin, Material.NAME_TAG,
                "book.tab.class", "Класс и пассивки", tab == Tab.CLASS));
        inventory.setItem(BookSlots.SLOT_TAB_TALENTS, BookItems.tabIcon(plugin, Material.END_CRYSTAL,
                "book.tab.talents", "Деревья путей", tab == Tab.TALENTS));
        inventory.setItem(BookSlots.SLOT_TAB_GEAR, BookItems.tabIcon(plugin, Material.ANVIL,
                "book.tab.gear", "Шмот и сеты", tab == Tab.GEAR));
        inventory.setItem(BookSlots.SLOT_CLOSE, BookItems.closeIcon());

        BookTabView view = VIEWS.get(tab);
        if (view != null) {
            view.render(new RenderCtx(plugin, player, pc, inventory, this));
        }
    }

    public static final class ClickHandler implements Listener {

        private final RaskolClasses plugin;

        public ClickHandler(RaskolClasses plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ClassBook book)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            int slot = event.getRawSlot();
            if (slot != event.getSlot()) {
                return;
            }
            boolean left = event.getClick() == ClickType.LEFT;
            boolean right = event.getClick() == ClickType.RIGHT;
            if (!left && !right) {
                return;
            }
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            if (pc == null) {
                return;
            }
            if (slot == BookSlots.SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == BookSlots.SLOT_TAB_ABILITIES) { open(plugin, player, Tab.ABILITIES); return; }
            if (slot == BookSlots.SLOT_TAB_SPECS) { open(plugin, player, Tab.SPECS); return; }
            if (slot == BookSlots.SLOT_TAB_CLASS) { open(plugin, player, Tab.CLASS); return; }
            if (slot == BookSlots.SLOT_TAB_TALENTS) { open(plugin, player, Tab.TALENTS); return; }
            if (slot == BookSlots.SLOT_TAB_GEAR) { open(plugin, player, Tab.GEAR); return; }

            BookTabView view = VIEWS.get(book.tab);
            if (view != null) {
                view.onClick(new RenderCtx(plugin, player, pc, book.inventory, book), slot, left, right);
            }
        }
    }
}
