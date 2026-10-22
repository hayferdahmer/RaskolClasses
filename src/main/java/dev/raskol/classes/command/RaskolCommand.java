// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.command.sub.CcSub;
import dev.raskol.classes.command.sub.CommandSub;
import dev.raskol.classes.command.sub.DebugSub;
import dev.raskol.classes.command.sub.FoliantSub;
import dev.raskol.classes.command.sub.ItemSub;
import dev.raskol.classes.command.sub.SetSub;
import dev.raskol.classes.command.sub.HealthSub;
import dev.raskol.classes.command.sub.PlusSub;
import dev.raskol.classes.command.sub.SubUtil;
import dev.raskol.classes.gui.ClassBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Исполнитель и автодополнение /rc (1.4.0 → 1.11.4 → 1.13.0 → 1.14.15).
 * 1.11.4 (P4d): РОУТЕР. menu/reload/selftest остаются здесь;
 * debug/gear/foliant/health вынесены в command/sub/*Sub (контракт CommandSub).
 * Права проверяются роутером через CommandSub.permission().
 * 1.13.0 (Б4): добавлен CcSub — /rc cc status/clear/test/reset/list
 *         (пермиссия raskolclasses.admin.cc).
 * 1.14.12 (Sprint 8, Волна 12B, P0-7-02): ветка "menu" проверяет raskolclasses.menu —
 *         нода была в plugin.yml, но не проверялась исполнителем (аудит 7-02).
 * 1.14.12 (Sprint 8, Волна 12B, P0-7-04): per-sender rate-limit 10 с на /rc reload
 *         и /rc selftest — обе команды тяжёлые на main-thread (аудит 7-04).
 * 1.14.15 (raskolspec+): добавлен PlusSub — /rc plus [|status|gift|grant|revoke]
 *         (perm: raskolclasses.plus.use для игроков, raskolclasses.admin.plus для grant/revoke).
 */
public final class RaskolCommand implements CommandExecutor, TabCompleter {

    // 1.13.0 (Б4): "cc" добавлен для таб-комплита /rc <Tab>
    // 1.14.15 (raskolspec+): "plus" добавлен в список подкоманд
    private static final List<String> ROOT_SUBS = List.of(
            "menu", "reload", "debug", "health", "selftest", "gear", "item", "set", "foliant", "cc", "plus");

    /** 1.14.12 (Волна 12B, 7-04): rate-limit для тяжёлых команд (мс). */
    private static final long HEAVY_CMD_COOLDOWN_MS = 10_000L;

    private final RaskolClasses plugin;
    private final Map<String, CommandSub> subs = new LinkedHashMap<>();
    /** 1.14.12 (Волна 12B, 7-04): last-execution per-sender для reload/selftest. */
    private final Map<String, Long> lastHeavyActionMs = new ConcurrentHashMap<>();

    public RaskolCommand(RaskolClasses plugin) {
        this.plugin = plugin;
        for (CommandSub sub : List.of(
                new DebugSub(plugin),
                new ItemSub(plugin),
                new SetSub(plugin),
                new FoliantSub(plugin),
                new HealthSub(plugin),
                // 1.13.0 (Б4): подкоманда /rc cc — админ-поверхность CC/DR
                new CcSub(plugin),
                // 1.14.15 (raskolspec+): подписка на кураторские билды.
                // plusGui уже создан в onEnable до registerCommand — порядок гарантирован.
                new PlusSub(plugin, plugin.getPlusGui()))) {
            subs.put(sub.name(), sub);
        }
    }

    /**
     * 1.14.12 (Волна 12B, 7-04): проверка rate-limit per-sender.
     * Ключ — имя отправителя (console или ник игрока), значение — время последнего вызова.
     * Возвращает true, если действие разрешено (и отмечает его); false — если нужно отказать.
     */
    private boolean rateLimitAllow(String senderKey) {
        long now = System.currentTimeMillis();
        Long prev = lastHeavyActionMs.get(senderKey);
        if (prev != null && now - prev < HEAVY_CMD_COOLDOWN_MS) {
            return false;
        }
        lastHeavyActionMs.put(senderKey, now);
        return true;
    }

    private static String senderKey(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getUniqueId().toString();
        }
        return "console:" + sender.getName();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "gear" -> {
                CommandSub items = subs.get("item");
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                String[] tail = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return items.execute(sender, prependStats(tail));
            }
            case "menu" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Только для игроков.", NamedTextColor.GRAY));
                    return true;
                }
                // 1.14.12 (Волна 12B, 7-02): проверка raskolclasses.menu
                if (!sender.hasPermission("raskolclasses.menu")) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                ClassBook.open(plugin, p, ClassBook.Tab.ABILITIES);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("raskolclasses.admin")) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                // 1.14.12 (Волна 12B, 7-04): rate-limit per-sender
                if (!rateLimitAllow(senderKey(sender))) {
                    sender.sendMessage(Component.text(
                            "Команда /rc reload доступна не чаще раза в 10 секунд.",
                            NamedTextColor.GRAY));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("RaskolClasses: конфигурация перезагружена.",
                        NamedTextColor.GREEN));
                return true;
            }
            case "selftest" -> {
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                // 1.14.12 (Волна 12B, 7-04): rate-limit per-sender
                if (!rateLimitAllow(senderKey(sender))) {
                    sender.sendMessage(Component.text(
                            "Команда /rc selftest доступна не чаще раза в 10 секунд.",
                            NamedTextColor.GRAY));
                    return true;
                }
                dev.raskol.classes.selftest.SelftestRunner.run(plugin, sender);
                return true;
            }
            default -> {
                CommandSub cs = subs.get(sub);
                if (cs == null) {
                    sendHelp(sender);
                    return true;
                }
                if (cs.permission() != null && !sender.hasPermission(cs.permission())) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                return cs.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
    }

    private static String[] prependStats(String[] tail) {
        String[] result = new String[tail.length + 1]; result[0] = "stats";
        System.arraycopy(tail, 0, result, 1, tail.length); return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SubUtil.filter(ROOT_SUBS, args[0]);
        }
        CommandSub cs = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (cs != null) {
            return cs.complete(sender, args);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== /rc ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/rc — сводка: класс, уровень персонажа, ресурс, резисты",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/rc menu — Книга класса (способности, спеки, класс, таланты, шмот)",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Способности 1–5 и инсталляции — только свитками в хотбаре",
                NamedTextColor.GRAY));
        // 1.14.15 (raskolspec+): справка для holders raskolclasses.plus.use
        if (sender.hasPermission("raskolclasses.plus.use")) {
            sender.sendMessage(Component.text("/rc plus — меню raskolspec+ (36 курируемых билдов)",
                    NamedTextColor.GREEN));
            sender.sendMessage(Component.text("/rc plus status [ник] · gift <ник> <n>",
                    NamedTextColor.GREEN));
        }
        if (sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text("/rc debug [player] — диагностика",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc debug simulate [A] [B] [level] — дуэль TTK",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc debug simulate matrix [level] — матрица 6×6",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc gear [player] — экипировка и сеты",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc health — MSPT/TPS/purge", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc selftest — headless-чеки формул (КД 10 с)",
                    NamedTextColor.YELLOW));
        }
        if (sender.hasPermission("raskolclasses.admin")) {
            sender.sendMessage(Component.text("/rc reload — перезагрузить конфигурацию (КД 10 с)",
                    NamedTextColor.RED));
            sender.sendMessage(Component.text("/rc foliant give <ник> — выдать Фолиант Раскола",
                    NamedTextColor.RED));
        }
        // 1.13.0 (Б4): справка /rc cc для holders raskolclasses.admin.cc
        if (sender.hasPermission("raskolclasses.admin.cc")) {
            sender.sendMessage(Component.text("/rc cc status|clear|test|reset|list — CC/DR админка",
                    NamedTextColor.RED));
        }
        // 1.14.15 (raskolspec+): админ-поверхность подписки
        if (sender.hasPermission("raskolclasses.admin.plus")) {
            sender.sendMessage(Component.text("/rc plus grant <ник> <n> · revoke <ник>",
                    NamedTextColor.RED));
        }
    }
}
