// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1.11.4 (P4b): общие предмет-билдеры Книги: рамка, навигация, эмблема,
 * инфо-предметы, gear-предметы. Тексты — через messages.book.* с фолбэками.
 */
public final class BookItems {

    private BookItems() {
    }

    public static String msg(RaskolClasses plugin, String key, String def) {
        return plugin.getRaskolConfig().message(key, def);
    }

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    public static ItemStack tabIcon(RaskolClasses plugin, Material material,
                                    String key, String def, boolean active) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(msg(plugin, key, def),
                    active ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            if (active) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.lore(List.of(Component.text(msg(plugin, "book.tab.hint", "Клик — открыть вкладку"),
                    NamedTextColor.DARK_GRAY)));
        });
        return item;
    }

    public static ItemStack closeIcon() {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> meta.displayName(Component.text("Закрыть", NamedTextColor.RED)));
        return item;
    }

    public static ItemStack infoItem(Material material, String title, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(title, NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(lore);
        });
        return item;
    }

    public static ItemStack gearItem(ItemStack source, String cls, String rarity, String slotName) {
        ItemStack item = source.clone();
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text("Слот: " + slotName, NamedTextColor.GRAY));
            lore.add(Component.text("Класс: " + cls + " · Редкость: " + rarity, NamedTextColor.GRAY));
            meta.lore(lore);
        });
        return item;
    }

    /** 1.10.0: покрыт WARLOCK (фолиант). */
    public static Material emblemMaterial(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> Material.IRON_SWORD;
            case HUNTER -> Material.BOW;
            case PRIEST -> Material.BELL;
            case MAGE -> Material.BLAZE_ROD;
            case ROGUE -> Material.SHEARS;
            case WARLOCK -> Material.WRITABLE_BOOK;
        };
    }

    /** 1.10.0: покрыт WARLOCK (Скверна); 1.11.2: числа сверены с config 1.11.2. */
    public static String resourceRulesDef(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "Ярость: −5/с вне боя; +10 за урон (нанёс/получил)";
            case HUNTER -> "Концентрация: +5/с вне боя; +4 за попадание в бою";
            case PRIEST -> "Свет: +2/с всегда; +5 за событие лечения";
            case MAGE -> "Мана: +1/1.5/2/2.5 в секунду по порогам 25/50/75";
            case ROGUE -> "Энергия: +10/с";
            case WARLOCK -> "Скверна: +9 за урон, +5 при получении, +15 за убийство, +25 за Чёрное Слово; вне боя −4/с до 0. При 75+ урон ×1.2; при 100 — тик 1% maxHP/с";
        };
    }

    public static ItemStack emblem(RaskolClasses plugin, Player player, PlayerClass pc) {
        ItemStack item = new ItemStack(emblemMaterial(pc));
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(pc.getDisplayName(),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin,
                    "book.resource." + pc.name().toLowerCase(Locale.ROOT), resourceRulesDef(pc)),
                    NamedTextColor.GRAY));
            lore.add(Component.text(msg(plugin, "book.emblem.resource", "Ресурс сейчас: {value}/100")
                    .replace("{value}", String.valueOf(
                            (int) plugin.getResources().getValue(player.getUniqueId()))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.emblem.crown", "Корона: {name}")
                    .replace("{name}", plugin.getFlavorService()
                            .crownDisplayName(player.getUniqueId())),
                    NamedTextColor.GOLD));
            meta.lore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return item;
    }
}
