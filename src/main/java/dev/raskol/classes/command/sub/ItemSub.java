// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.craft.CraftingGui;
import dev.raskol.classes.item.craft.CraftingService;
import dev.raskol.classes.item.craft.SalvageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.15.0: команды предметного слоя (/rc item ...).
 * 1.15.3: подкоманды локализованы через messages.item.*.
 * 1.15.4: +salvage (разбор предмета в руке на фрагменты) и +craft [id]
 * (крафт по рецепту; без id — открыть GUI крафта).
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

    @Override
    public String permission() {
        return "raskolclasses.item";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Использование: /rc item <give|stats|browser|debug|reload|salvage|craft>",
                    NamedTextColor.RED));
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        return switch (sub) {
            case "give" -> executeGive(sender, args);
            case "stats" -> executeStats(sender, args);
            case "browser" -> executeBrowser(sender, args);
            case "debug" -> executeDebug(sender, args);
            case "reload" -> executeReload(sender);
            case "salvage" -> executeSalvage(sender);
            case "craft" -> executeCraft(sender, args);
            default -> {
                sender.sendMessage(Component.text("Неизвестная подкоманда: " + args[0], NamedTextColor.RED));
                yield true;
            }
        };
    }

    @Override
    public List<String> complete(CommandSender sender, String[] fullArgs) {
        if (fullArgs.length == 2) {
            return SubUtil.filter(List.of("give", "stats", "browser", "debug",
                    "reload", "salvage", "craft"), fullArgs[1]);
        }
        if (fullArgs.length >= 3 && "give".equalsIgnoreCase(fullArgs[1])) {
            return completeGive(fullArgs);
        }
        if (fullArgs.length == 3 && "craft".equalsIgnoreCase(fullArgs[1])) {
            return SubUtil.filter(plugin.getItems().crafting().recipes().stream()
                    .map(CraftingService.Recipe::id).toList(), fullArgs[2]);
        }
        if (fullArgs.length == 3 && "stats".equalsIgnoreCase(fullArgs[1])) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[2]);
        }
        return List.of();
    }

    private List<String> completeGive(String[] fullArgs) {
        if (fullArgs.length == 3) {
            return SubUtil.filter(plugin.getItems().registry().all().stream()
                    .map(dev.raskol.classes.item.model.ItemDefinition::id).toList(), fullArgs[2]);
        }
        if (fullArgs.length == 4) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[3]);
        }
        if (fullArgs.length == 5) {
            return SubUtil.filter(List.of("1", "5", "10", "64"), fullArgs[4]);
        }
        return List.of();
    }

    private boolean executeGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.item.give")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /rc item give <id> <игрок> [количество]",
                    NamedTextColor.RED));
            return true;
        }
        String id = args[1];
        Player target = plugin.getServer().getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден: " + args[2], NamedTextColor.RED));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
            }
        }
        if (plugin.getItems().registry().get(id) == null) {
            sender.sendMessage(Component.text("Неизвестный предмет: " + id, NamedTextColor.RED));
            return true;
        }
        for (int i = 0; i < amount; i++) {
            target.getInventory().addItem(plugin.getItems().create(id,
                    plugin.getItems().registry().get(id).itemLevel())).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        }
        sender.sendMessage(Component.text("Выдано " + amount + "× " + id + " игроку "
                + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean executeStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        Player target = args.length >= 2 ? plugin.getServer().getPlayer(args[1]) : player;
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return true;
        }
        var snapshot = plugin.getItems().snapshot(target.getUniqueId());
        sender.sendMessage(Component.text("=== Снаряжение " + target.getName() + " ===",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Статов в снапшоте: " + snapshot.stats().size(),
                NamedTextColor.AQUA));
        snapshot.stats().forEach((stat, value) -> sender.sendMessage(Component.text(
                "  " + stat.id() + ": " + String.format(java.util.Locale.ROOT, "%.2f", value),
                NamedTextColor.GRAY)));
        if (!snapshot.unmetRequirements().isEmpty()) {
            sender.sendMessage(Component.text("Неактивные (unmet): "
                    + String.join(", ", snapshot.unmetRequirements()), NamedTextColor.YELLOW));
        }
        var active = plugin.getItems().setBonuses().active(target.getUniqueId());
        if (!active.isEmpty()) {
            sender.sendMessage(Component.text("Активные сеты:", NamedTextColor.DARK_PURPLE));
            active.forEach(s -> sender.sendMessage(Component.text("  " + s.displayName()
                    + " (" + s.pieces() + " шт, пороги " + s.activeThresholds() + ")",
                    NamedTextColor.LIGHT_PURPLE)));
        }
        return true;
    }

    private boolean executeBrowser(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        plugin.getItems().browser().open(player, 0);
        return true;
    }

    private boolean executeDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.item.debug")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /rc item debug <uid>", NamedTextColor.RED));
            return true;
        }
        String uid = args[1];
        var owner = plugin.getItems().pdc().uidOwner(uid);
        sender.sendMessage(Component.text("UID " + uid + ": владелец "
                + (owner == null ? "—" : owner.toString()), NamedTextColor.AQUA));
        return true;
    }

    private boolean executeReload(CommandSender sender) {
        if (!sender.hasPermission("raskolclasses.item.reload")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return true;
        }
        boolean ok = plugin.getItems().reload();
        plugin.getItems().crafting().reload();
        sender.sendMessage(Component.text(ok
                ? "Реестр предметов и рецепты крафта перезагружены."
                : "Ошибка загрузки — смотри логи.", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private boolean executeSalvage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("raskolclasses.item.salvage")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return true;
        }
        SalvageService.SalvageResult result = plugin.getItems().salvage().salvage(player);
        if (result != SalvageService.SalvageResult.OK) {
            plugin.getItems().salvage().notifyFailure(player, result);
        }
        return true;
    }

    private boolean executeCraft(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("raskolclasses.item.craft")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            plugin.getItems().craftingGui().open(player, 0);
            return true;
        }
        String recipeId = args[1];
        CraftingService.Recipe recipe = plugin.getItems().crafting().recipe(recipeId);
        CraftingService.CraftResult result = plugin.getItems().crafting().craft(player, recipeId);
        if (result != CraftingService.CraftResult.OK) {
            plugin.getItems().crafting().notifyFailure(player, result, recipe);
        }
        return true;
    }
}
