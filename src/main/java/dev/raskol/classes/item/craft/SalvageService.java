// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.craft;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

/** Разбор предметов RaskolClasses на фрагменты с двухфазным резервированием лимита. */
public final class SalvageService {

    public enum SalvageResult {
        OK,
        DISABLED,
        EMPTY_HAND,
        NOT_OURS,
        DAILY_LIMIT,
        STORAGE_ERROR,
        BUSY,
        ITEM_CHANGED,
        PARTIAL_FAILURE
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
     * Сначала сохраняет pending-резерв. Проверка сессии, предмета и изменение
     * инвентаря выполняются на основном потоке; затем резерв подтверждается.
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
        UUID playerId = player.getUniqueId();
        long day = java.time.LocalDate.now().toEpochDay();
        dailyLimits.reserve(playerId, day, dailyLimit()).whenComplete((attempt, error) -> {
            if (error != null || attempt == null) {
                completeOnMain(playerId, completion, SalvageResult.STORAGE_ERROR);
                return;
            }
            switch (attempt.result()) {
                case LIMIT_REACHED -> completeOnMain(playerId, completion, SalvageResult.DAILY_LIMIT);
                case BUSY -> completeOnMain(playerId, completion, SalvageResult.BUSY);
                case STORAGE_ERROR, NOT_FOUND -> completeOnMain(playerId, completion, SalvageResult.STORAGE_ERROR);
                case SUCCESS -> dispatchReservedSalvage(playerId, expected, def, attempt.reservation(), completion);
            }
        });
    }

    private void dispatchReservedSalvage(
            UUID playerId,
            ItemStack expected,
            ItemDefinition def,
            SalvageDailyLimitStore.Reservation reservation,
            Consumer<SalvageResult> completion) {
        try {
            Bukkit.getScheduler()
                    .runTask(plugin, () -> processReservedSalvage(playerId, expected, def, reservation, completion));
        } catch (RuntimeException exception) {
            releaseReservation(reservation, "не удалось запланировать разбор");
            plugin.getLogger()
                    .warning("Salvage: main-thread dispatch отклонён для " + playerId + ": " + exception.getMessage());
        }
    }

    private void processReservedSalvage(
            UUID playerId,
            ItemStack expected,
            ItemDefinition def,
            SalvageDailyLimitStore.Reservation reservation,
            Consumer<SalvageResult> completion) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            releaseReservation(reservation, "игрок вышел до выполнения разбора");
            return;
        }

        ItemStack current = player.getInventory().getItemInMainHand();
        if (current == null || current.getAmount() != expected.getAmount() || !current.isSimilar(expected)) {
            releaseReservation(reservation, "предмет изменился до выполнения разбора")
                    .whenComplete((released, error) -> {
                        completeOnMain(
                                playerId,
                                completion,
                                error == null && released == SalvageDailyLimitStore.OperationResult.SUCCESS
                                        ? SalvageResult.ITEM_CHANGED
                                        : SalvageResult.STORAGE_ERROR);
                    });
            return;
        }

        Map<Fragment, Integer> gained = fragmentsFor(fragments, def);
        try {
            current.setAmount(current.getAmount() - 1);
            if (current.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            }
            fragments.addFragments(player, gained);
        } catch (RuntimeException exception) {
            // После начала изменения инвентаря результат выдачи может быть частичным.
            // Не освобождаем pending автоматически: это исключает повторную выдачу.
            plugin.getLogger()
                    .severe("Salvage: ошибка во время изменения инвентаря игрока " + playerId + ": "
                            + exception.getMessage());
            dailyLimits
                    .commit(reservation)
                    .whenComplete((commitResult, commitError) ->
                            completeOnMain(playerId, completion, SalvageResult.PARTIAL_FAILURE));
            return;
        }

        dailyLimits.commit(reservation).whenComplete((commitResult, error) -> {
            if (error != null || commitResult != SalvageDailyLimitStore.OperationResult.SUCCESS) {
                plugin.getLogger()
                        .severe("Salvage: разбор выполнен, но финализация резерва не подтверждена для " + playerId
                                + "; pending-резерв продолжает занимать попытку.");
            }
            completeOnMain(playerId, completion, SalvageResult.OK, def, gained);
        });
    }

    private CompletableFuture<SalvageDailyLimitStore.OperationResult> releaseReservation(
            SalvageDailyLimitStore.Reservation reservation, String reason) {
        CompletableFuture<SalvageDailyLimitStore.OperationResult> release = dailyLimits.release(reservation);
        release.whenComplete((result, error) -> {
            if (error != null || result != SalvageDailyLimitStore.OperationResult.SUCCESS) {
                plugin.getLogger()
                        .warning("Salvage: не удалось освободить резерв " + reservation.operationId() + " (" + reason
                                + "); он останется зарезервированным до восстановления.");
            }
        });
        return release;
    }

    private void completeOnMain(UUID playerId, Consumer<SalvageResult> completion, SalvageResult result) {
        completeOnMain(playerId, completion, result, null, Map.of());
    }

    private void completeOnMain(
            UUID playerId,
            Consumer<SalvageResult> completion,
            SalvageResult result,
            ItemDefinition def,
            Map<Fragment, Integer> gained) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current == null || !current.isOnline()) {
                    return;
                }
                if (result == SalvageResult.OK) {
                    sendSuccess(current, def, gained);
                }
                completion.accept(result);
            });
        } catch (RuntimeException exception) {
            plugin.getLogger()
                    .warning("Salvage: не удалось доставить результат игроку " + playerId + ": "
                            + exception.getMessage());
        }
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
                fallback = "♻ Предмет изменился во время проверки. Попытка возвращена в дневной лимит";
            }
            case PARTIAL_FAILURE -> {
                key = "item.salvage-partial-failure";
                fallback = "♻ Разбор завершился с внутренней ошибкой. Попытка сохранена; обратитесь к администрации";
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
