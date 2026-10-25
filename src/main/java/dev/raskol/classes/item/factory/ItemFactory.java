// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.factory;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.SetDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates item stacks from validated registry definitions.
 * 1.15.1-C: все генерируемые предметы получают флаг unbreakable (броня и оружие
 * не ломаются); legacy-предметы 1.15.0 без флага страхует UnbreakableGuard.
 * 1.15.3: lore-маркер неразрушаемости читается из messages.item.unbreakable-lore
 * (локализация без пересборки).
 */
public final class ItemFactory {
    private final RaskolClasses plugin;
    private final ItemPdc pdc;

    public ItemFactory(RaskolClasses plugin) {
        this.plugin = plugin;
        pdc = new ItemPdc(plugin);
    }

    public ItemStack create(ItemDefinition def, long seed) {
        Material material = Material.matchMaterial(def.material());
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Invalid material: " + def.material());
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(color(def.displayName()),
                def.rarity() == ItemRarity.EPIC ? NamedTextColor.LIGHT_PURPLE :
                        def.rarity() == ItemRarity.RARE ? NamedTextColor.BLUE : NamedTextColor.WHITE));
        List<Component> lore = new ArrayList<>();
        for (String line : def.lore()) {
            lore.add(Component.text(color(line), NamedTextColor.GRAY));
        }
        if (def.setId() != null) {
            SetDefinition set = plugin.getItems().registry().sets().get(def.setId());
            lore.add(Component.empty());
            lore.add(Component.text("Комплект: " + (set == null ? def.setId() : set.displayName()), NamedTextColor.GOLD));
            if (set != null) {
                set.bonuses().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> lore.add(Component.text(
                                entry.getKey() + " предмета: " + entry.getValue().description(),
                                NamedTextColor.DARK_PURPLE)));
            }
        }
        String requirement = def.playerClass() == null ? "Любой класс" : def.playerClass().getDisplayName();
        if (def.spec() != null) {
            requirement += " · " + def.spec();
        }
        requirement += " · уровень " + def.levelRequirement();
        lore.add(Component.text("Требуется: " + requirement, NamedTextColor.RED));
        Map<ItemStat, Double> affixValues = AffixRoller.roll(def.affixPool(), def.affixCount(), def.itemLevel(), seed);
        def.baseStats().forEach((stat, value) -> lore.add(Component.text(
                "+" + String.format(java.util.Locale.ROOT, "%.1f", value) + " " + stat.id(),
                NamedTextColor.GREEN)));
        affixValues.forEach((stat, value) -> lore.add(Component.text(
                "+" + String.format(java.util.Locale.ROOT, "%.1f", value) + " " + stat.id() + " (аффикс)",
                NamedTextColor.AQUA)));
        lore.add(Component.text(def.rarity() + " · ур. " + def.itemLevel(), NamedTextColor.DARK_GRAY));
        // 1.15.1-C: предмет не теряет прочность; 1.15.3: маркер из messages.item.unbreakable-lore
        lore.add(Component.text(plugin.getRaskolConfig()
                .message("item.unbreakable-lore", "⛨ Неразрушаемый"), NamedTextColor.GRAY));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(def.glow());
        meta.setUnbreakable(true);
        if (def.customModelData() != null) {
            meta.setCustomModelData(def.customModelData());
        }
        item.setItemMeta(meta);
        pdc.write(item, def, seed, affixValues);
        return item;
    }

    public ItemPdc pdc() {
        return pdc;
    }

    private static String color(String value) {
        return value.replace('&', '§');
    }
}
