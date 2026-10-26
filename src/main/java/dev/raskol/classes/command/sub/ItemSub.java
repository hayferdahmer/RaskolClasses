// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.craft.CraftingService;
import dev.raskol.classes.item.craft.SalvageService;
import dev.raskol.classes.item.gui.ItemBrowserMenu;
import dev.raskol.classes.item.gui.ItemHover;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.service.ItemStatAggregator;
import dev.raskol.classes.item.service.SetBonusService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.15.0: команды предметного слоя (/rc item ...).
 * 1.15.3: подкоманды локализованы через messages.item.*.
 * 1.15.4: +salvage и +craft [id].
 * 1.15.4-B-fix3: удалена dev-команда debug.
 * 1.15.5: stats расширен (hover по экипировке, причины unmet, прогресс сетов);
 *         +sets [игрок]; browser принимает поисковый запрос аргументами.
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
            sender.sendMessage(Component.text(
                    "Использование: /rc item <give|stats|sets|browser|reload|salvage|craft>",
                    NamedTextColor.RED));
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        return switch (sub) {
            case "give" -> executeGive(sender, args);
            case "stats" -> executeStats(sender, args);
            case "sets" -> executeSets(sender, args);
            case "browser" -> executeBrowser(sender, args);
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
            return SubUtil.filter(List.of("give", "stats", "sets", "browser",
                    "reload", "salvage", "craft"), fullArgs[1]);
        }
        if (fullArgs.length >= 3 && "give".equalsIgnoreCase(fullArgs[1])) {
            return completeGive(fullArgs);
        }
        if (fullArgs.length == 3 && "craft".equalsIgnoreCase(fullArgs[1])) {
            return SubUtil.filter(plugin.getItems().crafting().recipes().stream()
                    .map(CraftingService.Recipe::id).toList(), fullArgs[2]);
        }
        if (fullArgs.length == 3 && ("stats".equalsIgnoreCase(fullArgs[1])
                || "sets".equalsIgnoreCase(fullArgs[1]))) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[2]);
        }
        return List.of();
    }

    private List<String> completeGive(String[] fullArgs) {
        if (fullArgs.length == 3) {
            return SubUtil.filter(plugin.getItems().registry().all().stream()
                    .map(ItemDefinition::id).toList(), fullArgs[2]);
        }
        if (fullArgs.length == 4) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[3]);
        }
        if (fullArgs.length == 5) {
            return SubUtil.filter(List.of("1", "5", "10", "64"), fullArgs[4]);
        }
        return List.of();
    }

    /* ------------------------------ give ------------------------------ */

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
        ItemDefinition def = plugin.getItems().registry().get(id);
        if (def == null) {
            sender.sendMessage(Component.text("Неизвестный предмет: " + id, NamedTextColor.RED));
            return true;
        }
        for (int i = 0; i < amount; i++) {
            ItemStack created = plugin.getItems().create(id, def.itemLevel());
            target.getInventory().addItem(created).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        }
        sender.sendMessage(Component.text("Выдано " + amount + "× " + def.displayName()
                + " игроку " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    /* ------------------------------ stats (1.15.5: расширенный) ------------------------------ */

    private boolean executeStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        Player target = args.length >= 2 ? plugin.getServer().getPlayer(args[1]) : viewer;
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return true;
        }
        var snapshot = plugin.getItems().snapshot(target.getUniqueId());
        sender.sendMessage(Component.text("=== Снаряжение " + target.getName() + " ===",
                NamedTextColor.GOLD));

        // 1.15.5: построчный вывод экипировки с hover-превью
        for (ItemSlot slot : ItemSlot.values()) {
            ItemStack stack = equippedStack(target, slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemDefinition def = plugin.getItems().definitionOf(stack);
            Component label = Component.text("  " + slotLabel(slot) + ": "
                            + (def != null ? def.displayName() : stack.getType().name()),
                    def != null ? NamedTextColor.WHITE : NamedTextColor.GRAY);
            if (def != null) {
                label = ItemHover.withPreview(label, stack);
            }
            sender.sendMessage(label);
        }

        sender.sendMessage(Component.text("Статов в снапшоте: " + snapshot.stats().size(),
                NamedTextColor.AQUA));
        snapshot.stats().forEach((stat, value) -> sender.sendMessage(Component.text(
                "  " + stat.id() + ": " + String.format(java.util.Locale.ROOT, "%.2f", value),
                NamedTextColor.GRAY)));

        // 1.15.5: причины unmet (класс/спек/уровень)
        if (!snapshot.unmetRequirements().isEmpty()) {
            sender.sendMessage(Component.text("Неактивные предметы (требования не выполнены):",
                    NamedTextColor.YELLOW));
            for (String unmetId : snapshot.unmetRequirements()) {
                ItemDefinition def = plugin.getItems().registry().get(unmetId);
                sender.sendMessage(Component.text("  ⛔ "
                        + (def != null ? def.displayName() : unmetId) + ": "
                        + unmetReason(def, target), NamedTextColor.RED));
            }
        }

        // активные сеты
        var active = plugin.getItems().setBonuses().active(target.getUniqueId());
        if (!active.isEmpty()) {
            sender.sendMessage(Component.text("Активные сеты:", NamedTextColor.DARK_PURPLE));
            active.forEach(s -> sender.sendMessage(Component.text("  " + s.displayName()
                    + " (" + s.pieces() + " шт, пороги " + s.activeThresholds() + ")",
                    NamedTextColor.LIGHT_PURPLE)));
        }

        // 1.15.5: прогресс к следующему порогу по каждому надетому сету
        Map<String, Integer> counts = countSetPieces(target);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            var set = plugin.getItems().registry().sets().get(e.getKey());
            if (set == null) {
                continue;
            }
            Integer next = set.bonuses().keySet().stream()
                    .filter(t -> t > e.getValue()).sorted().findFirst().orElse(null);
            if (next != null) {
                sender.sendMessage(Component.text("  ↦ " + set.displayName() + ": до порога "
                        + next + " ещё " + (next - e.getValue()) + " предм.",
                        NamedTextColor.GRAY));
            }
        }
        return true;
    }

    /* ------------------------------ sets (1.15.5: новая) ------------------------------ */

    private boolean executeSets(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        Player target = args.length >= 2 ? plugin.getServer().getPlayer(args[1]) : viewer;
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return true;
        }
        var active = plugin.getItems().setBonuses().active(target.getUniqueId());
        if (active.isEmpty()) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("item.sets-none", "Активных сетов нет."), NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("=== Активные сеты " + target.getName() + " ===",
                    NamedTextColor.GOLD));
            for (SetBonusService.ActiveSet s : active) {
                sender.sendMessage(Component.text(s.displayName() + ": " + s.pieces()
                        + " предм. · активные пороги " + s.activeThresholds(),
                        NamedTextColor.LIGHT_PURPLE));
            }
        }
        Map<String, Integer> counts = countSetPieces(target);
        sender.sendMessage(Component.text("=== Потенциал ===", NamedTextColor.GOLD));
        boolean any = false;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            var set = plugin.getItems().registry().sets().get(e.getKey());
            if (set == null) {
                continue;
            }
            any = true;
            List<Integer> reached = set.bonuses().keySet().stream()
                    .filter(t -> t <= e.getValue()).sorted().toList();
            Integer next = set.bonuses().keySet().stream()
                    .filter(t -> t > e.getValue()).sorted().findFirst().orElse(null);
            StringBuilder line = new StringBuilder("  " + set.displayName() + ": "
                    + e.getValue() + " предм. · пороги " + reached);
            if (next != null) {
                line.append(" · след. ").append(next).append(" (ещё ").append(next - e.getValue()).append(")");
            } else {
                line.append(" · комплект полный");
            }
            sender.sendMessage(Component.text(line.toString(), NamedTextColor.AQUA));
        }
        if (!any) {
            sender.sendMessage(Component.text("  Предметов с сетами не надето.", NamedTextColor.GRAY));
        }
        return true;
    }

    /* ------------------------------ browser / reload / salvage / craft ------------------------------ */

    private boolean executeBrowser(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна игроку.", NamedTextColor.RED));
            return true;
        }
        String query = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
        plugin.getItems().browser().open(player, 0, query, "ALL", "ALL");
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

    /* ------------------------------ хелперы 1.15.5 ------------------------------ */

    private static ItemStack equippedStack(Player player, ItemSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> player.getInventory().getItemInMainHand();
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
        };
    }

    private static String slotLabel(ItemSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> "Оружие";
            case OFF_HAND -> "Вторая рука";
            case HEAD -> "Шлем";
            case CHEST -> "Кираса";
            case LEGS -> "Поножи";
            case FEET -> "Сапоги";
        };
    }

    /** Человекочитаемая причина, почему предмет не даёт статы (порядок как в EquipGuard). */
    private String unmetReason(ItemDefinition def, Player player) {
        if (def == null) {
            return "предмет неизвестен реестру";
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (def.playerClass() != null && def.playerClass() != pc) {
            return "примет только " + def.playerClass().getDisplayName();
        }
        String spec = plugin.getSpec2Service().mainSpec(player.getUniqueId());
        if (def.spec() != null && !def.spec().equalsIgnoreCase(spec)) {
            return "требуется спек " + def.spec();
        }
        int level = plugin.getCharacterLevels().characterLevel(player.getUniqueId());
        if (level < def.levelRequirement()) {
            return "требуется уровень " + def.levelRequirement() + " (у вас " + level + ")";
        }
        return "требования не распознаны";
    }

    /** Число надетых предметов каждого сета (только eligible — как в reconcile). */
    private Map<String, Integer> countSetPieces(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return counts;
        }
        int level = plugin.getCharacterLevels().characterLevel(player.getUniqueId());
        String spec = plugin.getSpec2Service().mainSpec(player.getUniqueId());
        for (ItemSlot slot : ItemSlot.values()) {
            ItemStack stack = equippedStack(player, slot);
            ItemDefinition def = plugin.getItems().definitionOf(stack);
            if (def == null || def.setId() == null || def.slot() != slot) {
                continue;
            }
            if (!ItemStatAggregator.eligible(def, pc, spec, level)) {
                continue;
            }
            counts.merge(def.setId(), 1, Integer::sum);
        }
        return counts;
    }
}
