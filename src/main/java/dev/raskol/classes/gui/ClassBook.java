// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.hook.SetBonusService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Книга класса (GUI) — единое окно управления способностями, спеками, талантами и экипировкой.
 * 1.9.3-r2: добавлена вкладка GEAR (отображение экипировки и активных сетов).
 */
public final class ClassBook {

    public enum Tab {
        ABILITIES, SPECS, CLASS, TALENTS, GEAR
    }

    private static final String INVENTORY_TITLE = "Книга класса";

    private ClassBook() {
    }

    public static void open(RaskolClasses plugin, Player player, Tab tab) {
        Inventory inv = Bukkit.createInventory(new BookHolder(tab), 54,
                Component.text(INVENTORY_TITLE, NamedTextColor.DARK_PURPLE));
        fill(plugin, player, inv, tab);
        player.openInventory(inv);
    }

    private static void fill(RaskolClasses plugin, Player player, Inventory inv, Tab tab) {
        // Заполнение вкладок (упрощённая версия — только GEAR для демонстрации)
        if (tab == Tab.GEAR) {
            fillGearTab(plugin, player, inv);
        }
    }

    private static void fillGearTab(RaskolClasses plugin, Player player, Inventory inv) {
        GearHook gearHook = plugin.getGearHook();
        SetBonusService setBonusService = plugin.getSetBonusService();
        if (gearHook == null || !gearHook.isAvailable()) {
            inv.setItem(22, createInfoItem(Material.BARRIER, "RaskolGear не установлен",
                    List.of("Установите плагин RaskolGear", "для отображения экипировки")));
            return;
        }

        // Оружие в руке
        GearHook.EquippedItem weapon = gearHook.getEquippedWeapon(player);
        if (weapon != null) {
            inv.setItem(10, createGearItem(weapon, "Оружие"));
        } else {
            inv.setItem(10, createInfoItem(Material.WOODEN_SWORD, "Нет оружия",
                    List.of("Возьмите оружие в руку")));
        }

        // Броня
        List<GearHook.EquippedItem> armor = gearHook.getEquippedArmor(player);
        int slot = 19;
        for (GearHook.EquippedItem item : armor) {
            inv.setItem(slot++, createGearItem(item, item.slot()));
        }

        // Активные сеты
        List<SetBonusService.ActiveSet> sets = setBonusService.getActiveSets(player.getUniqueId());
        int setSlot = 37;
        for (SetBonusService.ActiveSet set : sets) {
            String title = set.className() + " " + set.rarity();
            List<String> lore = new ArrayList<>();
            lore.add("Предметов: " + set.count() + "/4");
            if (set.full()) {
                lore.add("✔ Сет активен");
                lore.add("Бонусы применены");
            } else {
                lore.add("✘ Сет неполный");
                lore.add("Нужно ещё " + (4 - set.count()) + " предмет(ов)");
            }
            inv.setItem(setSlot++, createInfoItem(
                    set.full() ? Material.NETHERITE_CHESTPLATE : Material.IRON_CHESTPLATE,
                    title, lore));
        }
    }

    private static ItemStack createGearItem(GearHook.EquippedItem equipped, String slotName) {
        ItemStack item = equipped.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(Component.text(""));
            lore.add(Component.text("Слот: " + slotName, NamedTextColor.GRAY));
            lore.add(Component.text("Класс: " + equipped.className(), NamedTextColor.AQUA));
            lore.add(Component.text("Редкость: " + equipped.rarity(), NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInfoItem(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class ClickHandler implements Listener {
        private final RaskolClasses plugin;

        public ClickHandler(RaskolClasses plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof BookHolder)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    private static final class BookHolder implements InventoryHolder {
        private final Tab tab;

        BookHolder(Tab tab) {
            this.tab = tab;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
