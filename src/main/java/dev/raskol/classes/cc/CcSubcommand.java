// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.13.0 (Б4): подкоманды /rc cc для администрирования CC/DR.
 *  - status <player> — активные CC + DR-стеки + время до сброса окна
 *  - clear <player> — снять все CC + сбросить DR
 *  - test <player> <type> — наложить тестовый CC (например STUN/SLOW/ROOT)
 *  - reset <player> <category> — сбросить DR-стек конкретной категории
 *  - list — список всех CCType и их категорий
 * Права: raskolclasses.admin.cc (все подкоманды требуют эту пермиссию).
 */
public final class CcSubcommand {

    private final RaskolClasses plugin;

    public CcSubcommand(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.admin.cc")) {
            sender.sendMessage(Component.text("§cНедостаточно прав", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> statusCmd(sender, args);
            case "clear" -> clearCmd(sender, args);
            case "test" -> testCmd(sender, args);
            case "reset" -> resetCmd(sender, args);
            case "list" -> listCmd(sender);
            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6/rc cc <subcommand>", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  §fstatus <player> §7— активные CC + DR-стеки", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  §fclear <player> §7— снять все CC + сбросить DR", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  §ftest <player> <type> §7— наложить тестовый CC (STUN/SLOW/ROOT/...)", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  §freset <player> <category> §7— сбросить DR-стек категории", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  §flist §7— список всех CCType и категорий", NamedTextColor.WHITE));
    }

    private void statusCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cИспользование: /rc cc status <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("§cИгрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        CCService cc = plugin.getCC();
        List<CCInstance> active = cc.activeOf(target.getUniqueId());
        sender.sendMessage(Component.text("§6=== CC Status: " + target.getName() + " ===", NamedTextColor.GOLD));
        if (active.isEmpty()) {
            sender.sendMessage(Component.text("  §7Активных CC нет", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("  §fАктивные CC:", NamedTextColor.WHITE));
            long now = System.currentTimeMillis();
            for (CCInstance inst : active) {
                long remaining = inst.remainingTicks(now) / 20L;
                sender.sendMessage(Component.text("    §7- " + inst.type().ruName() + " (" + inst.type().id()
                        + ") — " + remaining + "с (DR " + (int) (inst.drMultiplier() * 100) + "%)",
                        NamedTextColor.GRAY));
            }
        }
        sender.sendMessage(Component.text("  §fDR-стеки:", NamedTextColor.WHITE));
        for (DRCategory cat : DRCategory.values()) {
            DRState state = cc.drState(target.getUniqueId(), cat);
            long windowMs = plugin.getConfig().getDouble("cc.window-seconds", 15.0) * 1000.0;
            long elapsed = now - state.lastAppliedAt();
            long remaining = Math.max(0, windowMs - elapsed);
            sender.sendMessage(Component.text("    §7- " + cat.ruName() + " (" + cat.id()
                    + "): стек " + state.stackCount() + ", сброс через " + (remaining / 1000L) + "с",
                    NamedTextColor.GRAY));
        }
    }

    private void clearCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cИспользование: /rc cc clear <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("§cИгрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        plugin.getCC().removeAll(target.getUniqueId());
        plugin.getCC().resetAllDR(target.getUniqueId());
        sender.sendMessage(Component.text("§aВсе CC сняты и DR сброшены для " + target.getName(), NamedTextColor.GREEN));
        if (target != sender) {
            target.sendMessage(Component.text("§aВсе эффекты контроля сняты администратором", NamedTextColor.GREEN));
        }
    }

    private void testCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("§cИспользование: /rc cc test <player> <type>", NamedTextColor.RED));
            sender.sendMessage(Component.text("§7Доступные типы: STUN, SLOW, ROOT, SILENCE, BLIND, FEAR, DISARM, CHARM, KNOCKBACK", NamedTextColor.GRAY));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("§cИгрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        CCType type;
        try {
            type = CCType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("§cНеизвестный тип CC: " + args[2], NamedTextColor.RED));
            sender.sendMessage(Component.text("§7Доступные: STUN, SLOW, ROOT, SILENCE, BLIND, FEAR, DISARM, CHARM, KNOCKBACK", NamedTextColor.GRAY));
            return;
        }
        Player caster = (sender instanceof Player) ? (Player) sender : null;
        int duration = plugin.getCC().durationTicks(type);
        CCService.ApplyResult result = plugin.getCC().tryApply(caster, target, type, duration);
        switch (result.result()) {
            case SUCCESS -> {
                sender.sendMessage(Component.text("§a" + type.ruName() + " наложен на " + target.getName()
                        + " (" + result.appliedTicks() + " тиков, DR " + (int) (result.drMultiplier() * 100) + "%)",
                        NamedTextColor.GREEN));
            }
            case FAIL_IMMUNE -> {
                sender.sendMessage(Component.text("§c" + target.getName() + " иммунен к " + type.ruName(), NamedTextColor.RED));
            }
            case FAIL_RESIST -> {
                sender.sendMessage(Component.text("§e" + target.getName() + " сопротивляется " + type.ruName(), NamedTextColor.YELLOW));
            }
            case FAIL_DR_IMMUNE -> {
                sender.sendMessage(Component.text("§c" + target.getName() + " нечувствителен (DR-иммунитет)", NamedTextColor.RED));
            }
            default -> {
                sender.sendMessage(Component.text("§cНе удалось наложить " + type.ruName() + ": " + result.result(), NamedTextColor.RED));
            }
        }
    }

    private void resetCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("§cИспользование: /rc cc reset <player> <category>", NamedTextColor.RED));
            sender.sendMessage(Component.text("§7Категории: STUN, FEAR, ROOT, SILENCE, SLOW, BLIND", NamedTextColor.GRAY));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("§cИгрок не найден: " + args[1], NamedTextColor.RED));
            return;
        }
        DRCategory cat;
        try {
            cat = DRCategory.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("§cНеизвестная категория: " + args[2], NamedTextColor.RED));
            sender.sendMessage(Component.text("§7Доступные: STUN, FEAR, ROOT, SILENCE, SLOW, BLIND", NamedTextColor.GRAY));
            return;
        }
        plugin.getCC().resetDr(target.getUniqueId(), cat);
        sender.sendMessage(Component.text("§aDR-стек категории " + cat.ruName() + " сброшен для " + target.getName(), NamedTextColor.GREEN));
    }

    private void listCmd(CommandSender sender) {
        sender.sendMessage(Component.text("§6=== Список CCType ===", NamedTextColor.GOLD));
        for (CCType type : CCType.values()) {
            sender.sendMessage(Component.text("  §f" + type.id() + " §7(" + type.ruName()
                    + ") — категория: " + type.category().id() + " (" + type.category().ruName() + ")",
                    NamedTextColor.WHITE));
        }
    }
}
