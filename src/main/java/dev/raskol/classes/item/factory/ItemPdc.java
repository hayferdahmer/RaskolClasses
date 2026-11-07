// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.factory;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemStat;

/**
 * Версионированный PDC-формат плагина для генерируемых предметов.
 *
 * 1.15.1-C: магическое число 2048 заменено константой MAX_AFFIX_STRING —
 * граница длины сериализованных аффиксов (защита от повреждённых/подделанных PDC).
 *
 * 1.15.6 (Волна 6 часть 2, аудит 2.6-29): hasValidUid с явным null-check.
 * Раньше UUID.fromString(uid) ловил RuntimeException, включая NPE при null uid —
 * исключение использовалось как control flow. Теперь null-значение обрабатывается
 * штатно без генерации исключения.
 *
 * 1.15.6 (Волна 6 часть 2): все комментарии переведены на русский.
 */
public final class ItemPdc {

    /** Версия PDC-формата. Инкрементируется при несовместимых изменениях схемы. */
    public static final int VERSION = 1;

    /** Максимальная длина строки affix_vals в PDC; сверх — считаем данные битыми. */
    public static final int MAX_AFFIX_STRING = 2048;

    private final NamespacedKey version;
    private final NamespacedKey uid;
    private final NamespacedKey id;
    private final NamespacedKey level;
    private final NamespacedKey rarity;
    private final NamespacedKey seed;
    private final NamespacedKey affixes;
    private final NamespacedKey shotDamage;
    private final NamespacedKey shotType;
    private final NamespacedKey socketGems;
    private final NamespacedKey upgrade;
    private final NamespacedKey enchants;
    private final NamespacedKey craftedBy;

    public ItemPdc(RaskolClasses plugin) {
        version = key(plugin, "item_v");
        uid = key(plugin, "item_uid");
        id = key(plugin, "item_id");
        level = key(plugin, "item_ilvl");
        rarity = key(plugin, "item_rarity");
        seed = key(plugin, "roll_seed");
        affixes = key(plugin, "affix_vals");
        shotDamage = key(plugin, "shot_attack_damage");
        shotType = key(plugin, "shot_type");
        socketGems = key(plugin, "socket_gems");
        upgrade = key(plugin, "upgrade");
        enchants = key(plugin, "enchants");
        craftedBy = key(plugin, "crafted_by");
    }

    private static NamespacedKey key(RaskolClasses plugin, String keyName) {
        return new NamespacedKey(plugin, keyName);
    }

    /**
     * Записать метаданные предмета в PDC.
     *
     * @param item     целевой предмет (ItemStack)
     * @param def      определение предмета из реестра
     * @param rollSeed сид ролла аффиксов (сохраняется для детерминизма)
     * @param vals     значения аффиксов (ItemStat → Double)
     */
    public void write(ItemStack item, ItemDefinition def, long rollSeed, Map<ItemStat, Double> vals) {
        var meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        var p = meta.getPersistentDataContainer();
        p.set(version, PersistentDataType.INTEGER, VERSION);
        p.set(uid, PersistentDataType.STRING, UUID.randomUUID().toString());
        p.set(id, PersistentDataType.STRING, def.id());
        p.set(level, PersistentDataType.INTEGER, def.itemLevel());
        p.set(rarity, PersistentDataType.STRING, def.rarity().name());
        p.set(seed, PersistentDataType.LONG, rollSeed);
        p.set(socketGems, PersistentDataType.STRING, "");
        p.set(upgrade, PersistentDataType.INTEGER, 0);
        p.set(enchants, PersistentDataType.STRING, "");
        StringBuilder builder = new StringBuilder();
        vals.forEach((stat, value) -> {
            if (builder.length() > 0) builder.append(';');
            builder.append(stat.id()).append(':').append(value);
        });
        p.set(affixes, PersistentDataType.STRING, builder.toString());
        item.setItemMeta(meta);
    }

    /** Предмет имеет валидный PDC-формат плагина (версия совпадает)? */
    public boolean isItem(ItemStack item) {
        var p = container(item);
        return p != null && Integer.valueOf(VERSION).equals(p.get(version, PersistentDataType.INTEGER));
    }

    /**
     * Предмет имеет валидный UUID (не пустой, парсится в UUID)?
     * 1.15.6 (2.6-29): явный null-check вместо ловли NPE.
     */
    public boolean hasValidUid(ItemStack item) {
        String uidValue = uid(item);
        if (uidValue == null || uidValue.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(uidValue);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** ID определения предмета (из реестра) или null. */
    public String itemId(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(id, PersistentDataType.STRING);
    }

    /** Уровень предмета (item level) или null. */
    public Integer itemLevel(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(level, PersistentDataType.INTEGER);
    }

    /** Редкость предмета (COMMON/RARE/EPIC/LEGENDARY/MYTHIC) или null. */
    public String rarity(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(rarity, PersistentDataType.STRING);
    }

    /**
     * Записать метаданные выстрела (снаряда) в PDC.
     *
     * @param projectile снаряд (стрела, снежок и т.п.)
     * @param damage     урон выстрела
     * @param type       тип выстрела (для резолва кастера)
     */
    public void writeShot(Projectile projectile, double damage, String type) {
        projectile.getPersistentDataContainer().set(shotDamage, PersistentDataType.DOUBLE, damage);
        projectile.getPersistentDataContainer().set(shotType, PersistentDataType.STRING, type);
    }

    /** Урон выстрела (снаряда) или null. */
    public Double shotDamage(Projectile projectile) {
        return projectile.getPersistentDataContainer().get(shotDamage, PersistentDataType.DOUBLE);
    }

    /** Тип выстрела (снаряда) или null. */
    public String shotType(Projectile projectile) {
        return projectile.getPersistentDataContainer().get(shotType, PersistentDataType.STRING);
    }

    /** UID экземпляра предмета или null. */
    public String uid(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(uid, PersistentDataType.STRING);
    }

    /**
     * Записать UUID создателя предмета (для крафт-интеграций).
     *
     * @param item    целевой предмет
     * @param creator UUID игрока-создателя
     */
    public void markCraftedBy(ItemStack item, UUID creator) {
        var meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(craftedBy, PersistentDataType.STRING, creator.toString());
        item.setItemMeta(meta);
    }

    /**
     * Прочитать опциональный UUID создателя (зарезервировано для крафт-патча).
     *
     * @return UUID создателя или null (если не установлен или невалиден)
     */
    public UUID craftedBy(ItemStack item) {
        var p = container(item);
        if (p == null) {
            return null;
        }
        String value = p.get(craftedBy, PersistentDataType.STRING);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Прочитать зарезервированные данные сокетов (без интерпретации в 1.15.0).
     * Задел на будущий gem-слой.
     */
    public String socketData(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(socketGems, PersistentDataType.STRING);
    }

    /**
     * Прочитать уровень улучшения предмета.
     * Свежие предметы стартуют с 0 (записывается в {@link #write}).
     */
    public Integer upgradeLevel(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(upgrade, PersistentDataType.INTEGER);
    }

    /**
     * Прочитать зарезервированные данные зачарований (без интерпретации в 1.15.0).
     * Задел на будущий enchant-слой.
     */
    public String enchantData(ItemStack item) {
        var p = container(item);
        return p == null ? null : p.get(enchants, PersistentDataType.STRING);
    }

    /**
     * Прочитать значения аффиксов предмета.
     *
     * @param item предмет с PDC
     * @param def  определение предмета (для валидации аффиксов и границ значений)
     * @return неизменяемая Map аффиксов и их значений; пустая Map при невалидных данных
     */
    public Map<ItemStat, Double> affixes(ItemStack item, ItemDefinition def) {
        PersistentDataContainer p = container(item);
        if (p == null) {
            return Map.of();
        }
        String raw = p.get(affixes, PersistentDataType.STRING);
        if (raw == null || raw.length() > MAX_AFFIX_STRING) {
            return Map.of();
        }
        Map<ItemStat, Double> values = new EnumMap<>(ItemStat.class);
        for (String part : raw.split(";")) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            ItemStat stat = ItemStat.fromId(pair[0]);
            if (stat == null || values.containsKey(stat)) {
                continue;
            }
            // Ищем диапазон аффикса в определении предмета (для clamp значений)
            dev.raskol.classes.item.model.Affix range = def.affixPool().stream()
                    .filter(a -> a.stat() == stat)
                    .findFirst()
                    .orElse(null);
            if (range == null) {
                continue;
            }
            try {
                double value = Double.parseDouble(pair[1]);
                if (Double.isFinite(value) && values.size() < def.affixCount()) {
                    values.put(stat, Math.max(range.min(), Math.min(range.max(), value)));
                }
            } catch (NumberFormatException ignored) {
                // Невалидное значение — пропускаем аффикс
            }
        }
        return Map.copyOf(values);
    }

    /**
     * Получить PDC-контейнер предмета или null.
     * Возвращает null для: null-предмета, воздуха, предмета без ItemMeta.
     */
    private static PersistentDataContainer container(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer();
    }
}
