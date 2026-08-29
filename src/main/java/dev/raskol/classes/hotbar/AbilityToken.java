// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Пакет 6: свиток способности — предмет с PDC-меткой raskolclasses:ability.
 * ПКМ со свитком в руке = каст привязанной способности (BindListener).
 * Метка в PersistentDataContainer переживает рестарты и не путается с
 * обычными предметами.
 */
public final class AbilityToken {

    private final RaskolClasses plugin;
    private final NamespacedKey key;

    public AbilityToken(RaskolClasses plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ability");
    }

    /** Создаёт свиток способности в теме класса. */
    public ItemStack create(AbilityDef def, PlayerClass pc) {
        Material material = parseMaterial(plugin.getRaskolConfig().bindMaterial());
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient("✦ " + def.displayName(),
                    theme.primary(), theme.secondary()));
            meta.lore(List.of(
                    Component.text("ПКМ — каст", NamedTextColor.GRAY),
                    Component.text("Слот способности: " + def.slot(),
                            NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, def.id());
        });
        return item;
    }

    /** Читает id способности из предмета; null, если это не свиток. */
    public String readId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key,
                PersistentDataType.STRING);
    }

    private Material parseMaterial(String name) {
        if (name == null) {
            return Material.AMETHYST_SHARD;
        }
        Material parsed = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return parsed != null ? parsed : Material.AMETHYST_SHARD;
    }
}
