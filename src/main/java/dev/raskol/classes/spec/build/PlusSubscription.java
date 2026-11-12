// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.build;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import dev.raskol.classes.RaskolClasses;

public final class PlusSubscription implements Listener {
    private static final Logger LOG = Logger.getLogger("RaskolClasses");

    private final RaskolClasses plugin;
    private final ImperialWallet wallet;
    private final PlusStorage storage;
    private final int periodDays;

    public PlusSubscription(RaskolClasses plugin, ImperialWallet wallet, PlusStorage storage, int periodDays) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.storage = storage;
        this.periodDays = Math.max(1, periodDays);
    }

    public enum PurchaseResult {
        OK,
        DISABLED,
        WALLET_UNAVAILABLE,
        INVALID_PERIODS,
        INSUFFICIENT,
        STORAGE_BUSY,
        STORAGE_FAILED
    }

    public enum GiftResult {
        OK,
        DISABLED,
        INSUFFICIENT,
        SELF_GIFT,
        WALLET_UNAVAILABLE,
        INVALID_PERIODS,
        STORAGE_BUSY,
        STORAGE_FAILED
    }

    public CompletableFuture<PurchaseResult> buyAsync(UUID player, int periods) {
        if (player == null || periods < 1 || periods > maxGiftPeriods()) {
            return CompletableFuture.completedFuture(PurchaseResult.INVALID_PERIODS);
        }
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(PurchaseResult.DISABLED);
        }
        if (!wallet.isAvailable()) {
            return CompletableFuture.completedFuture(PurchaseResult.WALLET_UNAVAILABLE);
        }

        int cost;
        long duration;
        try {
            cost = Math.multiplyExact(periods, pricePerPeriod());
            duration = TimeUnit.DAYS.toMillis(Math.multiplyExact((long) periods, periodDays));
        } catch (ArithmeticException overflow) {
            return CompletableFuture.completedFuture(PurchaseResult.INVALID_PERIODS);
        }
        if (cost < 0 || duration <= 0) {
            return CompletableFuture.completedFuture(PurchaseResult.INVALID_PERIODS);
        }
        if (storage.writeInFlight()) {
            return CompletableFuture.completedFuture(PurchaseResult.STORAGE_BUSY);
        }
        if (!wallet.take(player, cost)) {
            return CompletableFuture.completedFuture(PurchaseResult.INSUFFICIENT);
        }

        return storage.extendAsync(player, duration, null).thenApply(saved -> {
            if (!saved) {
                if (!wallet.give(player, cost)) {
                    LOG.severe("raskolspec+: не удалось вернуть списание после ошибки сохранения для " + player);
                }
                return storage.writeInFlight()
                        ? PurchaseResult.STORAGE_BUSY
                        : PurchaseResult.STORAGE_FAILED;
            }
            LOG.info("raskolspec+: purchase " + player + " periods=" + periods);
            return PurchaseResult.OK;
        });
    }

    public CompletableFuture<Long> grantAsync(UUID uuid, int periods) {
        if (uuid == null || periods <= 0) {
            return CompletableFuture.completedFuture(-1L);
        }
        try {
            long duration = TimeUnit.DAYS.toMillis(Math.multiplyExact((long) periods, periodDays));
            return storage.extendAsync(uuid, duration, null).thenApply(saved -> {
                if (!saved) {
                    return -1L;
                }
                long expiry = storage.expiresAt(uuid);
                LOG.info("raskolspec+: grant " + uuid + " +" + (periods * periodDays) + "d");
                return expiry;
            });
        } catch (ArithmeticException overflow) {
            return CompletableFuture.completedFuture(-1L);
        }
    }

    public CompletableFuture<Boolean> revokeAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return storage.setExpiresAsync(uuid, 0L).thenApply(saved -> {
            if (saved) {
                LOG.info("raskolspec+: revoke " + uuid);
            }
            return saved;
        });
    }

    public CompletableFuture<GiftResult> giftAsync(UUID from, UUID to, int periods, String offlineNote) {
        if (from == null || to == null || periods < 1 || periods > maxGiftPeriods()) {
            return CompletableFuture.completedFuture(GiftResult.INVALID_PERIODS);
        }
        if (from.equals(to)) {
            return CompletableFuture.completedFuture(GiftResult.SELF_GIFT);
        }
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(GiftResult.DISABLED);
        }
        if (!wallet.isAvailable()) {
            return CompletableFuture.completedFuture(GiftResult.WALLET_UNAVAILABLE);
        }

        int cost;
        long duration;
        try {
            cost = Math.multiplyExact(periods, pricePerPeriod());
            duration = TimeUnit.DAYS.toMillis(Math.multiplyExact((long) periods, periodDays));
        } catch (ArithmeticException overflow) {
            return CompletableFuture.completedFuture(GiftResult.INVALID_PERIODS);
        }
        if (cost < 0 || duration <= 0) {
            return CompletableFuture.completedFuture(GiftResult.INVALID_PERIODS);
        }
        if (storage.writeInFlight()) {
            return CompletableFuture.completedFuture(GiftResult.STORAGE_BUSY);
        }
        if (!wallet.take(from, cost)) {
            return CompletableFuture.completedFuture(GiftResult.INSUFFICIENT);
        }

        Player target = plugin.getServer().getPlayer(to);
        String note = target != null && target.isOnline() ? null : offlineNote;
        return storage.extendAsync(to, duration, note).thenApply(saved -> {
            if (!saved) {
                if (!wallet.give(from, cost)) {
                    LOG.severe("raskolspec+: не удалось вернуть списание за подарок после ошибки сохранения для " + from);
                }
                return storage.writeInFlight() ? GiftResult.STORAGE_BUSY : GiftResult.STORAGE_FAILED;
            }
            return GiftResult.OK;
        });
    }

    public boolean active(UUID uuid) {
        return uuid != null && storage.expiresAt(uuid) > System.currentTimeMillis();
    }

    public int remainingDays(UUID uuid) {
        long diff = storage.expiresAt(uuid) - System.currentTimeMillis();
        return diff <= 0L
                ? 0
                : (int) Math.min(Integer.MAX_VALUE, (diff - 1L) / TimeUnit.DAYS.toMillis(1) + 1L);
    }

    public int pricePerPeriod() {
        return Math.max(0, plugin.getRaskolConfig().getIntOr("plus.price-per-period", 100));
    }

    public int periodDays() {
        return periodDays;
    }

    public int maxGiftPeriods() {
        return Math.max(1, plugin.getRaskolConfig().getIntOr("plus.max-gift-periods", 12));
    }

    public boolean isEnabled() {
        return plugin.getRaskolConfig().getBoolOr("plus.enabled", true);
    }

    public boolean walletAvailable() {
        return wallet.isAvailable();
    }

    public boolean storageReady() {
        return !storage.writeInFlight();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        storage.consumeGiftNoteAsync(player.getUniqueId()).thenAccept(note -> {
            if (note == null) {
                return;
            }
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                "⚜ " + note, net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    }
                });
            } catch (IllegalStateException ignored) {
                LOG.warning("raskolspec+: уведомление не отправлено — плагин уже выключается");
            }
        });
    }
}
