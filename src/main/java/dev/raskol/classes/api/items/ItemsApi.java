// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.api.items;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Stable public API for RaskolMobs and other plugins to create configured items. */
public final class ItemsApi {
    /** Public API contract revision. */
    public static final int API_VERSION = 1;
    private final RaskolClasses plugin;

    public ItemsApi(RaskolClasses plugin) { this.plugin = plugin; }

    public ItemStack create(String itemId, int itemLevel, long seed) {
        return plugin.getItems().create(itemId, itemLevel, seed);
    }

    public ItemStack createFromTemplate(String templateId, int level, ItemRarity rarity, long seed) {
        String prefix = templateId.toLowerCase(java.util.Locale.ROOT) + "_" + level + "_" + rarity.name().toLowerCase(java.util.Locale.ROOT);
        ItemDefinition d = plugin.getItems().registry().get(prefix);
        if (d == null) throw new IllegalArgumentException("Unknown item template/variant: " + prefix);
        return plugin.getItems().create(d.id(), level, seed);
    }

    public ItemRarity rollRarity(Map<ItemRarity, Double> weights, long seed) {
        double total = weights.values().stream()
                .filter(v -> v != null && Double.isFinite(v) && v > 0)
                .mapToDouble(Double::doubleValue).sum();
        if (total <= 0) throw new IllegalArgumentException("At least one positive rarity weight is required");
        java.util.SplittableRandom random = new java.util.SplittableRandom(seed);
        double point = random.nextDouble(total);
        for (var e : weights.entrySet()) {
            if (e.getValue() != null && Double.isFinite(e.getValue()) && e.getValue() > 0) {
                point -= e.getValue();
                if (point < 0) return e.getKey();
            }
        }
        return ItemRarity.COMMON;
    }

    public ItemDefinition definitionOf(ItemStack stack) {
        return plugin.getItems().definitionOf(stack);
    }
}
