// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Свиток активки специализации (1.4.0 + 1.5.1).
 * FIX 1.5.1: stripScrolls — сжигание свитков старого пути при респеце.
 * 1.6.11: isSpecScroll — проверка PDC-ключа для санитизатора.
 */
public final class SpecToken {

    private final RaskolClasses plugin;
    private final NamespacedKey key;

    public SpecToken(RaskolClasses plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "spec_ability");
    }

    public ItemStack create(Spec spec, PlayerClass pc) {
        SpecRegistry.SpecDef def = plugin.getSpecRegistry().get(spec);
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextFx.gradient("⚔ Свиток: " + spec.displayName(),
                theme.primary(), theme.secondary()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Специализация: " + spec.displayName(), NamedTextColor.GRAY));
        if (def != null) {
            lore.add(Component.text(def.activeDescription(), NamedTextColor.YELLOW));
            lore.add(Component.text("Цена: " + def.activeCost() + " рес. · КД: "
                    + def.activeCooldown() + "с", NamedTextColor.GRAY));
        }
        lore.add(Component.text(""));
        lore.add(Component.text("ПКМ в хотбаре — активация", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, spec.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Прочитать спеку из предмета; null если это не свиток спеки. */
    public Spec readSpec(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
        return Spec.fromId(id);
    }

    /**
     * 1.6.11: предмет — свиток спеки (имеет наш PDC-ключ spec_ability).
     * Используется ScrollSanitizer: если это свиток, но readSpec() = null
     * (id не резолвится в Spec) — свиток битый, его надо удалить.
     */
    public boolean isSpecScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(key, PersistentDataType.STRING);
    }

    /** FIX 1.5.1: убрать из инвентаря все свитки указанной спеки; вернуть число. */
    public int stripScrolls(Player player, Spec spec) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            if (readSpec(item) == spec) {
                player.getInventory().setItem(i, null);
                count++;
            }
        }
        return count;
    }
}
