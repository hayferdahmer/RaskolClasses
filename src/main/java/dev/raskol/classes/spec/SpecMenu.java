// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.util.TextFx;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI выбора специализации (1.4.0, Пакет 1).
 * 9 слотов: две спеки по краям, описание в центре.
 */
public final class SpecMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Spec specA;
    private final Spec specB;

    private SpecMenu(RaskolClasses plugin, Player player, PlayerClass pc) {
        Spec[] specs = Spec.forClass(pc);
        this.specA = specs[0];
        this.specB = specs[1];

        this.inventory = Bukkit.createInventory(this, 9,
                TextFx.gradient("Выбор специализации: " + pc.getDisplayName(),
                        plugin.getRaskolConfig().themeOf(pc).primary(),
                        plugin.getRaskolConfig().themeOf(pc).secondary()));

        inventory.setItem(2, buildSpecItem(plugin, specA, pc));
        inventory.setItem(6, buildSpecItem(plugin, specB, pc));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            if (i != 2 && i != 6) {
                inventory.setItem(i, filler);
            }
        }
    }

    private ItemStack buildSpecItem(RaskolClasses plugin, Spec spec, PlayerClass pc) {
        SpecRegistry.SpecDef def = plugin.getSpecRegistry().get(spec);
        if (def == null) {
            return new ItemStack(Material.BARRIER);
        }

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(spec.displayName(), pc.getColor()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(spec.role(), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("Пассив: ", NamedTextColor.YELLOW)
                .append(Component.text(def.passiveDescription(), NamedTextColor.GRAY)));
        lore.add(Component.text("Актив: ", NamedTextColor.YELLOW)
                .append(Component.text(def.activeDescription(), NamedTextColor.GRAY)));
        lore.add(Component.text(""));
        lore.add(Component.text("Клик = выбрать", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static void open(RaskolClasses plugin, Player player, PlayerClass pc) {
        player.openInventory(new SpecMenu(plugin, player, pc).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static final class ClickHandler implements Listener {
        private final RaskolClasses plugin;

        public ClickHandler(RaskolClasses plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof SpecMenu menu)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            int slot = event.getRawSlot();
            if (slot == 2) {
                player.closeInventory();
                plugin.getSpecService().choose(player, menu.specA);
            } else if (slot == 6) {
                player.closeInventory();
                plugin.getSpecService().choose(player, menu.specB);
            }
        }
    }
}
