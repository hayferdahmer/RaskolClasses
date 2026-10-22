// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.PlusGui;
import dev.raskol.classes.spec.build.PlusSubscription;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.14.15 (raskolspec+): роутер подкоманд /rc plus.
 *
 * Команды:
 *   /rc plus                      — открыть GUI (player-only, perm: raskolclasses.plus.use)
 *   /rc plus buy <периоды>        — купить себе подписку за PlayerPoints
 *   /rc plus status [ник]         — статус подписки (player-only / [ник] op-only)
 *   /rc plus gift <ник> <периоды> — подарить (player-only, perm: raskolclasses.plus.use,
 *                                   rate-limit по plus.cooldown-gift-ms)
 *   /rc plus grant <ник> <периоды> — админская выдача (perm: raskolclasses.admin.plus)
 *   /rc plus revoke <ник>          — админский отзыв (perm: raskolclasses.admin.plus)
 *   /rc plus export                — 1.14.16-fix2: перезаписать builds.yml нормализованными
 *                                   шаблонами (perm: raskolclasses.admin.plus)
 *
 * Основной путь применения шаблонов — через GUI-флоу (/rc plus → карточка → двойной клик).
 *
 * Контракт CommandSub: name/execute/complete/permission — синхронизирован с роутером
 * RaskolCommand. execute возвращает boolean (true = команда обработана).
 */
public final class PlusSub implements CommandSub {

    private final RaskolClasses plugin;
    private final PlusGui gui;
    private final Map<UUID, Long> lastGiftMs = new ConcurrentHashMap<>();

    public PlusSub(RaskolClasses plugin, PlusGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public String name() {
        return "plus";
    }

    /** Базовая нода для /rc plus и /rc plus status/gift. Admin-подкоманды проверяют admin.plus внутри. */
    @Override
    public String permission() {
        return "raskolclasses.plus.use";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendPlainMessage("Используй: /rc plus status|gift|grant|revoke|export");
                return true;
            }
            gui.openCatalog(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> cmdStatus(sender, args);
            case "gift" -> cmdGift(sender, args);
            case "buy" -> cmdBuy(sender, args);
            case "grant" -> cmdGrant(sender, args);
            case "revoke" -> cmdRevoke(sender, args);
            case "export" -> cmdExport(sender);
            default -> sender.sendPlainMessage("Неизвестная подкоманда: " + sub
                    + ". Доступны: status, buy, gift, grant, revoke, export.");
        }
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("status", "buy", "gift", "grant", "revoke", "export");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("gift")
                || args[0].equalsIgnoreCase("grant")
                || args[0].equalsIgnoreCase("revoke")
                || args[0].equalsIgnoreCase("status"))) {
            return null; // таб-комплит ников — стандартный механизм Bukkit
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            return List.of("1", "3", "6", "12");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("gift")) {
            return List.of("1", "3", "6", "12");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
            return List.of("1", "3", "6", "12");
        }
        return List.of();
    }

    /* ------------------------------ status ------------------------------ */

    private void cmdStatus(CommandSender sender, String[] args) {
        PlusSubscription sub = plugin.getPlusSubscription();
        OfflinePlayer target;
        if (args.length >= 2) {
            if (!sender.hasPermission("raskolclasses.admin.plus")) {
                sender.sendMessage(err(plugin.getRaskolConfig()
                        .message("no-permission", "Недостаточно прав")));
                return;
            }
            target = findKnownPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(err("Игрок не найден: " + args[1]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendPlainMessage("Укажи ник: /rc plus status <ник>");
            return;
        }
        boolean active = sub.active(target.getUniqueId());
        if (active) {
            sender.sendMessage(ok(plugin.getRaskolConfig()
                    .message("plus.status-active",
                            "⚜ raskolspec+ активна · осталось {days} дн.")
                    .replace("{days}", String.valueOf(sub.remainingDays(target.getUniqueId())))
                    + " (" + playerName(target, args.length >= 2 ? args[1] : target.getName()) + ")"));
        } else {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.status-inactive", "⚜ raskolspec+ не активна")
                    + " (" + playerName(target, args.length >= 2 ? args[1] : target.getName()) + ")"));
        }
    }

    /* ------------------------------ gift ------------------------------ */

    private void cmdBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendPlainMessage("Покупка доступна только игроку.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(err("Используй: /rc plus buy <периоды>"));
            return;
        }
        int periods;
        try {
            periods = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(err("Число периодов должно быть целым"));
            return;
        }
        PlusSubscription sub = plugin.getPlusSubscription();
        PlusSubscription.BuyResult result = sub.buy(player.getUniqueId(), periods);
        switch (result) {
            case OK -> player.sendMessage(ok("⚜ raskolspec+ продлена на "
                    + (periods * sub.periodDays()) + " дн. Стоимость: "
                    + (periods * sub.pricePerPeriod()) + " империалов."));
            case INVALID_PERIODS -> player.sendMessage(err("Период должен быть от 1 до "
                    + plugin.getRaskolConfig().getIntOr("plus.max-gift-periods", 12)
                    + "; проверь цену в конфигурации."));
            case INSUFFICIENT -> player.sendMessage(err("Недостаточно империалов: нужно "
                    + ((long) periods * sub.pricePerPeriod()) + ", у вас "
                    + plugin.getImperialWallet().balance(player.getUniqueId())));
            case WALLET_UNAVAILABLE -> player.sendMessage(err("Кошелёк империалов недоступен"));
            case STORAGE_FAILED -> player.sendMessage(err("Покупка отменена: не удалось сохранить подписку. "
                    + "Списание возвращено; сообщи администратору, если баланс не восстановился."));
            case DISABLED -> player.sendMessage(err("Покупка raskolspec+ временно отключена"));
        }
    }

    private void cmdGift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendPlainMessage("Подарок доступен только игроку.");
            return;
        }
        if (args.length < 3) {
            from.sendMessage(err("Используй: /rc plus gift <ник> <периоды>"));
            return;
        }

        // Rate-limit
        long cooldown = plugin.getRaskolConfig()
                .getLongOr("plus.cooldown-gift-ms", 5000L);
        long now = System.currentTimeMillis();
        Long last = lastGiftMs.get(from.getUniqueId());
        if (last != null && now - last < cooldown) {
            long sec = (cooldown - (now - last) + 999) / 1000;
            from.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.gift-cooldown",
                            "Слишком часто: повтор через {sec} с")
                    .replace("{sec}", String.valueOf(sec))));
            return;
        }

        OfflinePlayer to = findKnownPlayer(args[1]);
        if (to == null) {
            from.sendMessage(err("Игрок не найден: " + args[1]));
            return;
        }

        int periods;
        try {
            periods = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            from.sendMessage(err("Число периодов должно быть целым"));
            return;
        }
        int maxPeriods = plugin.getRaskolConfig().getIntOr("plus.max-gift-periods", 12);
        if (periods < 1 || periods > maxPeriods) {
            from.sendMessage(err("Периодов должно быть от 1 до " + maxPeriods));
            return;
        }

        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.gift-self", "Нельзя подарить подписку самому себе")));
            return;
        }

        PlusSubscription subscription = plugin.getPlusSubscription();
        String offlineNote = plugin.getRaskolConfig()
                .message("plus.gift-offline-note",
                        "{sender} подарил вам raskolspec+ на {days} дн.")
                .replace("{sender}", from.getName())
                .replace("{days}", String.valueOf(periods * subscription.periodDays()));

        PlusSubscription.GiftResult result = subscription.gift(
                from.getUniqueId(), to.getUniqueId(), periods, offlineNote);

        switch (result) {
            case OK -> {
                lastGiftMs.put(from.getUniqueId(), now);
                int days = periods * subscription.periodDays();
                from.sendMessage(ok(plugin.getRaskolConfig()
                        .message("plus.gift-sent",
                                "⚜ Подписка raskolspec+ подарена {target} на {days} дн.")
                        .replace("{target}", playerName(to, args[1]))
                        .replace("{days}", String.valueOf(days))));
            }
            case INSUFFICIENT -> {
                int cost = periods * subscription.pricePerPeriod();
                int balance = plugin.getImperialWallet().balance(from.getUniqueId());
                from.sendMessage(err(plugin.getRaskolConfig()
                        .message("plus.gift-poor",
                                "Не хватает империалов: нужно {cost}, у вас {balance}")
                        .replace("{cost}", String.valueOf(cost))
                        .replace("{balance}", String.valueOf(balance))));
            }
            case SELF_GIFT -> from.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.gift-self",
                            "Нельзя подарить подписку самому себе")));
            case WALLET_UNAVAILABLE -> from.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.gift-wallet-off",
                            "Кошелёк империалов недоступен")));
            case STORAGE_FAILED -> from.sendMessage(err("Подарок отменён: не удалось сохранить подписку. "
                    + "Списание возвращено; сообщи администратору, если баланс не восстановился."));
            case DISABLED -> from.sendMessage(err("Подарки raskolspec+ временно отключены"));
        }
    }

    /* ------------------------------ grant (admin) ------------------------------ */

    private void cmdGrant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав")));
            return;
        }
        if (args.length < 3) {
            sender.sendPlainMessage("Используй: /rc plus grant <ник> <периоды>");
            return;
        }
        OfflinePlayer target = findKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(err("Игрок не найден: " + args[1]));
            return;
        }
        int periods;
        try {
            periods = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendPlainMessage("Число периодов должно быть целым");
            return;
        }
        if (periods < 1 || periods > 365) {
            sender.sendPlainMessage("Периодов должно быть от 1 до 365");
            return;
        }
        PlusSubscription sub = plugin.getPlusSubscription();
        long expires = sub.grant(target.getUniqueId(), periods);
        if (expires < 0L) {
            sender.sendMessage(err("Не удалось сохранить подписку; смотри лог сервера."));
            return;
        }
        int days = periods * sub.periodDays();
        sender.sendMessage(ok("⚜ " + playerName(target, args[1])
                + ": подписка активна, + " + days + " дн. (expires=" + expires + ")"));
    }

    /* ------------------------------ revoke (admin) ------------------------------ */

    private void cmdRevoke(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав")));
            return;
        }
        if (args.length < 2) {
            sender.sendPlainMessage("Используй: /rc plus revoke <ник>");
            return;
        }
        OfflinePlayer target = findKnownPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(err("Игрок не найден: " + args[1]));
            return;
        }
        if (!plugin.getPlusSubscription().revoke(target.getUniqueId())) {
            sender.sendMessage(err("Не удалось сохранить отзыв подписки; смотри лог сервера."));
            return;
        }
        sender.sendMessage(ok("⚜ " + playerName(target, args[1]) + ": подписка отозвана"));
    }

    /* ------------------------------ export (admin, 1.14.16-fix2) ------------------------------ */

    /**
     * Перезаписать builds.yml нормализованными шаблонами (то, что фактически
     * загружено и применимо). Убирает INFO-строки «нормализовано по стоп-листу/гейтам»
     * из логов старта до следующего баланс-патча, меняющего стоп-лист/гейты.
     */
    private void cmdExport(CommandSender sender) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав")));
            return;
        }
        boolean ok = plugin.getBuildTemplates().exportNormalized();
        if (ok) {
            sender.sendMessage(ok("⚜ builds.yml перезаписан нормализованными шаблонами. "
                    + "Память уже согласована; после следующего /rc reload или рестарта "
                    + "логи нормализации исчезнут."));
        } else {
            sender.sendMessage(err("Не удалось записать builds.yml — смотри лог сервера."));
        }
    }

    private static Component ok(String raw) {
        return Component.text(raw, NamedTextColor.GREEN);
    }

    private static Component err(String raw) {
        return Component.text(raw, NamedTextColor.RED);
    }

    /** Resolve online or cached offline players without a blocking profile lookup. */
    private static OfflinePlayer findKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private static String playerName(OfflinePlayer player, String fallback) {
        String name = player.getName();
        return name == null || name.isBlank() ? fallback : name;
    }
}

