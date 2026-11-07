// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.command.sub;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemStat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Реестр предметов и операции игрока: /rc item list|info|give|roll|stats|browse|salvage|craft.
 * 1.15.5 (аудит 2.7-2/2.7-5): права по дереву plugin.yml:
 *   игровые  — raskolclasses.item.{stats,salvage,craft} (default true);
 *   админские — raskolclasses.item.{give,reload,debug,browser} (default op, дети item.admin).
 * stats по чужому нику требует item.debug.
 * 1.15.5-fix: браузер живёт в ItemSystem.browser() (gui/book/ItemBrowserMenu удалён в #1825,
 * геттер RaskolClasses.getItemBrowser() убран в #1826) — вызов через plugin.getItems().browser().
 */
public final class ItemSub implements CommandSub {
    private final RaskolClasses plugin;

    public ItemSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "item";
    }

    /** Базовая нода: доступна всем (внутренние действия проверяют свои узлы). */
    @Override
    public String permission() {
        return "raskolclasses.item";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(
                    Component.text("/rc item list|info|give|roll|stats|browse|salvage|craft", NamedTextColor.YELLOW));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);

        if ("stats".equals(action)) {
            if (!sender.hasPermission("raskolclasses.item.stats")) return deny(sender);
            Player p;
            if (args.length > 1) {
                if (!sender.hasPermission("raskolclasses.item.debug")) return deny(sender);
                p = Bukkit.getPlayer(args[1]);
            } else {
                p = sender instanceof Player pl ? pl : null;
            }
            if (p == null) {
                sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                return true;
            }
            printStats(sender, p);
            return true;
        }

        if ("salvage".equals(action)) {
            if (!sender.hasPermission("raskolclasses.item.salvage")) return deny(sender);
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
                return true;
            }
            var result = plugin.getItems().salvage().salvage(p);
            if (result != dev.raskol.classes.item.craft.SalvageService.SalvageResult.OK) {
                plugin.getItems().salvage().notifyFailure(p, result);
            }
            return true;
        }

        if ("craft".equals(action)) {
            if (!sender.hasPermission("raskolclasses.item.craft")) return deny(sender);
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                plugin.getItems().craftingGui().open(p, 0);
                return true;
            }
            var recipe = plugin.getItems().crafting().recipe(args[1]);
            var result = plugin.getItems().crafting().craft(p, args[1]);
            if (result != dev.raskol.classes.item.craft.CraftingService.CraftResult.OK) {
                plugin.getItems().crafting().notifyFailure(p, result, recipe);
            }
            return true;
        }

        // Остальное — админ-ветка
        if (!sender.hasPermission("raskolclasses.item.admin") && !hasAnyAdminChild(sender)) return deny(sender);

        switch (action) {
            case "list" -> {
                int page = args.length > 1 ? positiveInt(args[1], 1) : 1;
                List<ItemDefinition> all = plugin.getItems().registry().all().stream()
                        .sorted(java.util.Comparator.comparing(ItemDefinition::id))
                        .toList();
                int from = (page - 1) * 45;
                sender.sendMessage(Component.text(
                        "Предметы " + Math.min(from + 1, all.size()) + "–" + Math.min(from + 45, all.size()) + " / "
                                + all.size(),
                        NamedTextColor.GOLD));
                for (int i = from; i < Math.min(from + 45, all.size()); i++)
                    sender.sendMessage(Component.text(all.get(i).id(), NamedTextColor.GRAY));
            }
            case "info" -> {
                if (args.length < 2) {
                    usage(sender, "info <id>");
                    return true;
                }
                ItemDefinition d = plugin.getItems().registry().get(args[1]);
                if (d == null) {
                    sender.sendMessage(Component.text("Предмет не найден.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text(
                        d.id() + " · " + d.displayName() + " · " + d.rarity() + " · ур. " + d.itemLevel(),
                        NamedTextColor.GOLD));
                sender.sendMessage(Component.text(
                        "Слот: " + d.slot() + " · класс: " + (d.playerClass() == null ? "ALL" : d.playerClass())
                                + " · спек: " + (d.spec() == null ? "ALL" : d.spec()) + " · требование: "
                                + d.levelRequirement(),
                        NamedTextColor.GRAY));
                sender.sendMessage(Component.text(
                        "Шаблон: " + (d.template() == null ? "—" : d.template())
                                + " · сет: " + (d.setId() == null ? "—" : d.setId())
                                + " · аффиксы: " + d.affixCount() + "/"
                                + d.affixPool().size(),
                        NamedTextColor.GRAY));
                d.baseStats()
                        .forEach((s, v) ->
                                sender.sendMessage(Component.text("  " + s.id() + ": " + v, NamedTextColor.GRAY)));
                for (var affix : d.affixPool())
                    sender.sendMessage(Component.text(
                            "  affix " + affix.stat().id() + ": " + affix.min() + ".." + affix.max() + " (вес "
                                    + affix.weight() + ")",
                            NamedTextColor.AQUA));
            }
            case "give", "roll" -> {
                if (!sender.hasPermission("raskolclasses.item.give")) return deny(sender);
                if (args.length < 3) {
                    usage(sender, action + " <игрок> <id> [кол-во|seed]");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                ItemDefinition d = plugin.getItems().registry().get(args[2]);
                if (target == null || d == null) {
                    sender.sendMessage(Component.text(
                            target == null ? "Игрок не найден." : "Предмет не найден.", NamedTextColor.RED));
                    return true;
                }
                int amount = 1;
                long seed = ThreadLocalRandom.current().nextLong();
                if ("give".equals(action) && args.length > 3) amount = positiveInt(args[3], 1);
                if ("roll".equals(action) && args.length > 3)
                    try {
                        seed = Long.parseLong(args[3]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Component.text("Seed должен быть целым числом.", NamedTextColor.RED));
                        return true;
                    }
                if (amount > 64) {
                    sender.sendMessage(Component.text(
                            "За одну выдачу можно передать не больше 64 предметов.", NamedTextColor.RED));
                    return true;
                }
                for (int i = 0; i < amount; i++) {
                    ItemStack item = plugin.getItemsApi().create(d.id(), d.itemLevel(), seed + i);
                    var leftovers = target.getInventory().addItem(item);
                    leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                }
                plugin.getLogger()
                        .info("Item issued by=" + sender.getName() + " to=" + target.getName() + " id=" + d.id()
                                + " amount=" + amount + " seed=" + seed);
                sender.sendMessage(Component.text("Выдано: " + d.displayName() + " ×" + amount, NamedTextColor.GREEN));
            }
            case "browse" -> {
                if (!sender.hasPermission("raskolclasses.item.browser")) return deny(sender);
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
                    return true;
                }
                // 1.15.5-fix: браузер переехал в ItemSystem (gui/book-класс удалён)
                plugin.getItems().browser().open(p, 0, filter(args, 1), filter(args, 2), filter(args, 3));
            }
            case "reload" -> {
                if (!sender.hasPermission("raskolclasses.item.reload")) return deny(sender);
                boolean ok = plugin.getItems().reload();
                sender.sendMessage(Component.text(
                        ok ? "Реестр предметов перезагружен." : "Ошибка загрузки — смотри логи.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            default -> sender.sendMessage(Component.text(
                    "Неизвестная операция. /rc item list|info|give|roll|stats|browse|salvage|craft",
                    NamedTextColor.RED));
        }
        return true;
    }

    /** Любой дочерний админ-узел открывает ветку (для точечных LP-грантов). */
    private static boolean hasAnyAdminChild(CommandSender sender) {
        return sender.hasPermission("raskolclasses.item.give")
                || sender.hasPermission("raskolclasses.item.reload")
                || sender.hasPermission("raskolclasses.item.debug")
                || sender.hasPermission("raskolclasses.item.browser");
    }

    private void printStats(CommandSender sender, Player player) {
        var snap = plugin.getItems().snapshot(player.getUniqueId());
        sender.sendMessage(Component.text("Снаряжение " + player.getName() + ":", NamedTextColor.GOLD));
        for (ItemStat s : ItemStat.values()) {
            double v = snap.value(s);
            if (v != 0)
                sender.sendMessage(
                        Component.text(s.id() + ": " + String.format(Locale.ROOT, "%.1f", v), NamedTextColor.GRAY));
        }
        if (!snap.unmetRequirements().isEmpty())
            sender.sendMessage(Component.text("Требования: " + snap.unmetRequirements(), NamedTextColor.RED));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return SubUtil.filter(
                    List.of("list", "info", "give", "roll", "stats", "browse", "salvage", "craft"), args[1]);
        }
        if (args.length == 3 && "browse".equalsIgnoreCase(args[1])) {
            List<String> choices = new java.util.ArrayList<>(List.of("ALL"));
            for (var pc : dev.raskol.classes.classsystem.PlayerClass.values()) choices.add(pc.name());
            return SubUtil.filter(choices, args[2]);
        }
        if (args.length == 4 && "browse".equalsIgnoreCase(args[1])) {
            List<String> choices = new java.util.ArrayList<>(List.of("ALL"));
            for (var slot : dev.raskol.classes.item.model.ItemSlot.values()) choices.add(slot.name());
            return SubUtil.filter(choices, args[3]);
        }
        if (args.length == 5 && "browse".equalsIgnoreCase(args[1])) {
            List<String> choices = new java.util.ArrayList<>(List.of("ALL"));
            for (var rarity : ItemRarity.values()) choices.add(rarity.name());
            return SubUtil.filter(choices, args[4]);
        }
        if (args.length == 3 && "info".equalsIgnoreCase(args[1]))
            return SubUtil.filter(
                    plugin.getItems().registry().all().stream()
                            .map(ItemDefinition::id)
                            .toList(),
                    args[2]);
        if (args.length == 3 && List.of("give", "roll").contains(args[1].toLowerCase(Locale.ROOT)))
            return SubUtil.filter(SubUtil.onlinePlayerNames(), args[2]);
        if (args.length == 4 && List.of("give", "roll").contains(args[1].toLowerCase(Locale.ROOT)))
            return SubUtil.filter(
                    plugin.getItems().registry().all().stream()
                            .map(ItemDefinition::id)
                            .toList(),
                    args[3]);
        if (args.length == 3 && "stats".equalsIgnoreCase(args[1]))
            return SubUtil.filter(SubUtil.onlinePlayerNames(), args[2]);
        if (args.length == 3 && "craft".equalsIgnoreCase(args[1]))
            return SubUtil.filter(
                    plugin.getItems().crafting().recipes().stream()
                            .map(dev.raskol.classes.item.craft.CraftingService.Recipe::id)
                            .toList(),
                    args[2]);
        return List.of();
    }

    private static String filter(String[] args, int index) {
        return args.length > index ? args[index] : "ALL";
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
        return true;
    }

    private void usage(CommandSender sender, String value) {
        sender.sendMessage(Component.text("/rc item " + value, NamedTextColor.YELLOW));
    }

    private static int positiveInt(String value, int fallback) {
        try {
            int n = Integer.parseInt(value);
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
