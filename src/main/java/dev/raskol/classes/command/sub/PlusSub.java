// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.command.sub;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.PlusGui;
import dev.raskol.classes.spec.build.PlusSubscription;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 1.14.15 (raskolspec+): роутер подкоманд /rc plus.
 *
 * Команды:
 *   /rc plus                      — открыть GUI (player-only, perm: raskolclasses.plus.use)
 *   /rc plus buy [периоды]         — купить или продлить подписку себе за империалы
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
                sender.sendPlainMessage("Используй: /rc plus buy|status|gift|grant|revoke|export");
                return true;
            }
            gui.openCatalog(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "buy" -> cmdBuy(sender, args);
            case "status" -> cmdStatus(sender, args);
            case "gift" -> cmdGift(sender, args);
            case "grant" -> cmdGrant(sender, args);
            case "revoke" -> cmdRevoke(sender, args);
            case "export" -> cmdExport(sender);
            default ->
                sender.sendPlainMessage(
                        "Неизвестная подкоманда: " + sub + ". Доступны: buy, status, gift, grant, revoke, export.");
        }
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("buy", "status", "gift", "grant", "revoke", "export");
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("gift")
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

    private void cmdBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendPlainMessage("Покупка подписки доступна только игроку.");
            return;
        }
        SenderSession initiator = captureSender(player);
        UUID playerId = player.getUniqueId();
        PlusSubscription subscription = plugin.getPlusSubscription();
        int periods = 1;
        if (args.length >= 2) {
            try {
                periods = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(err("Число периодов должно быть целым"));
                return;
            }
        }
        int requestedPeriods = periods;
        subscription
                .buyAsync(playerId, requestedPeriods)
                .whenComplete((result, failure) -> runOnMain("plus purchase for " + playerId, initiator, currentSender -> {
                    if (failure != null) {
                        currentSender.sendMessage(err(
                                "Не удалось завершить операцию подписки. Проверь баланс и обратись к администрации."));
                        return;
                    }
                    switch (result) {
                        case OK -> {
                            int days = requestedPeriods * subscription.periodDays();
                            currentSender.sendMessage(ok("⚜ raskolspec+ продлена на " + days
                                    + " дн. За покупку списано "
                                    + (requestedPeriods * subscription.pricePerPeriod()) + " империалов."));
                            if (currentSender instanceof Player currentPlayer && gui != null) {
                                gui.openCatalog(currentPlayer);
                            }
                        }
                        case DISABLED ->
                            currentSender.sendMessage(
                                    err("Платная подписка сейчас отключена; шаблоны открыты в тестовом режиме."));
                        case WALLET_UNAVAILABLE ->
                            currentSender.sendMessage(err("Кошелёк империалов недоступен — покупка отключена."));
                        case INVALID_PERIODS ->
                            currentSender.sendMessage(err("Не удалось рассчитать срок или стоимость подписки."));
                        case INSUFFICIENT ->
                            currentSender.sendMessage(err("Недостаточно империалов: нужно "
                                    + (requestedPeriods * subscription.pricePerPeriod()) + ", у вас "
                                    + plugin.getImperialWallet().balance(playerId) + "."));
                        case STORAGE_BUSY ->
                            currentSender.sendMessage(
                                    err("Хранилище подписок занято другой операцией. Повтори позже."));
                        case STORAGE_FAILED ->
                            currentSender.sendMessage(err("Подписка не сохранена. Списание отменено; проверь баланс."));
                    }
                }));
    }

    private void cmdStatus(CommandSender sender, String[] args) {
        PlusSubscription sub = plugin.getPlusSubscription();
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("raskolclasses.admin.plus")) {
                sender.sendMessage(err(plugin.getRaskolConfig().message("no-permission", "Недостаточно прав")));
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
                            .message("plus.status-active", "⚜ raskolspec+ активна · осталось {days} дн.")
                            .replace("{days}", String.valueOf(sub.remainingDays(target.getUniqueId())))
                    + " (" + target.getName() + ")"));
        } else {
            sender.sendMessage(err(plugin.getRaskolConfig().message("plus.status-inactive", "⚜ raskolspec+ не активна")
                    + " (" + target.getName() + ")"));
        }
    }

    /* ------------------------------ gift ------------------------------ */

    private void cmdGift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendPlainMessage("Подарок доступен только игроку.");
            return;
        }
        if (args.length < 3) {
            from.sendMessage(err("Используй: /rc plus gift <ник> <периоды>"));
            return;
        }
        long cooldown = plugin.getRaskolConfig().getLongOr("plus.cooldown-gift-ms", 5000L);
        long now = System.currentTimeMillis();
        Long last = lastGiftMs.get(from.getUniqueId());
        if (last != null && now - last < cooldown) {
            long sec = (cooldown - (now - last) + 999) / 1000;
            from.sendMessage(err(plugin.getRaskolConfig()
                    .message("plus.gift-cooldown", "Слишком часто: повтор через {sec} с")
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
        if (periods < 1 || periods > 365) {
            from.sendMessage(err("Число периодов должно быть от 1 до 365"));
            return;
        }
        PlusSubscription subscription = plugin.getPlusSubscription();
        SenderSession initiator = captureSender(from);
        UUID senderId = from.getUniqueId();
        String senderName = from.getName();
        UUID recipientId = to.getUniqueId();
        String recipientName = to.getName();
        String giftNote = plugin.getRaskolConfig()
                .message("plus.gift-received", "⚜ {sender} подарил вам raskolspec+ на {days} дн.!")
                .replace("{sender}", senderName)
                .replace("{days}", String.valueOf(periods * subscription.periodDays()));
        int requestedPeriods = periods;
        // Cooldown фиксируется при принятии корректной команды, а не в UI completion.
        lastGiftMs.put(senderId, now);
        subscription
                .giftAsync(senderId, recipientId, requestedPeriods, giftNote)
                .whenComplete((result, failure) -> runOnMain("plus gift from " + senderId + " to " + recipientId, initiator, currentSender -> {
                    if (failure != null) {
                        currentSender.sendMessage(
                                err("Не удалось завершить подарок. Проверь баланс и обратись к администрации."));
                        return;
                    }
                    switch (result) {
                        case OK -> {
                            int days = requestedPeriods * subscription.periodDays();
                            currentSender.sendMessage(ok(plugin.getRaskolConfig()
                                    .message("plus.gift-sent", "⚜ Подписка raskolspec+ подарена {target} на {days} дн.")
                                    .replace("{target}", recipientName)
                                    .replace("{days}", String.valueOf(days))));
                        }
                        case INSUFFICIENT -> {
                            int cost = requestedPeriods * subscription.pricePerPeriod();
                            int balance = plugin.getImperialWallet().balance(senderId);
                            currentSender.sendMessage(err(plugin.getRaskolConfig()
                                    .message("plus.gift-poor", "Не хватает империалов: нужно {cost}, у вас {balance}")
                                    .replace("{cost}", String.valueOf(cost))
                                    .replace("{balance}", String.valueOf(balance))));
                        }
                        case SELF_GIFT -> currentSender.sendMessage(err("Нельзя подарить подписку самому себе"));
                        case WALLET_UNAVAILABLE -> currentSender.sendMessage(err("Кошелёк империалов недоступен"));
                        case DISABLED ->
                            currentSender.sendMessage(err("Платная подписка отключена; подарки сейчас недоступны."));
                        case INVALID_PERIODS -> currentSender.sendMessage(err("Некорректное число периодов."));
                        case STORAGE_BUSY ->
                            currentSender.sendMessage(
                                    err("Хранилище подписок занято другой операцией. Повтори позже."));
                        case STORAGE_FAILED ->
                            currentSender.sendMessage(err("Подарок не сохранён. Списание отменено; проверь баланс."));
                    }
                }));
    }

    private void cmdGrant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig().message("no-permission", "Недостаточно прав")));
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
        SenderSession initiator = captureSender(sender);
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        PlusSubscription sub = plugin.getPlusSubscription();
        int requestedPeriods = periods;
        sub.grantAsync(targetId, requestedPeriods)
                .whenComplete((expires, failure) -> runOnMain("plus grant to " + targetId, initiator, currentSender -> {
                    if (failure != null || expires == null || expires < 0L) {
                        currentSender.sendMessage(err("Не удалось сохранить подписку; игроку срок не выдан."));
                        return;
                    }
                    int days = requestedPeriods * sub.periodDays();
                    currentSender.sendMessage(
                            ok("⚜ " + targetName + ": подписка активна, + " + days + " дн. (expires=" + expires + ")"));
                }));
    }

    private void cmdRevoke(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig().message("no-permission", "Недостаточно прав")));
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
        SenderSession initiator = captureSender(sender);
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        plugin.getPlusSubscription()
                .revokeAsync(targetId)
                .whenComplete((saved, failure) -> runOnMain("plus revoke for " + targetId, initiator, currentSender -> {
                    if (failure != null || !Boolean.TRUE.equals(saved)) {
                        currentSender.sendMessage(err("Не удалось сохранить отзыв подписки."));
                        return;
                    }
                    currentSender.sendMessage(ok("⚜ " + targetName + ": подписка отозвана"));
                }));
    }

    private void cmdExport(CommandSender sender) {
        if (!sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(err(plugin.getRaskolConfig().message("no-permission", "Недостаточно прав")));
            return;
        }
        SenderSession initiator = captureSender(sender);
        plugin.getBuildTemplates()
                .exportNormalizedAsync()
                .whenComplete((saved, failure) -> runOnMain("plus template export", initiator, currentSender -> {
                    if (failure != null || !Boolean.TRUE.equals(saved)) {
                        currentSender.sendMessage(err("Не удалось записать builds.yml — смотри лог сервера."));
                        return;
                    }
                    currentSender.sendMessage(ok("⚜ builds.yml сохранён нормализованными шаблонами. "
                            + "Память уже согласована; после следующего /rc reload или рестарта "
                            + "логи нормализации исчезнут."));
                }));
    }

    private SenderSession captureSender(CommandSender sender) {
        if (sender instanceof Player player) {
            return new SenderSession(player.getUniqueId(), new WeakReference<>(player), false);
        }
        return new SenderSession(null, null, sender == Bukkit.getConsoleSender());
    }

    private void runOnMain(String operation, SenderSession initiator, Consumer<CommandSender> action) {
        plugin.dispatchAsyncCompletion("raskolspec+ " + operation, () -> {
            CommandSender current = resolveSender(initiator);
            if (current == null) {
                plugin.getLogger().warning(
                        "raskolspec+: результат операции не доставлен [" + operation
                                + "] — исходная сессия отправителя завершена");
                return;
            }
            action.accept(current);
        });
    }

    private CommandSender resolveSender(SenderSession initiator) {
        if (initiator.console()) {
            return Bukkit.getConsoleSender();
        }
        if (initiator.playerId() == null) {
            return null;
        }
        Player originalSession = initiator.playerSession().get();
        Player current = Bukkit.getPlayer(initiator.playerId());
        return originalSession != null && current == originalSession && current.isOnline() ? current : null;
    }

    private record SenderSession(UUID playerId, WeakReference<Player> playerSession, boolean console) {}

    private static Component ok(String raw) {
        return Component.text(raw, NamedTextColor.GREEN);
    }

    private static Component err(String raw) {
        return Component.text(raw, NamedTextColor.RED);
    }
}
