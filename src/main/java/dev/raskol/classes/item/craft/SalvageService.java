// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.storage.AsyncStorageWriter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Разбор предметов RaskolClasses на фрагменты с предварительным сохранением дневного лимита.
 */
public final class SalvageService {

    public enum SalvageResult {
        OK,
        DISABLED,
        EMPTY_HAND,
        NOT_OURS,
        DAILY_LIMIT,
        STORAGE_ERROR,
        BUSY,
        ITEM_CHANGED
    }

    private static final int DEFAULT_DAILY_LIMIT = 20;

    private final RaskolClasses plugin;
    private final FragmentRegistry fragments;
    private final SalvageDailyLimitStore dailyLimits;

    public SalvageService(RaskolClasses plugin, FragmentRegistry fragments, AsyncStorageWriter writer) {
        this.plugin = plugin;
        this.fragments = fragments;
        this.dailyLimits = new SalvageDailyLimitStore(
                new File(plugin.getDataFolder(), "salvage-daily.yml"), plugin.getLogger(), writer);
    }

    /** Рассчитывает содержимое разбора без изменения состояния игрока. */
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

    /**
     * Сохраняет резервирование лимита асинхронно. Изменение инвентаря и выдача
     * фрагментов выполняются только на основном потоке после успешной записи.
     */
    public void salvage(Player player, Consumer<SalvageResult> completion) {
        if (!plugin.getConfig().getBoolean("item.salvage.enabled", true)) {
            completion.accept(SalvageResult.DISABLED);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            completion.accept(SalvageResult.EMPTY_HAND);
            return;
        }
        ItemDefinition def = plugin.getItems().definitionOf(hand);
        if (def == null) {
            completion.accept(SalvageResult.NOT_OURS);
            return;
        }

        ItemStack expected = hand.clone();
        UUID uuid = player.getUniqueId();
        int limit = dailyLimit();
        dailyLimits
                .tryConsume(uuid, java.time.LocalDate.now().toEpochDay(), limit)
                .whenComplete((consumeResult, error) -> {
                    try {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (error != null
                                    || consumeResult == null
                                    || consumeResult == SalvageDailyLimitStore.ConsumeResult.STORAGE_ERROR) {
                                completion.accept(SalvageResult.STORAGE_ERROR);
                                return;
                            }
                            if (consumeResult == SalvageDailyLimitStore.ConsumeResult.LIMIT_REACHED) {
                                completion.accept(SalvageResult.DAILY_LIMIT);
                                return;
                            }
                            if (consumeResult == SalvageDailyLimitStore.ConsumeResult.BUSY) {
                                completion.accept(SalvageResult.BUSY);
                                return;
                            }

                            ItemStack current = player.getInventory().getItemInMainHand();
                            if (current == null
                                    || current.getAmount() != expected.getAmount()
                                    || !current.isSimilar(expected)) {
                                completion.accept(SalvageResult.ITEM_CHANGED);
                                return;
                            }

                            Map<Fragment, Integer> gained = fragmentsFor(fragments, def);
                            current.setAmount(current.getAmount() - 1);
                            if (current.getAmount() <= 0) {
                                player.getInventory().setItemInMainHand(null);
                            }
                            fragments.addFragments(player, gained);
                            sendSuccess(player, def, gained);
                            completion.accept(SalvageResult.OK);
                        });
                    } catch (RuntimeException exception) {
                        plugin.getLogger()
                                .severe("Salvage: не удалось вернуть результат на основной поток: "
                                        + exception.getMessage());
                    }
                });
    }

    private void sendSuccess(Player player, ItemDefinition def, Map<Fragment, Integer> gained) {
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
    }

    private int dailyLimit() {
        int limit = plugin.getConfig().getInt("item.salvage.daily-limit", DEFAULT_DAILY_LIMIT);
        return limit >= 1 ? limit : DEFAULT_DAILY_LIMIT;
    }

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
            case BUSY -> {
                key = "item.salvage-storage-busy";
                fallback = "♻ Предыдущая операция разбора ещё сохраняется. Повторите позже";
            }
            case ITEM_CHANGED -> {
                key = "item.salvage-item-changed";
                fallback = "♻ Предмет в руке изменился во время сохранения лимита. Попытка сохранена";
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
