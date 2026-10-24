// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.config;

import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.WeaponType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.15.1 (Итерация B): единая точка валидации items.yml, вынесенная из
 * ItemConfigLoader. Все методы бросают IllegalArgumentException с человекочитаемым
 * сообщением — вызывающий код решает, отключить один предмет (warning) или
 * отклонить весь файл (severe + прежний реестр).
 *
 * Правила сохранены байт-в-байт из редакции 1.15.0:
 *  - schema-version = 1, settings.max-item-level ∈ 1..60;
 *  - материал существует и соответствует слоту брони (суффиксы _HELMET/_CHESTPLATE/...);
 *  - weapon-type обязателен ровно для MAIN_HAND и настроен в settings.weapon-types
 *    (материал и класс разрешены);
 *  - статы: известные id, конечные, ≥ 0, в диапазоне ItemStat; для distribution
 *    дополнительно ≤ 100 и сумма = 100;
 *  - generated-levels уникальны и ≤ max-item-level; generated-rarities без дублей;
 *  - бюджет-карты (rarity-mult/slot-weight/stat-cost) — конечные положительные значения.
 */
public final class ItemValidator {

    private ItemValidator() {
    }

    /** schema-version и верхняя граница уровня предметов; возвращает max-item-level. */
    public static int validateSchema(YamlConfiguration yaml) {
        if (yaml.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException("items.yml schema-version must be 1");
        }
        int maxLevel = yaml.getInt("settings.max-item-level", 60);
        if (maxLevel < 1 || maxLevel > 60) {
            throw new IllegalArgumentException("settings.max-item-level must be 1..60");
        }
        return maxLevel;
    }

    /** Материал существует в реестре Bukkit. */
    public static void validateMaterial(String material) {
        if (Material.matchMaterial(material) == null) {
            throw new IllegalArgumentException("unknown material " + material);
        }
    }

    /** Материал брони соответствует объявленному слоту (суффикс-правило 1.15.0). */
    public static void validateMaterialSlot(ItemSlot slot, String material) {
        if ((slot == ItemSlot.HEAD && !material.endsWith("_HELMET"))
                || (slot == ItemSlot.CHEST && !material.endsWith("_CHESTPLATE"))
                || (slot == ItemSlot.LEGS && !material.endsWith("_LEGGINGS"))
                || (slot == ItemSlot.FEET && !material.endsWith("_BOOTS"))) {
            throw new IllegalArgumentException("material does not fit slot " + slot);
        }
    }

    /** Шаблон: weapon-type присутствует ровно для MAIN_HAND. */
    public static void validateTemplateSlotWeaponType(ItemSlot slot, WeaponType weaponType, String templateId) {
        if ((slot == ItemSlot.MAIN_HAND) != (weaponType != null)) {
            throw new IllegalArgumentException("template weapon type must match MAIN_HAND: " + templateId);
        }
    }

    /** custom-model-data положительный (0/отрицательное ломает ресурс-пак). */
    public static void validateModelData(Integer modelData, String templateId) {
        if (modelData != null && modelData < 1) {
            throw new IllegalArgumentException("template custom-model-data must be positive: " + templateId);
        }
    }

    /**
     * Предмет из шаблона: материал — реальный ItemStack-материал
     * (isItem), иначе генерация выдаст недержимый предмет.
     */
    public static void validateExpandedMaterial(ItemDefinition def) {
        Material material = Material.matchMaterial(def.material());
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Template " + def.id()
                    + " uses invalid item material " + def.material());
        }
    }

    /**
     * weapon-type предмета настроен и совместим: материал входит в список
     * (или список пуст = разрешены все, включая wildcard-суффиксы «*SWORD»),
     * класс предмета входит в список классов типа (или там есть ALL).
     */
    public static void validateWeaponType(YamlConfiguration yaml, ItemDefinition def) {
        if (def.slot() != ItemSlot.MAIN_HAND) {
            return;
        }
        ConfigurationSection type = yaml.getConfigurationSection("settings.weapon-types." + def.weaponType().name());
        if (type == null) {
            throw new IllegalArgumentException("weapon type not configured: " + def.weaponType());
        }
        List<String> materials = type.getStringList("materials");
        boolean materialAllowed = materials.isEmpty()
                || materials.stream().anyMatch(m -> m.equals(def.material())
                || (m.startsWith("*") && def.material().endsWith(m.substring(1))));
        if (!materialAllowed) {
            throw new IllegalArgumentException("material not allowed for " + def.weaponType());
        }
        List<String> classes = type.getStringList("classes");
        if (def.playerClass() != null && !classes.contains("ALL") && !classes.contains(def.playerClass().name())) {
            throw new IllegalArgumentException("weapon type not allowed for class " + def.playerClass());
        }
    }

    /**
     * Карта статов из секции: известные id, конечные значения ≥ 0;
     * percentages=true (distribution шаблонов): ≤ 100 и сумма строго 100;
     * percentages=false (base-stats/бонусы сетов): значение в диапазоне ItemStat.
     */
    public static Map<ItemStat, Double> parseStatMap(ConfigurationSection section, boolean percentages) {
        Map<ItemStat, Double> map = new EnumMap<>(ItemStat.class);
        if (section == null) {
            return map;
        }
        double sum = 0;
        for (String key : section.getKeys(false)) {
            ItemStat stat = ItemStat.fromId(key);
            if (stat == null) {
                throw new IllegalArgumentException("unknown stat " + key);
            }
            double val = section.getDouble(key, Double.NaN);
            if (!Double.isFinite(val) || val < 0 || (percentages && val > 100)) {
                throw new IllegalArgumentException("invalid stat value " + key);
            }
            if (!percentages && (val < stat.min() || val > stat.max())) {
                throw new IllegalArgumentException("stat out of range " + key);
            }
            map.put(stat, val);
            sum += val;
        }
        if (percentages && Math.abs(sum - 100) > 0.01) {
            throw new IllegalArgumentException("template distribution must total 100");
        }
        return map;
    }

    /** settings.generated-rarities без дублей; дефолт COMMON/RARE/EPIC. */
    public static List<ItemRarity> parseRarityList(YamlConfiguration yaml) {
        List<String> values = yaml.getStringList("settings.generated-rarities");
        List<ItemRarity> result = values.isEmpty()
                ? List.of(ItemRarity.COMMON, ItemRarity.RARE, ItemRarity.EPIC)
                : values.stream().map(v -> ItemRarity.valueOf(v.toUpperCase(Locale.ROOT))).toList();
        if (result.stream().distinct().count() != result.size()) {
            throw new IllegalArgumentException("Duplicate generated rarity");
        }
        return result;
    }

    /** settings.generated-levels: дефолт [20,40,60]; уникальные, в пределах 1..maxLevel. */
    public static List<Integer> parseLevels(YamlConfiguration yaml, int maxLevel) {
        List<Integer> levels = yaml.getIntegerList("settings.generated-levels");
        if (levels.isEmpty()) {
            levels = List.of(20, 40, 60);
        }
        if (levels.stream().anyMatch(level -> level < 1 || level > maxLevel)
                || levels.stream().distinct().count() != levels.size()) {
            throw new IllegalArgumentException(
                    "settings.generated-levels must be unique values up to settings.max-item-level");
        }
        return levels;
    }

    /**
     * Бюджет-карты (budget.rarity-mult / budget.slot-weight / budget.stat-cost):
     * конечные положительные значения; ключи stat-cost резолвятся через ItemStat.fromId.
     */
    public static <E extends Enum<E>> Map<E, Double> parseEnumDoubleMap(ConfigurationSection section, Class<E> type) {
        if (section == null) {
            return Map.of();
        }
        Map<E, Double> result = new EnumMap<>(type);
        for (String key : section.getKeys(false)) {
            E enumKey;
            if (type == ItemStat.class) {
                ItemStat stat = ItemStat.fromId(key);
                if (stat == null) {
                    throw new IllegalArgumentException("unknown budget stat: " + key);
                }
                enumKey = type.cast(stat);
            } else {
                enumKey = Enum.valueOf(type, key.toUpperCase(Locale.ROOT));
            }
            double value = section.getDouble(key, Double.NaN);
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("invalid budget value: " + key);
            }
            result.put(enumKey, value);
        }
        return Map.copyOf(result);
    }

    /** Ключ affix-pool «stat» обязателен и должен резолвиться в ItemStat. */
    public static ItemStat requireStat(Object value) {
        ItemStat stat = ItemStat.fromId(String.valueOf(value));
        if (stat == null) {
            throw new IllegalArgumentException("unknown stat " + value);
        }
        return stat;
    }

    /** Число из YAML: только конечное Number (min/max/weight аффиксов). */
    public static double finiteNumber(Object value) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            throw new IllegalArgumentException("expected finite number");
        }
        return n.doubleValue();
    }
}
