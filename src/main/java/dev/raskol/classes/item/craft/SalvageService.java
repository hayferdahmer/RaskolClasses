// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 1.15.4: разбор (salvage) предметов RaskolClasses на фрагменты.
 *
 * Математика (pure, selftest 136):
 *  - вид осколка: MAIN_HAND/OFF_HAND → WEAPON, иначе ARMOR;
 *  - тир: COMMON/UNCOMMON → COMMON, RARE → RARE, EPIC/LEGENDARY/MYTHIC → EPIC;
 *  - количество осколков: itemLevel / 20 (20→1, 40→2, 60→3);
 *  - эссенция класса: 0 (COMMON-тир) / 1 (RARE) / 2 (EPIC+), только для классовых;
 *  - реликвия: +1 для LEGENDARY/MYTHIC.
 *
 * Экономика: разбор убыточен (~30% ценности) — монетный сток. Дневной лимит
 * item.salvage.daily-limit (дефолт 20) защищает экономику от фермы фрагментов.
 * Счётчик хранится в salvage-daily.yml и сохраняется до изменения инвентаря.
 */
public final class SalvageService {

    public enum SalvageResult {
        OK,
        DISABLED,
        EMPTY_HAND,
        NOT_OURS,
        DAILY_LIMIT,
        STORAGE_ERROR
    }

    private static final int DEFAULT_DAILY_LIMIT = 20;

    private final RaskolClasses plugin;
    private final FragmentRegistry fragments;
    private final SalvageDailyLimitStore dailyLimits;

    public SalvageService(RaskolClasses plugin, FragmentRegistry fragments) {
        this.plugin = plugin;
        this.fragments = fragments;
        this.dailyLimits =
                new SalvageDailyLimitStore(new File(plugin.getDataFolder(), "salvage-daily.yml"), plugin.getLogger());
    }

    /** Pure: карта фрагментов за разбор определения (без состояния). */
    public static Map<Fragment, Integer> fragmentsFor(FragmentRegistry registry, ItemDefinition def) {
        Map<Fragment, Integer> out = new LinkedHashMap<>();
        boolean weapon = def.slot() == ItemSlot.MAIN_HAND || def.slot() == ItemSlot.OFF_HAND;
        ItemRarity tier = tierOf(def.rarity());
        Fragment shard = registry.byId(
                (weapon ? "frag_weapon_" : "frag_armor_") + tier.name().toLowerCase(java.util.Locale.ROOT));
        if (shard != null) {
            out.merge(shard, Math.max(1, def.itemLevel() / 20), Integer::sum);
        }
        if (def.playerClass() != null) {
            Fragment essence =
                    registry.byId("frag_essence_" + def.playerClass().name().toLowerCase(java.util.Locale.ROOT));
            int essenceCount = tier == ItemRarity.COMMON ? 0 : tier == ItemRarity.RARE ? 1 : 2;
            if (essence != null && essenceCount > 0) {
                out.merge(essence, essenceCount, Integer::sum);
            }
        }
        if (def.rarity() == ItemRarity.LEGENDARY || def.rarity() == ItemRarity.MYTHIC) {
            Fragment relic = registry.byId("frag_relic");
            if (relic != null) {
                out.merge(relic, 1, Integer::sum);
            }
        }
        return out;
    }

    private static ItemRarity tierOf(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON -> ItemRarity.COMMON;
            case RARE -> ItemRarity.RARE;
            default -> ItemRarity.EPIC;
        };
    }

    /** Разобрать предмет в основной руке. */
    public SalvageResult salvage(Player player) {
        if (!plugin.getConfig().getBoolean("item.salvage.enabled", true)) {
            return SalvageResult.DISABLED;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return SalvageResult.EMPTY_HAND;
        }
        ItemDefinition def = plugin.getItems().definitionOf(hand);
        if (def == null) {
            return SalvageResult.NOT_OURS;
        }

        UUID uuid = player.getUniqueId();
        int limit = dailyLimit();
        SalvageDailyLimitStore.ConsumeResult consumeResult =
                dailyLimits.tryConsume(uuid, java.time.LocalDate.now().toEpochDay(), limit);
        if (consumeResult == SalvageDailyLimitStore.ConsumeResult.LIMIT_REACHED) {
            return SalvageResult.DAILY_LIMIT;
        }
        if (consumeResult == SalvageDailyLimitStore.ConsumeResult.STORAGE_ERROR) {
            return SalvageResult.STORAGE_ERROR;
        }

        Map<Fragment, Integer> gained = fragmentsFor(fragments, def);
        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        fragments.addFragments(player, gained);

        StringBuilder list = new StringBuilder();
        gained.forEach((fragment, count) ->
                list.append(fragment.displayName()).append(" ×").append(count).append(", "));
        String pretty = list.length() > 0 ? list.substring(0, list.length() - 2) : "";
        player.sendMessage(Component.text(
                plugin.getRaskolConfig()
                        .message("item.salvage-ok", "♻ Разобрано: {item} → {fragments}")
                        .replace("{item}", def.displayName())
                        .replace("{fragments}", pretty),
                NamedTextColor.GREEN));
        return SalvageResult.OK;
    }

    /** Дневной лимит с гардом: конфиг <1 или мусор → дефолт 20. */
    private int dailyLimit() {
        int limit = plugin.getConfig().getInt("item.salvage.daily-limit", DEFAULT_DAILY_LIMIT);
        return limit >= 1 ? limit : DEFAULT_DAILY_LIMIT;
    }

    /** Сообщение об отказе для UI/команды. */
    public void notifyFailure(Player player, SalvageResult result) {
        String key;
        String fallback;
        switch (result) {
            case DISABLED -> {
                key = "item.salvage-disabled";
                fallback = "♻ Разбор отключён";
            }
            case EMPTY_HAND -> {
                key = "item.salvage-empty";
                fallback = "♻ В руке нет предмета для разбора";
            }
            case NOT_OURS -> {
                key = "item.salvage-not-ours";
                fallback = "♻ Этот предмет не разобрать: он не из RaskolClasses";
            }
            case DAILY_LIMIT -> {
                key = "item.salvage-limit";
                fallback = "♻ Дневной лимит разбора исчерпан ({limit})";
            }
            case STORAGE_ERROR -> {
                key = "item.salvage-storage-error";
                fallback = "♻ Разбор временно недоступен: не удалось сохранить дневной лимит";
            }
            default -> {
                return;
            }
        }
        player.sendMessage(Component.text(
                plugin.getRaskolConfig().message(key, fallback).replace("{limit}", String.valueOf(dailyLimit())),
                NamedTextColor.RED));
    }
}
