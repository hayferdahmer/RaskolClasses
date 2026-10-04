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
 * Показывает активное дерево основной спеки и список деревьев класса.
 * ЛКМ по узлу = +1 ранг; узел открыт при ряд-гейте + ранговых пререквизитах.
 *
 * 1.14.0-fix (ряды): узлы сортируются по (row,col) общим orderedNodes() и в render,
 *   и в onClick (раньше index клетки мог разъезжаться с index узла); в lore каждого
 *   узла — цветной статус ряда [Ряд N · гейт G]; info-предмет даёт сводку по 6 рядам.
 *   Полноценная сетка-разделка рядов требует BookSlots.TALENT_NODE_SLOTS layout —
 *   сделано текстово/цветово, чтобы не ломать раскладку вслепую.
 */
public final class TalentsTab implements BookTabView {

    private static final Map<UUID, Long> RESET_ARM = new ConcurrentHashMap<>();
    private static final int ROW_COUNT = 6;

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.TALENTS;
    }

    /** Узлы дерева в стабильном порядке ряд→колонка (общий для render и onClick). */
    private static List<Spec2Node> orderedNodes(Spec2Tree tree) {
        List<Spec2Node> list = new ArrayList<>(tree.nodes());
        list.sort(Comparator.comparingInt(Spec2Node::row).thenComparingInt(Spec2Node::col));
        return list;
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
        int inTree = tree.spentInTree(svc.storage().getRanks(uuid, main));
        Map<String, Integer> ranks = svc.storage().getRanks(uuid, main);

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        info.editMeta(meta -> {
            meta.displayName(Component.text("Дерево: " + main, NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Очков доступно: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(available), NamedTextColor.WHITE)));
            lore.add(Component.text("Потрачено: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(spent), NamedTextColor.WHITE))
                    .append(Component.text(" · заработано: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(earned), NamedTextColor.WHITE)));
            lore.add(Component.text("В этом дереве: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(inTree), NamedTextColor.AQUA))
                    .append(Component.text(" очков (гейты рядов)", NamedTextColor.DARK_GRAY)));
            lore.add(Component.empty());
            lore.add(Component.text("Ряды дерева (гейт по очкам ВНУТРИ дерева):",
                    NamedTextColor.YELLOW));
            for (int row = 1; row <= ROW_COUNT; row++) {
                int gate = Spec2Points.ROW_GATES[row - 1];
                boolean open = inTree >= gate;
                lore.add(Component.text((open ? "  ✔ " : "  🔒 ") + "Ряд " + row
                                + (row == ROW_COUNT ? " (ульт)" : "") + " — гейт " + gate,
                        open ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .append(Component.text(open ? "" : "  (ещё " + (gate - inTree) + ")",
                                NamedTextColor.DARK_GRAY)));
            }
            lore.add(Component.empty());
            lore.add(Component.text("ЛКМ по узлу = +1 ранг (стоимость 1 очко)",
                    NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, info);

        List<Spec2Node> nodes = orderedNodes(tree);
        for (int i = 0; i < nodes.size() && i < BookSlots.TALENT_NODE_SLOTS.length; i++) {
            ctx.inv().setItem(BookSlots.TALENT_NODE_SLOTS[i],
                    nodeItem(plugin, player, tree, nodes.get(i), ranks, available, inTree));
        }

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

    @Override
    public void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        UUID uuid = player.getUniqueId();
        Spec2Service svc = plugin.getSpec2Service();
        String main = svc.mainSpec(uuid);

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
        int nodeIdx = BookSlots.indexOf(BookSlots.TALENT_NODE_SLOTS, slot);
        if (nodeIdx < 0) {
            return;
        }
        Spec2Tree tree = Spec2Registry.treeOf(main);
        if (tree == null) {
            return;
        }
        List<Spec2Node> nodes = orderedNodes(tree); // тот же порядок, что в render
        if (nodeIdx >= nodes.size()) {
            return;
        }
        Spec2Node node = nodes.get(nodeIdx);
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

    private ItemStack nodeItem(RaskolClasses plugin, Player player, Spec2Tree tree,
                               Spec2Node node, Map<String, Integer> ranks, int available,
                               int inTree) {
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
            // 1.14.0-fix (ряды): явный статус ряда первым
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
