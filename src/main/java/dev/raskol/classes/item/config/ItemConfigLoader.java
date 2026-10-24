// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.config;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.Affix;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.SetDefinition;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemRegistry;
import dev.raskol.classes.spec.Spec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 1.15.0: загрузка items.yml → ItemRegistry (явные предметы + генерация из шаблонов + сеты).
 * 1.15.1 (Итерация B): ДЕКОМПОЗИЦИЯ. Валидация вынесена в ItemValidator; load() стал
 * оркестратором четырёх шагов:
 *   1) parseExplicitItems   — секция items: ошибка одного предмета = warning + отключение;
 *   2) parseAndExpandTemplates — секция templates + generate + budget → ItemTemplateExpander;
 *      ошибка шаблона/бюджета = отказ ВСЕГО файла (реестр сохраняется прежний);
 *   3) parseSets            — секция sets с порогами 2..6;
 *   4) filterUnknownRefs    — пост-фильтры: предмет с неизвестным сетом/шаблоном =
 *      warning + отключение (семантика 1.15.0 сохранена).
 * Контракт load(File, Consumer<String>) не изменён — ItemSystem.reload() не тронут.
 */
public final class ItemConfigLoader {

    private ItemConfigLoader() {
    }

    public static ItemRegistry load(File file) throws Exception {
        return load(file, ignored -> { });
    }

    public static ItemRegistry load(File file, Consumer<String> warning) throws Exception {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int maxLevel = ItemValidator.validateSchema(yaml);

        List<ItemDefinition> defs = new ArrayList<>(parseExplicitItems(yaml, maxLevel, warning));
        defs.addAll(parseAndExpandTemplates(yaml, maxLevel));

        Map<String, SetDefinition> sets = parseSets(yaml);
        List<ItemDefinition> filtered = filterUnknownRefs(defs, sets, yaml, warning);
        return new ItemRegistry(filtered, sets);
    }

    /* ------------------------------ шаг 1: явные предметы ------------------------------ */

    private static List<ItemDefinition> parseExplicitItems(YamlConfiguration yaml, int maxLevel,
                                                           Consumer<String> warning) {
        List<ItemDefinition> defs = new ArrayList<>();
        ConfigurationSection entries = yaml.getConfigurationSection("items");
        if (entries == null) {
            return defs;
        }
        for (String id : entries.getKeys(false)) {
            ConfigurationSection s = entries.getConfigurationSection(id);
            if (s == null) {
                throw new IllegalArgumentException("items." + id + " must be a section");
            }
            try {
                ItemDefinition def = parseDefinition(id, s);
                if (def.itemLevel() > maxLevel) {
                    throw new IllegalArgumentException("item-level exceeds settings.max-item-level");
                }
                ItemValidator.validateWeaponType(yaml, def);
                defs.add(def);
            } catch (RuntimeException ex) {
                warning.accept("Предмет " + id + " отключён: " + ex.getMessage());
            }
        }
        return defs;
    }

    private static ItemDefinition parseDefinition(String id, ConfigurationSection s) {
        ItemSlot slot = ItemSlot.valueOf(s.getString("slot", "").toUpperCase(Locale.ROOT));
        String material = s.getString("material", "");
        ItemValidator.validateMaterial(material);
        ItemValidator.validateMaterialSlot(slot, material);

        String cls = s.getString("class", "ALL");
        PlayerClass pc = "ALL".equalsIgnoreCase(cls) ? null : PlayerClass.valueOf(cls.toUpperCase(Locale.ROOT));

        String spec = s.getString("spec");
        if (spec != null) {
            Spec parsed = Spec.fromId(spec);
            if (parsed == null) {
                throw new IllegalArgumentException("unknown spec " + spec);
            }
            if (pc == null || parsed.playerClass() != pc) {
                throw new IllegalArgumentException("spec does not belong to item class");
            }
        }

        List<Affix> pool = new ArrayList<>();
        for (Map<?, ?> a : s.getMapList("affix-pool")) {
            ItemStat stat = ItemValidator.requireStat(a.get("stat"));
            pool.add(new Affix(stat,
                    ItemValidator.finiteNumber(a.get("min")),
                    ItemValidator.finiteNumber(a.get("max")),
                    ItemValidator.finiteNumber(a.get("weight"))));
        }

        Integer custom = s.contains("custom-model-data") ? s.getInt("custom-model-data") : null;
        return new ItemDefinition(id,
                s.getString("display-name", id),
                material,
                slot,
                s.contains("weapon-type")
                        ? WeaponType.valueOf(s.getString("weapon-type").toUpperCase(Locale.ROOT)) : null,
                ItemRarity.valueOf(s.getString("rarity", "").toUpperCase(Locale.ROOT)),
                s.getInt("item-level", 1),
                pc,
                spec,
                s.getInt("level-requirement", 1),
                ItemValidator.parseStatMap(s.getConfigurationSection("base-stats"), false),
                pool,
                s.getInt("affix-count", 0),
                s.getString("set"),
                s.getStringList("lore"),
                s.getBoolean("glow", false),
                custom,
                s.getString("template"));
    }

    /* ------------------------------ шаг 2: шаблоны и генерация ------------------------------ */

    private static List<ItemDefinition> parseAndExpandTemplates(YamlConfiguration yaml, int maxLevel) {
        ConfigurationSection templates = yaml.getConfigurationSection("templates");
        List<ItemTemplateExpander.Template> ts = new ArrayList<>();
        Map<String, String> materials = new HashMap<>();

        if (templates != null) {
            for (String id : templates.getKeys(false)) {
                ConfigurationSection s = templates.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                PlayerClass pc = PlayerClass.valueOf(s.getString("class", "").toUpperCase(Locale.ROOT));
                ItemSlot slot = ItemSlot.valueOf(s.getString("slot", "").toUpperCase(Locale.ROOT));
                WeaponType wt = s.contains("weapon-type")
                        ? WeaponType.valueOf(s.getString("weapon-type").toUpperCase(Locale.ROOT)) : null;
                ItemValidator.validateTemplateSlotWeaponType(slot, wt, id);
                Map<ItemStat, Double> distribution =
                        ItemValidator.parseStatMap(s.getConfigurationSection("distribution"), true);
                if (s.contains("material")) {
                    materials.put(id, s.getString("material"));
                }
                Integer modelData = s.contains("custom-model-data") ? s.getInt("custom-model-data") : null;
                ItemValidator.validateModelData(modelData, id);
                ts.add(new ItemTemplateExpander.Template(id, pc, slot, wt, distribution,
                        s.getStringList("names"), s.getString("set"), modelData));
            }
        }
        if (ts.isEmpty()) {
            ts.addAll(ItemTemplateExpander.starterTemplates());
        }

        // generate: переопределение материалов шаблонов без правки templates
        for (Map<?, ?> generated : yaml.getMapList("generate")) {
            Object template = generated.get("template");
            Object material = generated.get("material");
            if (template != null && material != null) {
                materials.put(template.toString(), material.toString());
            }
        }

        List<Integer> levels = ItemValidator.parseLevels(yaml, maxLevel);
        ConfigurationSection budget = yaml.getConfigurationSection("budget");
        List<ItemDefinition> expanded = ItemTemplateExpander.expand(ts, materials, levels,
                ItemValidator.parseRarityList(yaml),
                ItemValidator.parseEnumDoubleMap(budget == null ? null : budget.getConfigurationSection("rarity-mult"), ItemRarity.class),
                ItemValidator.parseEnumDoubleMap(budget == null ? null : budget.getConfigurationSection("slot-weight"), ItemSlot.class),
                ItemValidator.parseEnumDoubleMap(budget == null ? null : budget.getConfigurationSection("stat-cost"), ItemStat.class));

        for (ItemDefinition def : expanded) {
            ItemValidator.validateExpandedMaterial(def);
            ItemValidator.validateWeaponType(yaml, def);
        }
        return expanded;
    }

    /* ------------------------------ шаг 3: сеты ------------------------------ */

    private static Map<String, SetDefinition> parseSets(YamlConfiguration yaml) {
        Map<String, SetDefinition> sets = new LinkedHashMap<>();
        ConfigurationSection setSections = yaml.getConfigurationSection("sets");
        if (setSections == null) {
            return sets;
        }
        for (String id : setSections.getKeys(false)) {
            ConfigurationSection set = setSections.getConfigurationSection(id);
            if (set == null) {
                continue;
            }
            Map<Integer, SetDefinition.Bonus> bonuses = new LinkedHashMap<>();
            ConfigurationSection section = set.getConfigurationSection("bonuses");
            if (section != null) {
                for (String threshold : section.getKeys(false)) {
                    int pieces = Integer.parseInt(threshold);
                    ConfigurationSection bonus = section.getConfigurationSection(threshold);
                    if (bonus == null) {
                        continue;
                    }
                    bonuses.put(pieces, new SetDefinition.Bonus(
                            ItemValidator.parseStatMap(bonus.getConfigurationSection("stats"), false),
                            bonus.getString("description", "")));
                }
            }
            sets.put(id, new SetDefinition(id, set.getString("display-name", id), bonuses));
        }
        return sets;
    }

    /* ------------------------------ шаг 4: пост-фильтры ------------------------------ */

    private static List<ItemDefinition> filterUnknownRefs(List<ItemDefinition> defs,
                                                          Map<String, SetDefinition> sets,
                                                          YamlConfiguration yaml,
                                                          Consumer<String> warning) {
        defs.removeIf(def -> {
            if (def.setId() != null && !sets.containsKey(def.setId())) {
                warning.accept("Предмет " + def.id() + " отключён: неизвестный сет " + def.setId());
                return true;
            }
            return false;
        });

        ConfigurationSection templates = yaml.getConfigurationSection("templates");
        Set<String> templateIds = templates == null ? Set.of() : templates.getKeys(false);
        if (templateIds.isEmpty()) {
            templateIds = ItemTemplateExpander.starterTemplates().stream()
                    .map(ItemTemplateExpander.Template::id)
                    .collect(Collectors.toSet());
        }
        Set<String> validTemplates = templateIds;
        defs.removeIf(def -> {
            if (def.template() != null && !validTemplates.contains(def.template())) {
                warning.accept("Предмет " + def.id() + " отключён: неизвестный шаблон " + def.template());
                return true;
            }
            return false;
        });
        return defs;
    }
}
