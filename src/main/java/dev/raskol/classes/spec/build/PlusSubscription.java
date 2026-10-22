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

/**
 * 1.14.15: сервис подписки raskolspec+ с поддержкой подарков.
 *
 * Публичный контракт:
 *   - grant(uuid, periods): админский путь — выдача без списания;
 *     продление суммирует дни (expiry += periods × periodDays).
 *   - revoke(uuid): админский отзыв.
 *   - gift(from, to, periods): атомарная транзакция: take у from → grant у to;
 *     если take=false → grant не выполняется (атомарность).
 *   - active(uuid): true если expires > now.
 *   - remainingDays(uuid): дней до истечения, 0 если неактивна.
 *   - isOnlineWithNote(uuid): есть ли оффлайн-уведомление о подарке.
 *
 * Слушатель PlayerJoinEvent: если у игрока есть gift-note — шлём уведомление
 * и очищаем поле. Оффлайн-подарок переживает перезагрузку сервера.
 *
 * Permission-поверхность (проверяет PlusSub / команды админа):
 *   - raskolclasses.plus.use: использование (status, apply, gift) — default true;
 *   - raskolclasses.admin.plus: grant/revoke — op only.
 */
public final class PlusSubscription implements Listener {

    private static final Logger LOG = Logger.getLogger("RaskolClasses");

    private final RaskolClasses plugin;
    private final ImperialWallet wallet;
    private final PlusStorage storage;
    private final int periodDays;

    public PlusSubscription(RaskolClasses plugin, ImperialWallet wallet,
                             PlusStorage storage, int periodDays) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.storage = storage;
        this.periodDays = Math.max(1, periodDays);
    }

    /* ------------------------------ grant / revoke ------------------------------ */

    /**
     * Админская выдача подписки. Если уже активна — продлевает (суммирует дни).
     * Возвращает новый expires в epochMillis.
     */
    public long grant(UUID uuid, int periods) {
        if (uuid == null || periods <= 0) {
            return storage.expiresAt(uuid);
        }
        long now = System.currentTimeMillis();
        long current = storage.expiresAt(uuid);
        long base = current > now ? current : now;
        long newExpires = base + TimeUnit.DAYS.toMillis((long) periods * periodDays);
        if (!storage.setExpires(uuid, newExpires)) return -1L;
        LOG.info("raskolspec+: grant " + uuid + " +" + (periods * periodDays) + "d");
        return newExpires;
    }

    /** Админский отзыв. */
    public boolean revoke(UUID uuid) {
        if (uuid == null || !storage.setExpires(uuid, 0L)) return false;
        LOG.info("raskolspec+: revoke " + uuid);
        return true;
    }

    /* ------------------------------ gift ------------------------------ */

    /**
     * Результат подарка.
     *  - OK: списано и подарено;
     *  - INSUFFICIENT: у дарителя не хватило империалов;
     *  - SELF_GIFT: даритель = получатель;
     *  - WALLET_UNAVAILABLE: кошелёк не подключён.
     */
    public enum GiftResult { OK, INSUFFICIENT, SELF_GIFT, WALLET_UNAVAILABLE, STORAGE_FAILED, DISABLED }

    public enum BuyResult { OK, INVALID_PERIODS, INSUFFICIENT, WALLET_UNAVAILABLE, STORAGE_FAILED, DISABLED }

    /** Purchase for the player using PlayerPoints, refunding the debit if persistence fails. */
    public BuyResult buy(UUID player, int periods) {
        if (!isEnabled()) return BuyResult.DISABLED;
        if (player == null || periods < 1
                || periods > plugin.getRaskolConfig().getIntOr("plus.max-gift-periods", 12)) {
            return BuyResult.INVALID_PERIODS;
        }
        if (!wallet.isAvailable()) return BuyResult.WALLET_UNAVAILABLE;
        int price = pricePerPeriod();
        long costLong = (long) periods * price;
        if (price <= 0 || costLong > Integer.MAX_VALUE) return BuyResult.INVALID_PERIODS;
        int cost = (int) costLong;
        if (!wallet.take(player, cost)) return BuyResult.INSUFFICIENT;
        long expires = Math.max(storage.expiresAt(player), System.currentTimeMillis())
                + TimeUnit.DAYS.toMillis((long) periods * periodDays);
        if (!storage.setExpires(player, expires)) {
            wallet.give(player, cost);
            return BuyResult.STORAGE_FAILED;
        }
        LOG.info("raskolspec+: purchase " + player + " " + periods + " period(s), cost=" + cost);
        return BuyResult.OK;
    }

    /**
     * Атомарный подарок: списание у from → выдача to.
     * При неуспешном take — grant НЕ выполняется (деньги не теряются).
     *
     * @param periods число периодов в [1..12] (rate-limit на стороне команды)
     * @param offlineNote текст уведомления оффлайн-получателю (или null)
     */
    public GiftResult gift(UUID from, UUID to, int periods, String offlineNote) {
        if (!isEnabled()) return GiftResult.DISABLED;
        if (from == null || to == null || periods <= 0) {
            return GiftResult.INSUFFICIENT;
        }
        if (from.equals(to)) {
            return GiftResult.SELF_GIFT;
        }
        if (!wallet.isAvailable()) {
            return GiftResult.WALLET_UNAVAILABLE;
        }
        long costLong = (long) periods * pricePerPeriod();
        if (costLong <= 0 || costLong > Integer.MAX_VALUE) return GiftResult.INSUFFICIENT;
        int cost = (int) costLong;
        if (!wallet.take(from, cost)) {
            return GiftResult.INSUFFICIENT;
        }
        long expires = Math.max(storage.expiresAt(to), System.currentTimeMillis())
                + TimeUnit.DAYS.toMillis((long) periods * periodDays);
        if (!storage.setExpires(to, expires)) {
            wallet.give(from, cost);
            return GiftResult.STORAGE_FAILED;
        }
        Player target = plugin.getServer().getPlayer(to);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text(
                    "⚜ Вам подарили raskolspec+ на " + (periods * periodDays) + " дней!",
                    NamedTextColor.GOLD));
        } else if (offlineNote != null && !offlineNote.isBlank()) {
            storage.setGiftNote(to, offlineNote);
        }
        return GiftResult.OK;
    }

    /* ------------------------------ статус ------------------------------ */

    public boolean active(UUID uuid) {
        if (uuid == null) return false;
        return storage.expiresAt(uuid) > System.currentTimeMillis();
    }

    /**
     * Whether a player may use premium builds. The explicit disabled mode is
     * documented as a public preview; it must affect the authoritative check,
     * not just the configuration comments.
     */
    public boolean hasAccess(UUID uuid) {
        return !isEnabled() || active(uuid);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("plus.enabled", true);
    }

    public int remainingDays(UUID uuid) {
        long exp = storage.expiresAt(uuid);
        long diff = exp - System.currentTimeMillis();
        if (diff <= 0L) return 0;
        return (int) (diff / TimeUnit.DAYS.toMillis(1)) + 1;
    }

    public int pricePerPeriod() {
        return plugin.getRaskolConfig().getIntOr("plus.price-per-period", 100);
    }

    public int periodDays() {
        return periodDays;
    }

    /* ------------------------------ оффлайн-уведомления ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        String note = storage.consumeGiftNote(uuid);
        if (note != null) {
            p.sendMessage(Component.text("⚜ " + note, NamedTextColor.GOLD));
        }
    }
}

