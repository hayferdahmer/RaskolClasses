// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.cc.CCInstance;
import dev.raskol.classes.cc.CCService;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.cc.DRCategory;
import dev.raskol.classes.cc.DRState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1.13.0 (Б4): подкоманда /rc cc — админ-поверхность CC/DR.
 *  status <player>          — активные CC (тип, остаток, DR-множитель) + DR-стеки с таймером окна;
 *  clear <player>           — снять все CC и сбросить весь DR;
 *  test <player> <type>     — наложить тестовый CC (полный путь tryApply: иммунитеты/resist/DR);
 *  reset <player> <category>— сбросить DR-стек одной категории;
 *  list                     — таблица CCType → категория DR.
 * Реализует CommandSub (тот же контракт, что DebugSub): регистрация в RaskolCommand
 * одной строкой `new CcSub(plugin)`.
 */
public final class CcSub implements CommandSub {

    private final RaskolClasses plugin;

    public CcSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "cc";
    }

    @Override
    public String permission() {
        return "raskolclasses.admin.cc";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission())) {
            sender.sendMessage(Component.text("Недостаточно прав", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> statusCmd(sender, args);
            case "clear" -> clearCmd(sender, args);
            case "test" -> testCmd(sender, args);
            case "reset" -> resetCmd(sender, args);
            case "list" -> listCmd(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] fullArgs) {
        if (fullArgs.length == 2) {
            return SubUtil.filter(List.of("status", "clear", "test", "reset", "list"), fullArgs[1]);
        }
        String sub = fullArgs.length > 1 ? fullArgs[1].toLowerCase(Locale.ROOT) : "";
        if (fullArgs.length == 3 && (sub.equals("status") || sub.equals("clear")
                || sub.equals("test") || sub.equals("reset"))) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[2]);
        }
        if (fullArgs.length == 4 && sub.equals("test")) {
            List<String> types = new ArrayList<>();
            for (CCType t : CCType.values()) {
                types.add(t.id());
            }
            return SubUtil.filter(types, fullArgs[3]);
        }
        if (fullArgs.length == 4 && sub.equals("reset")) {
            List<String> cats = new ArrayList<>();
            for (DRCategory c : DRCategory.values()) {
                cats.add(c.id());
            }
            return SubUtil.filter(cats, fullArgs[3]);
        }
        return List.of();
    }

    /* ------------------------------ подкоманды ------------------------------ */

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── /rc cc ──", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" status <player> — активные CC и DR-стеки", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" clear <player> — снять все CC и сбросить DR", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" test <player> <type> — наложить тестовый CC", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" reset <player> <category> — сброс DR-стека категории", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" list — список CCType и категорий DR", NamedTextColor.GRAY));
    }

    private void statusCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /rc cc status <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        CCService cc = plugin.getCC();
        long now = System.currentTimeMillis();
        sender.sendMessage(Component.text("=== CC: " + target.getName() + " ===", NamedTextColor.GOLD));
        List<CCInstance> active = cc.activeOf(target.getUniqueId());
        if (active.isEmpty()) {
            sender.sendMessage(Component.text(" Активных CC: —", NamedTextColor.GRAY));
        } else {
            for (CCInstance inst : active) {
                sender.sendMessage(Component.text(" " + inst.type().ruName() + " (" + inst.type().id()
                        + "): " + (inst.remainingTicks(now) / 20L) + "с, DR "
                        + (int) Math.round(inst.drMultiplier() * 100.0) + "%",
                        NamedTextColor.RED));
            }
        }
        long windowMs = (long) (plugin.getConfig().getDouble("cc.window-seconds", 15.0) * 1000.0);
        for (DRCategory cat : DRCategory.values()) {
            DRState st = cc.drState(target.getUniqueId(), cat);
            if (st.stackCount() <= 0) {
                continue;
            }
            long left = Math.max(0L, windowMs - (now - st.lastAppliedAt()));
            sender.sendMessage(Component.text(" DR " + cat.ruName() + ": стек " + st.stackCount()
                    + ", окно сбросится через " + (left / 1000L) + "с", NamedTextColor.DARK_AQUA));
        }
    }

    private void clearCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /rc cc clear <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        plugin.getCC().removeAll(target.getUniqueId());
        plugin.getCC().resetAllDr(target.getUniqueId());
        sender.sendMessage(Component.text("CC сняты и DR сброшен у " + target.getName(), NamedTextColor.GREEN));
    }

    private void testCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /rc cc test <player> <type>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        CCType type = CCType.fromId(args[2]);
        if (type == null) {
            sender.sendMessage(Component.text("Неизвестный тип CC: " + args[2], NamedTextColor.RED));
            return;
        }
        Player caster = sender instanceof Player p ? p : null;
        CCService.ApplyResult res = plugin.getCC().tryApply(caster, target, type);
        switch (res.result()) {
            case SUCCESS -> sender.sendMessage(Component.text(type.ruName() + " → " + target.getName()
                    + ": " + (res.appliedTicks() / 20.0) + "с (DR "
                    + (int) Math.round(res.drMultiplier() * 100.0) + "%)", NamedTextColor.GREEN));
            case FAIL_IMMUNE -> sender.sendMessage(Component.text(target.getName()
                    + " невосприимчив к: " + type.ruName(), NamedTextColor.RED));
            case FAIL_RESIST -> sender.sendMessage(Component.text(target.getName()
                    + " сопротивляется: " + type.ruName(), NamedTextColor.YELLOW));
            case FAIL_DR_IMMUNE -> sender.sendMessage(Component.text(target.getName()
                    + " нечувствителен (DR-иммунитет): " + type.ruName(), NamedTextColor.RED));
            default -> sender.sendMessage(Component.text("Отказ: " + res.result(), NamedTextColor.RED));
        }
    }

    private void resetCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /rc cc reset <player> <category>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        DRCategory cat = null;
        for (DRCategory c : DRCategory.values()) {
            if (c.id().equalsIgnoreCase(args[2])) {
                cat = c;
                break;
            }
        }
        if (cat == null) {
            sender.sendMessage(Component.text("Неизвестная категория: " + args[2], NamedTextColor.RED));
            return;
        }
        plugin.getCC().resetDr(target.getUniqueId(), cat);
        sender.sendMessage(Component.text("DR-стек категории " + cat.ruName()
                + " сброшен у " + target.getName(), NamedTextColor.GREEN));
    }

    private void listCmd(CommandSender sender) {
        sender.sendMessage(Component.text("=== CCType → категория DR ===", NamedTextColor.GOLD));
        for (CCType t : CCType.values()) {
            sender.sendMessage(Component.text(" " + t.id() + " (" + t.ruName() + ") → "
                    + t.category().id() + (t.instant() ? " [мгновенный]" : ""),
                    NamedTextColor.WHITE));
        }
    }
}
