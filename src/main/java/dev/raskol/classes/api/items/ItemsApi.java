// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.api.items;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.inventory.ItemStack;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.service.ItemRegistry;

/**
 * Стабильный публичный API для RaskolMobs и других плагинов для создания
 * сконфигурированных предметов.
 */
public final class ItemsApi {
    /** Ревизия контракта публичного API. */
    public static final int API_VERSION = 1;

    private final RaskolClasses plugin;

    public ItemsApi(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(String itemId, int itemLevel, long seed) {
        return plugin.getItems().create(itemId, itemLevel, seed);
    }

    /**
     * Создаёт вариант шаблона с ближайшим доступным уровнем.
     * Допустимые уровни и редкости берутся из фактически загруженного реестра,
     * поэтому API не расходится с settings.generated-levels и
     * settings.generated-rarities в items.yml.
     */
    public ItemStack createFromTemplate(String templateId, int level, ItemRarity rarity, long seed) {
        ItemDefinition definition = resolveTemplate(plugin.getItems().registry(), templateId, level, rarity);
        return plugin.getItems().create(definition.id(), definition.itemLevel(), seed);
    }

    static ItemDefinition resolveTemplate(ItemRegistry registry, String templateId, int level, ItemRarity rarity) {
        Objects.requireNonNull(registry, "Item registry must not be null");
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("Template id must not be blank");
        }
        Objects.requireNonNull(rarity, "Item rarity must not be null");

        List<ItemDefinition> templateVariants = registry.all().stream()
                .filter(definition -> templateId.equalsIgnoreCase(definition.template()))
                .toList();
        if (templateVariants.isEmpty()) {
            throw new IllegalArgumentException("Unknown item template: " + templateId);
        }

        int selectedLevel = templateVariants.stream()
                .map(ItemDefinition::itemLevel)
                .distinct()
                .min(Comparator.comparingLong((Integer candidate) -> Math.abs((long) level - candidate))
                        .thenComparingInt(Integer::intValue))
                .orElseThrow();

        List<ItemDefinition> levelVariants = templateVariants.stream()
                .filter(definition -> definition.itemLevel() == selectedLevel)
                .toList();
        return levelVariants.stream()
                .filter(definition -> definition.rarity() == rarity)
                .findFirst()
                .orElseThrow(() -> {
                    String availableRarities = levelVariants.stream()
                            .map(ItemDefinition::rarity)
                            .distinct()
                            .sorted()
                            .map(Enum::name)
                            .collect(Collectors.joining(", "));
                    return new IllegalArgumentException("Unsupported rarity " + rarity + " for template "
                            + templateId + " at level " + selectedLevel + ". Available rarities: "
                            + availableRarities);
                });
    }

    public ItemRarity rollRarity(Map<ItemRarity, Double> weights, long seed) {
        double total = weights.values().stream()
                .filter(v -> v != null && Double.isFinite(v) && v > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
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
