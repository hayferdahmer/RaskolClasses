// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Свиток инсталляции (1.5.0, Пакет 2): предмет = сама инсталляция,
 * ПКМ в хотбаре — постановка. PDC-ключ raskolclasses.install.
 */
public final class InstallToken {

    private final RaskolClasses plugin;
    private final NamespacedKey key;

    public InstallToken(RaskolClasses plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "install");
    }

    public ItemStack create(InstallationType type, PlayerClass pc) {
        ItemStack item = new ItemStack(type.displayItem());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextFx.gradient("⚙ " + type.displayName(),
                plugin.getRaskolConfig().themeOf(pc).primary(),
                plugin.getRaskolConfig().themeOf(pc).secondary()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Инсталляция класса: " + pc.getDisplayName(),
                NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("ПКМ в хотбаре — установить", NamedTextColor.GREEN));
        lore.add(Component.text("Лимит: 2 активных · TTL 60 с", NamedTextColor.GRAY));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Прочитать тип инсталляции из предмета; null если это не свиток. */
    public InstallationType readType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        for (InstallationType t : InstallationType.values()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }
}
