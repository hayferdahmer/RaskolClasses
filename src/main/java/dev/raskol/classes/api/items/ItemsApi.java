// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.api.items;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * Стабильный публичный API для RaskolMobs и других плагинов для создания
 * сконфигурированных предметов.
 */
public final class ItemsApi {
    /** Ревизия контракта публичного API. */
    public static final int API_VERSION = 1;
    private final RaskolClasses plugin;

    /**
     * 1.15.7 (2.6-20): допустимые уровни для createFromTemplate.
     * В реестре items.yml есть только предметы уровней 20/40/60 (шаблоны
     * warrior_sword_20_rare, warrior_sword_40_rare, warrior_sword_60_rare).
     * Если RaskolMobs запросит level=45, округляем до ближайшего (40).
     */
    private static final int[] VALID_LEVELS = {20, 40, 60};

    /**
     * 1.15.7 (2.6-20): допустимые редкости для createFromTemplate.
     * В реестре items.yml есть только RARE/EPIC/LEGENDARY (COMMON/UNCOMMON/MYTHIC
     * не имеют шаблонов). Если RaskolMobs запросит MYTHIC, fallback на LEGENDARY.
     */
    private static final Set<ItemRarity> VALID_RARITIES = Set.of(
            ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.LEGENDARY);

    public ItemsApi(RaskolClasses plugin) { this.plugin = plugin; }

    public ItemStack create(String itemId, int itemLevel, long seed) {
        return plugin.getItems().create(itemId, itemLevel, seed);
    }

    /**
     * 1.15.7 (2.6-20): валидация level и rarity перед поиском в реестре.
     * - level округляется до ближайшего из {20, 40, 60};
     * - rarity fallback: если не в {RARE, EPIC, LEGENDARY}, используется LEGENDARY;
     * - если точный prefix не найден, бросается IllegalArgumentException с подсказкой.
     */
    public ItemStack createFromTemplate(String templateId, int level, ItemRarity rarity, long seed) {
        int validLevel = nearestLevel(level);
        ItemRarity validRarity = VALID_RARITIES.contains(rarity) ? rarity : ItemRarity.LEGENDARY;
        String prefix = templateId.toLowerCase(java.util.Locale.ROOT) + "_" + validLevel + "_" + validRarity.name().toLowerCase(java.util.Locale.ROOT);
        ItemDefinition d = plugin.getItems().registry().get(prefix);
        if (d == null) {
            throw new IllegalArgumentException("Unknown item template/variant: " + prefix
                    + " (requested level=" + level + " rarity=" + rarity + ")"
                    + ". Valid levels: " + java.util.Arrays.toString(VALID_LEVELS)
                    + ", valid rarities: " + VALID_RARITIES);
        }
        return plugin.getItems().create(d.id(), validLevel, seed);
    }

    /**
     * 1.15.7 (2.6-20): округление level до ближайшего из VALID_LEVELS.
     * Примеры: 15 → 20, 35 → 40, 55 → 60, 70 → 60.
     */
    private static int nearestLevel(int level) {
        int best = VALID_LEVELS[0];
        int bestDiff = Math.abs(level - best);
        for (int i = 1; i < VALID_LEVELS.length; i++) {
            int diff = Math.abs(level - VALID_LEVELS[i]);
            if (diff < bestDiff) {
                best = VALID_LEVELS[i];
                bestDiff = diff;
            }
        }
        return best;
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
