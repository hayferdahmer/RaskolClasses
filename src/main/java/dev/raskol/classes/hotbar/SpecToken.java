// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Свиток активки специализации (1.4.0, Пакет 2.1).
 * Предмет с PDC-меткой raskolclasses:spec_ability; ПКМ = каст спеки.
 * Материал — NETHER_STAR, визуально отделён от классовых свитков (AMETHYST_SHARD).
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

        meta.displayName(TextFx.gradient(
                spec.symbol() + " Свиток: " + spec.displayName(),
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
}
