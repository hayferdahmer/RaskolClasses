// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Subscription service: durable grants, self-purchase, gifts and expiry checks. */
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

    public enum PurchaseResult { OK, DISABLED, WALLET_UNAVAILABLE, INVALID_PERIODS, INSUFFICIENT, STORAGE_FAILED }
    public enum GiftResult { OK, DISABLED, INSUFFICIENT, SELF_GIFT, WALLET_UNAVAILABLE, INVALID_PERIODS, STORAGE_FAILED }

    /** Buy/renew for the caller. Wallet debit is compensated if durable storage rejects the extension. */
    public PurchaseResult buy(UUID player, int periods) {
        if (player == null || periods < 1 || periods > maxGiftPeriods()) return PurchaseResult.INVALID_PERIODS;
        if (!isEnabled()) return PurchaseResult.DISABLED;
        if (!wallet.isAvailable()) return PurchaseResult.WALLET_UNAVAILABLE;
        int cost;
        long duration;
        try {
            cost = Math.multiplyExact(periods, pricePerPeriod());
            duration = TimeUnit.DAYS.toMillis(Math.multiplyExact((long) periods, periodDays));
        } catch (ArithmeticException overflow) { return PurchaseResult.INVALID_PERIODS; }
        if (cost < 0 || duration <= 0) return PurchaseResult.INVALID_PERIODS;
        if (!wallet.take(player, cost)) return PurchaseResult.INSUFFICIENT;
        if (!storage.extend(player, duration, null)) {
            if (!wallet.give(player, cost)) LOG.severe("raskolspec+: не удалось вернуть списание после ошибки сохранения для " + player);
            return PurchaseResult.STORAGE_FAILED;
        }
        LOG.info("raskolspec+: purchase " + player + " periods=" + periods);
        return PurchaseResult.OK;
    }

    /** Admin grant. Returns -1 if validation or durable persistence fails. */
    public long grant(UUID uuid, int periods) {
        if (uuid == null || periods <= 0) return -1L;
        try {
            long duration = TimeUnit.DAYS.toMillis(Math.multiplyExact((long) periods, periodDays));
            if (!storage.extend(uuid, duration, null)) return -1L;
            long expiry = storage.expiresAt(uuid);
            LOG.info("raskolspec+: grant " + uuid + " +" + (periods * periodDays) + "d");
            return expiry;
        } catch (ArithmeticException overflow) { return -1L; }
    }

    public boolean revoke(UUID uuid) {
        if (uuid == null) return false;
        boolean saved = storage.setExpires(uuid, 0L);
        if (saved) LOG.info("raskolspec+: revoke " + uuid);
        return saved;
    }

    /** Debits first, persists the recipient's extension and offline note atomically, then notifies. */
    public GiftResult gift(UUID from, UUID to, int periods, String offlineNote) {
        if (from == null || to == null || periods < 1 || periods > maxGiftPeriods()) return GiftResult.INVALID_PERIODS;
        if (from.equals(to)) return GiftResult.SELF_GIFT;
        if (!isEnabled()) return GiftResult.DISABLED;
        if (!wallet.isAvailable()) return GiftResult.WALLET_UNAVAILABLE;
        int cost;
        long duration;
        try {
            cost = Math.multiplyExact(periods, pricePerPeriod());
            duration = TimeUnit.DAYS.toMillis(Math.multiplyExact((long) periods, periodDays));
        } catch (ArithmeticException overflow) { return GiftResult.INVALID_PERIODS; }
        if (cost < 0 || duration <= 0) return GiftResult.INVALID_PERIODS;
        if (!wallet.take(from, cost)) return GiftResult.INSUFFICIENT;
        Player target = plugin.getServer().getPlayer(to);
        String note = target != null && target.isOnline() ? null : offlineNote;
        if (!storage.extend(to, duration, note)) {
            if (!wallet.give(from, cost)) LOG.severe("raskolspec+: не удалось вернуть списание за подарок после ошибки сохранения для " + from);
            return GiftResult.STORAGE_FAILED;
        }
        if (target != null && target.isOnline()) target.sendMessage(Component.text(
                "⚜ Вам подарили raskolspec+ на " + (periods * periodDays) + " дней!", NamedTextColor.GOLD));
        return GiftResult.OK;
    }

    public boolean active(UUID uuid) { return uuid != null && storage.expiresAt(uuid) > System.currentTimeMillis(); }
    public int remainingDays(UUID uuid) {
        long diff = storage.expiresAt(uuid) - System.currentTimeMillis();
        return diff <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, (diff - 1L) / TimeUnit.DAYS.toMillis(1) + 1L);
    }
    public int pricePerPeriod() { return Math.max(0, plugin.getRaskolConfig().getIntOr("plus.price-per-period", 100)); }
    public int periodDays() { return periodDays; }
    public int maxGiftPeriods() { return Math.max(1, plugin.getRaskolConfig().getIntOr("plus.max-gift-periods", 12)); }
    public boolean isEnabled() { return plugin.getRaskolConfig().getBoolOr("plus.enabled", true); }
    public boolean walletAvailable() { return wallet.isAvailable(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String note = storage.consumeGiftNote(player.getUniqueId());
        if (note != null) player.sendMessage(Component.text("⚜ " + note, NamedTextColor.GOLD));
    }
}
