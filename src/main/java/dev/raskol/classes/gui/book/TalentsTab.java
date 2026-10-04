// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import dev.raskol.classes.spec.service.Spec2Service;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.14.0 (Б3): вкладка «Деревья путей» на Spec2.
 * 1.14.0-fix (ряды): пагинация по рядам. Ромб BookSlots.TALENT_NODE_SLOTS (9 слотов)
 *   не вмещает 6-рядное дерево, поэтому показываем ОДИН ряд за раз горизонтальной
 *   линией ROW_LINE_SLOTS (до 5 узлов), листаем ◀ ▶ (SLOT_ROW_PREV/NEXT). Инфо (22)
 *   — сводка ряда + прогресс 6 рядов + общий бюджет очков. Сброс (44) без изменений.
 *   ЛКМ по узлу = +1 ранг; узел открыт при ряд-гейте + ранговых пререквизитах.
 */
public final class TalentsTab implements BookTabView {

    private static final Map<UUID, Long> RESET_ARM = new ConcurrentHashMap<>();
    /** 1.14.0-fix (ряды): просматриваемый ряд per-player (1..6). */
    private static final Map<UUID, Integer> VIEW_ROW = new ConcurrentHashMap<>();
    private static final int ROW_COUNT = 6;

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.TALENTS;
    }

    /** Узлы одного ряда, отсортированные по колонке (детерминированно для render/click). */
    private static List<Spec2Node> rowNodes(Spec2Tree tree, int row) {
        List<Spec2Node> list = new ArrayList<>();
        for (Spec2Node n : tree.nodes()) {
            if (n.row() == row) {
                list.add(n);
            }
        }
        list.sort(Comparator.comparingInt(Spec2Node::col));
        return list;
    }

    private static int clampRow(int row) {
        return Math.max(1, Math.min(ROW_COUNT, row));
    }

    @Override
    public void render(RenderCtx ctx) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        UUID uuid = player.getUniqueId();
        Spec2Service svc = plugin.getSpec2Service();
        String main = svc.mainSpec(uuid);
        if (main == null) {
            ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, BookItems.infoItem(Material.BARRIER,
                    "Деревья путей недоступны",
                    List.of("Сначала выбери основную спеку",
                            "во вкладке «Специализации» (уровень 15+)")));
            return;
        }
        Spec2Tree tree = Spec2Registry.treeOf(main);
        if (tree == null) {
            ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, BookItems.infoItem(Material.BARRIER,
                    "Дерево пока не добавлено", List.of("Контент появится в батчах 1.14.4–1.14.7")));
            return;
        }

        int available = svc.availablePoints(uuid);
        int earned = svc.earnedPoints(uuid);
        int spent = svc.spentGlobal(uuid);
        Map<String, Integer> ranks = svc.storage().getRanks(uuid, main);
        int inTree = tree.spentInTree(ranks);
        int row = clampRow(VIEW_ROW.getOrDefault(uuid, 1));
        VIEW_ROW.put(uuid, row);

        // --- инфо текущего ряда + прогресс 6 рядов + бюджет ---
        int gate = Spec2Points.ROW_GATES[row - 1];
        boolean rowOpen = inTree >= gate;
        List<Spec2Node> nodes = rowNodes(tree, row);
        int ownedInRow = 0;
        int maxInRow = 0;
        for (Spec2Node n : nodes) {
            maxInRow += n.maxRank();
            ownedInRow += Math.min(n.maxRank(), ranks.getOrDefault(n.id(), 0));
        }

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        info.editMeta(meta -> {
            meta.displayName(Component.text("Дерево: " + main, NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Ряд " + row + " из " + ROW_COUNT
                            + (row == ROW_COUNT ? " (ульт)" : ""),
                    row == ROW_COUNT ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.AQUA)
                    .append(Component.text(rowOpen ? "  ✔ открыт" : "  🔒 закрыт",
                            rowOpen ? NamedTextColor.GREEN : NamedTextColor.RED)));
            lore.add(Component.text("Гейт ряда: " + gate + " очков в дереве (сейчас "
                    + inTree + (rowOpen ? "" : ", нужно ещё " + (gate - inTree)) + ")",
                    NamedTextColor.GRAY));
            lore.add(Component.text("Узлов в ряду: " + nodes.size()
                    + " · прокачано " + ownedInRow + "/" + maxInRow, NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Очков доступно: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(available), NamedTextColor.WHITE)));
            lore.add(Component.text("Потрачено: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(spent), NamedTextColor.WHITE))
                    .append(Component.text(" · заработано: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(earned), NamedTextColor.WHITE)));
            lore.add(Component.text("В этом дереве: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(inTree), NamedTextColor.AQUA)));
            lore.add(Component.empty());
            lore.add(Component.text("Ряды: ", NamedTextColor.YELLOW).append(rowProgress(tree, ranks)));
            lore.add(Component.empty());
            lore.add(Component.text("◀ ▶ — листать ряды · ЛКМ по узлу — +1 ранг",
                    NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, info);

        // --- навигация рядов ---
        ctx.inv().setItem(BookSlots.SLOT_ROW_PREV, navItem(Material.ARROW,
                row > 1 ? "◀ Ряд " + (row - 1) : "◀", row > 1));
        ctx.inv().setItem(BookSlots.SLOT_ROW_NEXT, navItem(Material.ARROW,
                row < ROW_COUNT ? "Ряд " + (row + 1) + " ▶" : "▶", row < ROW_COUNT));

        // --- линия узлов текущего ряда ---
        int limit = Math.min(nodes.size(), BookSlots.ROW_LINE_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            ctx.inv().setItem(BookSlots.ROW_LINE_SLOTS[i],
                    nodeItem(plugin, tree, nodes.get(i), ranks, available, inTree));
        }

        // --- сброс дерева ---
        ItemStack reset = new ItemStack(Material.END_CRYSTAL);
        reset.editMeta(meta -> {
            meta.displayName(Component.text("Сброс дерева", NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Цена: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getConfig().getInt("spec2.respec-base-cost", 250)
                            + " + " + plugin.getConfig().getInt("spec2.respec-per-level", 10)
                            + "×потрачено в дереве", NamedTextColor.RED))
                    .append(Component.text(" монет", NamedTextColor.GRAY)));
            lore.add(Component.text("Очки возвращаются в общий пул", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("ПКМ №1 — взвести · ПКМ №2 (30 с) — сбросить",
                    NamedTextColor.YELLOW));
            meta.lore(lore);
        });
        ctx.inv().setItem(BookSlots.SLOT_TALENT_RESET, reset);
    }

    /** Компактная строка прогресса рядов: 1✔ 2✔ 3🔒 4· 5· 6★ */
    private static Component rowProgress(Spec2Tree tree, Map<String, Integer> ranks) {
        int inTree = tree.spentInTree(ranks);
        Component acc = Component.empty();
        for (int r = 1; r <= ROW_COUNT; r++) {
            int g = Spec2Points.ROW_GATES[r - 1];
            boolean open = inTree >= g;
            String sym = (r == ROW_COUNT ? "★" : (open ? "✔" : "🔒"));
            NamedTextColor col = r == ROW_COUNT
                    ? (open ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY)
                    : (open ? NamedTextColor.GREEN : NamedTextColor.RED);
            if (r > 1) {
                acc = acc.append(Component.text(" ", NamedTextColor.DARK_GRAY));
            }
            acc = acc.append(Component.text(r + sym, col));
        }
        return acc;
    }

    private ItemStack navItem(Material mat, String label, boolean enabled) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(Component.text(label,
                    enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
            List<Component> lore = new ArrayList<>();
            lore.add(enabled
                    ? Component.text("ЛКМ — перейти", NamedTextColor.GRAY)
                    : Component.text("край дерева", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            if (enabled) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    @Override
    public void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        UUID uuid = player.getUniqueId();
        Spec2Service svc = plugin.getSpec2Service();
        String main = svc.mainSpec(uuid);

        // --- навигация рядов ---
        if (left && (slot == BookSlots.SLOT_ROW_PREV || slot == BookSlots.SLOT_ROW_NEXT)) {
            int cur = clampRow(VIEW_ROW.getOrDefault(uuid, 1));
            int next = slot == BookSlots.SLOT_ROW_PREV ? cur - 1 : cur + 1;
            VIEW_ROW.put(uuid, clampRow(next));
            ctx.refresh();
            return;
        }

        // --- сброс дерева (ПКМ ×2) ---
        if (slot == BookSlots.SLOT_TALENT_RESET && right) {
            Long armed = RESET_ARM.get(uuid);
            long now = System.currentTimeMillis();
            if (armed == null || now - armed > BookSlots.RESET_ARM_MILLIS) {
                RESET_ARM.put(uuid, now);
                player.sendMessage(Component.text(
                        "Сброс дерева взведён: ПКМ по кристаллу ещё раз в течение 30 с.",
                        NamedTextColor.YELLOW));
            } else {
                RESET_ARM.remove(uuid);
                if (main == null) {
                    player.sendMessage(Component.text(
                            "Основная спека не выбрана — сбрасывать нечего.",
                            NamedTextColor.GRAY));
                } else {
                    boolean free = player.hasPermission("raskolclasses.admin");
                    Spec2Service.ResetResult result = svc.resetTree(player, main, free);
                    player.sendMessage(Component.text(switch (result) {
                        case OK -> "Дерево сброшено: очки возвращены в общий пул.";
                        case NO_MAIN -> "Основная спека не выбрана.";
                        case TREE_NOT_FOUND -> "Дерево не найдено.";
                        case NO_RANKS -> "В дереве нет рангов.";
                        case POOR -> "Не хватает монет на сброс.";
                        case NO_ECONOMY -> "Экономика недоступна.";
                        case RATE_LIMITED -> "Слишком часто.";
                    }, result == Spec2Service.ResetResult.OK
                            ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
            }
            ctx.refresh();
            return;
        }

        if (!left || main == null) {
            return;
        }
        int lineIdx = BookSlots.indexOf(BookSlots.ROW_LINE_SLOTS, slot);
        if (lineIdx < 0) {
            return;
        }
        Spec2Tree tree = Spec2Registry.treeOf(main);
        if (tree == null) {
            return;
        }
        int row = clampRow(VIEW_ROW.getOrDefault(uuid, 1));
        List<Spec2Node> nodes = rowNodes(tree, row); // тот же порядок, что в render
        if (lineIdx >= nodes.size()) {
            return;
        }
        Spec2Node node = nodes.get(lineIdx);
        Spec2Service.PurchaseResult result = svc.purchase(player, main, node.id());
        int newRank = svc.storage().getRanks(uuid, main).getOrDefault(node.id(), 0);
        player.sendMessage(Component.text(switch (result) {
            case OK -> "«" + node.name() + "» — ранг " + newRank + "/" + node.maxRank() + ".";
            case DISABLED -> "Деревья путей отключены.";
            case RATE_LIMITED -> "Слишком часто.";
            case NO_MAIN -> "Основная спека не выбрана.";
            case WRONG_CLASS_TREE, TREE_NOT_FOUND -> "Дерево не найдено.";
            case NODE_NOT_FOUND -> "Узел не найден.";
            case ROW_GATE -> "Ряд закрыт: нужно больше очков в дереве.";
            case PREREQ -> "Нужны пререквизиты (требуемые ранги).";
            case MAX_RANK -> "Узел уже прокачан до максимума.";
            case NOT_ENOUGH_POINTS -> "Не хватает очков.";
        }, result == Spec2Service.PurchaseResult.OK
                ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        ctx.refresh();
    }

    private ItemStack nodeItem(RaskolClasses plugin, Spec2Tree tree, Spec2Node node,
                               Map<String, Integer> ranks, int available, int inTree) {
        int rank = ranks.getOrDefault(node.id(), 0);
        boolean isOwned = rank > 0;
        boolean maxed = rank >= node.maxRank();
        int gate = Spec2Points.ROW_GATES[node.row() - 1];
        boolean rowOk = inTree >= gate;
        boolean prereqOk = tree.prereqsMet(node, ranks);
        boolean affordable = available >= 1;

        ItemStack item = new ItemStack(node.isUltimate() ? Material.BEACON : Material.NETHER_STAR);
        item.editMeta(meta -> {
            NamedTextColor nameColor = maxed ? NamedTextColor.GREEN
                    : isOwned ? NamedTextColor.GOLD
                    : (!rowOk || !prereqOk) ? NamedTextColor.DARK_GRAY
                    : affordable ? NamedTextColor.WHITE : NamedTextColor.RED;
            String prefix = node.isUltimate() ? "★ " : "";
            meta.displayName(Component.text(prefix + node.name() + " " + rank + "/" + node.maxRank(),
                    nameColor));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Ряд " + node.row()
                            + (node.isUltimate() ? " · УЛЬТ" : "")
                            + " · гейт " + gate + " — " + (rowOk ? "ОТКРЫТ" : "ЗАБЛОКИРОВАН"),
                    rowOk ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text(node.lore(), NamedTextColor.GRAY));
            lore.add(Component.text(describeEffect(node), NamedTextColor.WHITE));
            lore.add(Component.text("кол. " + node.col() + " · " + node.type(),
                    branchColor(node.treeId())));
            for (Map.Entry<String, Integer> e : node.prereqs().entrySet()) {
                Spec2Node prereqNode = tree.find(e.getKey());
                String prereqName = prereqNode != null ? prereqNode.name() : e.getKey();
                int have = ranks.getOrDefault(e.getKey(), 0);
                boolean met = have >= e.getValue();
                lore.add(Component.text((met ? "↑ ✔ " : "↑ требует " + e.getValue() + ": ")
                                + "«" + prereqName + "»",
                        met ? NamedTextColor.DARK_GRAY : NamedTextColor.RED));
            }
            lore.add(Component.empty());
            if (maxed) {
                lore.add(Component.text("✔ МАКСИМУМ " + rank + "/" + node.maxRank(),
                        NamedTextColor.GREEN));
            } else if (!rowOk) {
                lore.add(Component.text("Ряд откроется при " + gate + " очках в дереве (сейчас "
                        + inTree + ", нужно ещё " + (gate - inTree) + ")", NamedTextColor.RED));
            } else if (!prereqOk) {
                lore.add(Component.text("Нужны пререквизиты (требуемые ранги)",
                        NamedTextColor.RED));
            } else if (!affordable) {
                lore.add(Component.text("Не хватает очков", NamedTextColor.RED));
            } else {
                lore.add(Component.text("ЛКМ — купить ранг " + (rank + 1) + "/" + node.maxRank(),
                        NamedTextColor.YELLOW));
            }
            meta.lore(lore);
            if (maxed) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private static NamedTextColor branchColor(String treeId) {
        return switch (treeId) {
            case "arms", "marksmanship", "discipline", "arcane", "assassination", "affliction"
                    -> NamedTextColor.AQUA;
            default -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private String describeEffect(Spec2Node node) {
        if (node.effect() == null) {
            return "";
        }
        var e = node.effect();
        return switch (e.kind()) {
            case "attr" -> "+" + (int) e.value() + " " + switch (e.target()) {
                case "str" -> "СИЛЫ";
                case "agi" -> "ЛОВКОСТИ";
                case "int" -> "ИНТЕЛЛЕКТА";
                default -> e.target();
            } + " за ранг";
            case "resist" -> "both".equals(e.target())
                    ? "+" + (int) e.value() + "% физ и +" + (int) e.value2() + "% маг резиста за ранг"
                    : "+" + (int) e.value() + "% "
                        + ("phys".equals(e.target()) ? "физ" : "маг") + "резиста за ранг";
            case "kit_base" -> "+" + (int) e.value() + " к базе «" + e.target() + "» за ранг";
            case "kit_mult" -> "+" + (int) Math.round(e.value() * 100)
                    + "% к коэф. «" + e.target() + "» за ранг";
            case "cd" -> "−" + (int) Math.round(e.value() * 100)
                    + "% кулдауна «" + e.target() + "» за ранг";
            case "kit_cd" -> "−" + (int) e.value() + " с кулдауна «" + e.target() + "» за ранг";
            case "regen" -> "+" + (int) e.value() + " ресурс/с за ранг";
            case "avoid" -> "+" + (int) e.value() + "% "
                    + ("dodge".equals(e.target()) ? "уклонения" : "парирования") + " за ранг";
            case "pen_phys_pct", "pen_magic_pct" -> "+" + (int) e.value()
                    + "% пробития " + e.kind() + " за ранг";
            case "proc" -> "+" + e.value() + " к проце «" + e.target() + "» за ранг";
            case "unlock_ability" -> node.isUltimate()
                    ? "УЛЬТ: открывает «" + e.target() + "»"
                    : "открывает способность «" + e.target() + "»";
            default -> e.kind() + " " + e.target();
        };
    }
}
