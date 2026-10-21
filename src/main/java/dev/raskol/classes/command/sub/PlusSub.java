// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.PlusGui;
import dev.raskol.classes.spec.build.PlusSubscription;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 1.14.15 (raskolspec+): роутер подкоманд /rc plus.
 *
 * Команды:
 *   /rc plus                      — открыть GUI (player-only, perm: raskolclasses.plus.use)
 *   /rc plus status [ник]         — статус подписки (player-only / [ник] op-only)
 *   /rc plus gift <ник> <периоды> — подарить (player-only, perm: raskolclasses.plus.use,
 *                                   rate-limit по plus.cooldown-gift-ms)
 *   /rc plus grant <ник> <периоды> — админская выдача (perm: raskolclasses.admin.plus)
 *   /rc plus revoke <ник>          — админский отзыв (perm: raskolclasses.admin.plus)
 *
 * /rc plus apply <pvp|pve> — оставлено на 15B-финал как утилита для тестов GUI;
 * пока не роутится. Основной путь применения — через GUI-флоу.
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

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendPlainMessage("Используй: /rc plus status|gift|grant|revoke <ник>");
                return;
            }
            if (!player.hasPermission("raskolclasses.plus.use")) {
                player.sendMessage(err(plugin.getRaskolConfig()
                        .message("messages.no-permission", "Недостаточно прав")));
                return;
            }
            gui.openCatalog(player);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> cmdStatus(sender, args);
            case "gift" -> cmdGift(sender, args);
            case "grant" -> cmdGrant(sender, args);
            case "revoke" -> cmdRevoke(sender, args);
            default -> sender.sendPlainMessage("Неизвестная подкоманда: " + sub
                    + ". Доступны: status, gift, grant, revoke.");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("status", "gift", "grant", "revoke");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("gift")
                || args[0].equalsIgnoreCase("grant")
                || args[0].equalsIgnoreCase("revoke")
                || args[0].equalsIgnoreCase("status"))) {
            return null; // таб-комплит ников — стандартный механизм Bukkit
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
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("raskolclasses.admin.plus")) {
                sender.sendMessage(err(plugin.getRaskolConfig()
                        .message("messages.no-permission", "Недостаточно прав")));
                return;
            }
            target = Bukkit.getPlayer(args[1]);
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
                    + " (" + target.getName() + ")"));
        } else {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.status-inactive", "⚜ raskolspec+ не активна")
                    + " (" + target.getName() + ")"));
        }
    }

    /* ------------------------------ gift ------------------------------ */

    private void cmdGift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendPlainMessage("Подарок доступен только игроку.");
            return;
        }
        if (!from.hasPermission("raskolclasses.plus.use")) {
            from.sendMessage(err(plugin.getRaskolConfig()
                    .message("messages.no-permission", "Недостаточно прав")));
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

        Player to = Bukkit.getPlayer(args[1]);
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

        PlusSubscription sub = plugin.getPlusSubscription();
        String offlineNote = plugin.getRaskolConfig()
                .message("plus.gift-offline-note",
                        "{sender} подарил вам raskolspec+ на {days} дн.")
                .replace("{sender}", from.getName())
                .replace("{days}", String.valueOf(periods * sub.periodDays()));

        PlusSubscription.GiftResult result = sub.gift(
                from.getUniqueId(), to.getUniqueId(), periods, offlineNote);

        switch (result) {
            case OK -> {
                lastGiftMs.put(from.getUniqueId(), now);
                int days = periods * sub.periodDays();
                from.sendMessage(ok(plugin.getRaskolConfig()
                        .message("plus.gift-sent",
                                "⚜ Подписка raskolspec+ подарена {target} на {days} дн.")
                        .replace("{target}", to.getName())
                        .replace("{days}", String.valueOf(days))));
            }
            case INSUFFICIENT -> {
                int cost = periods * sub.pricePerPeriod();
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
        }
    }

    /* ------------------------------ grant (admin) ------------------------------ */

    private void cmdGrant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("messages.no-permission", "Недостаточно прав")));
            return;
        }
        if (args.length < 3) {
            sender.sendPlainMessage("Используй: /rc plus grant <ник> <периоды>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
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
        int days = periods * sub.periodDays();
        sender.sendMessage(ok("⚜ " + target.getName()
                + ": подписка активна, + " + days + " дн. (expires=" + expires + ")"));
    }

    /* ------------------------------ revoke (admin) ------------------------------ */

    private void cmdRevoke(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig()
                    .message("messages.no-permission", "Недостаточно прав")));
            return;
        }
        if (args.length < 2) {
            sender.sendPlainMessage("Используй: /rc plus revoke <ник>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(err("Игрок не найден: " + args[1]));
            return;
        }
        plugin.getPlusSubscription().revoke(target.getUniqueId());
        sender.sendMessage(ok("⚜ " + target.getName() + ": подписка отозвана"));
    }

    private static Component ok(String raw) {
        return Component.text(raw, NamedTextColor.GREEN);
    }

    private static Component err(String raw) {
        return Component.text(raw, NamedTextColor.RED);
    }
}
