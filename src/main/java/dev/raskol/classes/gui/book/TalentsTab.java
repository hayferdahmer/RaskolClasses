// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.talent.TalentModel;
import dev.raskol.classes.talent.TalentService;
import dev.raskol.classes.talent.TalentsRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P4b): вкладка «Таланты спеки»: инфо-бутыль, 9 узлов, кристалл сброса.
 * 1.14.0 (Б3): WoW-подобная вертикальная сетка (BookSlots.TALENT_NODE_SLOTS):
 *         тир1 → тир2 → тир3 → ульт с конвергенцией к центру;
 *         в лоре узла — тег ветки (A/B) и строки «↑ требует: «Имя»» по пререквизитам
 *         (аналог стрелок референса); шапка дерева показывает роль спеки.
 */
public final class TalentsTab implements BookTabView {

    private static final Map<UUID, Long> RESET_ARM = new ConcurrentHashMap<>();

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.TALENTS;
    }

    @Override
    public void render(RenderCtx ctx) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        UUID uuid = player.getUniqueId();
        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec == null) {
            ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, BookItems.infoItem(Material.BARRIER,
                    "Таланты недоступны", List.of(
                            "Сначала выбери специализацию",
                            "во вкладке «Специализации» (уровень 40+)")));
            return;
        }
        TalentModel.TalentTree tree = TalentsRegistry.treeOf(spec.id());
        if (tree == null) {
            ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, BookItems.infoItem(Material.BARRIER,
                    "Дерево не найдено", List.of("specId: " + spec.id())));
            return;
        }
        TalentService talents = plugin.getTalentService();
        int available = talents.availablePoints(uuid, spec.id());
        List<String> owned = talents.purchased(uuid, spec.id());

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        info.editMeta(meta -> {
            meta.displayName(Component.text("Таланты: " + spec.displayName(), NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Роль: " + spec.role(), NamedTextColor.DARK_AQUA));
            lore.add(Component.empty());
            lore.add(Component.text("Очков доступно: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(available), NamedTextColor.WHITE)));
            lore.add(Component.text("Потрачено всего: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(talents.spentGlobal(uuid)), NamedTextColor.WHITE))
                    .append(Component.text(" · заработано: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(talents.earnedPoints(uuid)), NamedTextColor.WHITE)));
            lore.add(Component.text("Очки — общий бюджет персонажа (все деревья)", NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("ЛКМ по узлу — купить талант", NamedTextColor.YELLOW));
            meta.lore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        ctx.inv().setItem(BookSlots.SLOT_TALENT_INFO, info);

        List<TalentModel.TalentNode> nodes = tree.nodes();
        for (int i = 0; i < nodes.size() && i < BookSlots.TALENT_NODE_SLOTS.length; i++) {
            ctx.inv().setItem(BookSlots.TALENT_NODE_SLOTS[i],
                    talentNodeItem(plugin, player, tree, nodes.get(i), owned, available));
        }

        ItemStack reset = new ItemStack(Material.END_CRYSTAL);
        reset.editMeta(meta -> {
            meta.displayName(Component.text("Сброс дерева талантов", NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Цена: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getConfig().getInt("talents.reset-base", 500)
                            + " + " + plugin.getConfig().getInt("talents.reset-per-point", 25)
                            + "×потрачено", NamedTextColor.RED))
                    .append(Component.text(" монет", NamedTextColor.GRAY)));
            lore.add(Component.text("Очки возвращаются в общий пул", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("ПКМ №1 — взвести · ПКМ №2 (30 с) — сбросить", NamedTextColor.YELLOW));
            meta.lore(lore);
        });
        ctx.inv().setItem(BookSlots.SLOT_TALENT_RESET, reset);
    }

    @Override
    public void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        UUID uuid = player.getUniqueId();
        if (slot == BookSlots.SLOT_TALENT_RESET && right) {
            Long armed = RESET_ARM.get(uuid);
            long now = System.currentTimeMillis();
            if (armed == null || now - armed > BookSlots.RESET_ARM_MILLIS) {
                RESET_ARM.put(uuid, now);
                player.sendMessage(Component.text(
                        "Сброс талантов взведён: ПКМ по кристаллу ещё раз в течение 30 с.",
                        NamedTextColor.YELLOW));
            } else {
                RESET_ARM.remove(uuid);
                boolean free = player.hasPermission("raskolclasses.admin");
                TalentService.ResetResult result =
                        plugin.getTalentService().reset(player, free);
                player.sendMessage(Component.text(switch (result) {
                    case OK -> "Дерево талантов сброшено: очки возвращены в общий пул.";
                    case NO_SPEC -> "Спека не выбрана — сбрасывать нечего.";
                    case NO_PURCHASED -> "В дереве нет купленных узлов.";
                    case POOR -> "Не хватает монет на сброс талантов.";
                    case NO_ECONOMY -> "Экономика недоступна — сброс отключён.";
                    case RATE_LIMITED -> "Слишком часто: подожди мгновение и повтори.";
                }, result == TalentService.ResetResult.OK
                        ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            ctx.refresh();
            return;
        }
        int nodeIdx = BookSlots.indexOf(BookSlots.TALENT_NODE_SLOTS, slot);
        if (nodeIdx >= 0 && left) {
            Spec spec = plugin.getSpecService().getSpec(uuid);
            if (spec == null) {
                return;
            }
            TalentModel.TalentTree tree = TalentsRegistry.treeOf(spec.id());
            if (tree == null || nodeIdx >= tree.nodes().size()) {
                return;
            }
            TalentModel.TalentNode node = tree.nodes().get(nodeIdx);
            TalentService.PurchaseResult result =
                    plugin.getTalentService().purchase(player, node.id());
            player.sendMessage(Component.text(switch (result) {
                case OK -> "Талант «" + node.name() + "» изучен.";
                case TALENTS_DISABLED -> "Таланты отключены конфигурацией.";
                case NO_SPEC -> "Спека не выбрана.";
                case NODE_NOT_FOUND -> "Узел не найден в дереве.";
                case WRONG_TREE -> "Узел не из дерева активной спеки.";
                case TIER_GATE -> "Рановато: нужен уровень персонажа выше.";
                case PREREQ_MISSING -> "Сначала изучи предыдущие узлы ветки.";
                case NOT_ENOUGH_POINTS -> "Не хватает очков талантов.";
                case ALREADY_OWNED -> "Талант уже изучен.";
                case RATE_LIMITED -> "Слишком часто: подожди мгновение и повтори.";
            }, result == TalentService.PurchaseResult.OK
                    ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            ctx.refresh();
        }
    }

    /* ------------------------------ предмет узла ------------------------------ */

    private ItemStack talentNodeItem(RaskolClasses plugin, Player player,
                                     TalentModel.TalentTree tree,
                                     TalentModel.TalentNode node,
                                     List<String> owned, int available) {
        UUID uuid = player.getUniqueId();
        boolean isOwned = owned.contains(node.id());
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int gate = TalentModel.tierGate(node.tier(),
                plugin.getConfig().getInt("talents.start-level", 40));
        boolean tierOk = charLevel >= gate;
        boolean prereqOk = owned.containsAll(node.prereqs());
        boolean affordable = node.cost() <= available;
        boolean isUlt = node.tier() == 4;

        ItemStack item = new ItemStack(isUlt ? Material.BEACON : Material.NETHER_STAR);
        item.editMeta(meta -> {
            NamedTextColor nameColor = isOwned ? NamedTextColor.GREEN
                    : (!tierOk || !prereqOk) ? NamedTextColor.DARK_GRAY
                    : affordable ? NamedTextColor.GOLD : NamedTextColor.RED;
            meta.displayName(Component.text((isUlt ? "★ " : "") + node.name(), nameColor));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(node.lore(), NamedTextColor.GRAY));
            lore.add(Component.text(describeEffect(node.effect()), NamedTextColor.WHITE));
            lore.add(Component.text("Ветка " + node.branch() + " · тир " + node.tier()
                    + " · цена " + node.cost() + " очк.", branchColor(node.branch())));
            // 1.14.0: стрелки-пререквизиты именами узлов (аналог линий референса)
            for (String prereqId : node.prereqs()) {
                String prereqName = nodeName(tree, prereqId);
                lore.add(Component.text((owned.contains(prereqId) ? "↑ ✔ " : "↑ требует: ")
                        + "«" + prereqName + "»",
                        owned.contains(prereqId) ? NamedTextColor.DARK_GRAY : NamedTextColor.RED));
            }
            lore.add(Component.empty());
            if (isOwned) {
                lore.add(Component.text("✔ ИЗУЧЕНО", NamedTextColor.GREEN));
            } else if (!tierOk) {
                lore.add(Component.text("Нужен уровень персонажа " + gate, NamedTextColor.RED));
            } else if (!prereqOk) {
                lore.add(Component.text("Нужны предыдущие узлы ветки", NamedTextColor.RED));
            } else if (!affordable) {
                lore.add(Component.text("Не хватает очков", NamedTextColor.RED));
            } else {
                lore.add(Component.text("ЛКМ — купить", NamedTextColor.YELLOW));
            }
            meta.lore(lore);
            if (isOwned) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private static String nodeName(TalentModel.TalentTree tree, String id) {
        for (TalentModel.TalentNode n : tree.nodes()) {
            if (n.id().equals(id)) {
                return n.name();
            }
        }
        return id;
    }

    private static NamedTextColor branchColor(String branch) {
        return "A".equals(branch) ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;
    }

    private String describeEffect(TalentModel.TalentEffect e) {
        if (e == null) {
            return "";
        }
        return switch (e.kind()) {
            case "attr" -> "+" + (int) e.value() + " " + switch (e.target()) {
                case "str" -> "СИЛЫ";
                case "agi" -> "ЛОВКОСТИ";
                case "int" -> "ИНТЕЛЛЕКТА";
                default -> e.target();
            } + " постоянно";
            case "resist" -> "both".equals(e.target())
                    ? "+" + (int) e.value() + "% физ и +" + (int) e.value2() + "% маг резиста"
                    : "+" + (int) e.value() + "% " + ("phys".equals(e.target()) ? "физ" : "маг") + "резиста";
            case "kit_base" -> "+" + (int) e.value() + " к базе «" + e.target() + "»";
            case "kit_mult" -> "+" + (int) Math.round(e.value() * 100) + "% к коэф. «" + e.target() + "»";
            case "cd" -> "−" + (int) Math.round(e.value() * 100) + "% кулдауна «" + e.target() + "»";
            case "regen" -> "+" + (int) e.value() + " ресурс/с";
            case "avoid" -> "+" + (int) e.value() + "% " + ("dodge".equals(e.target()) ? "уклонения" : "парирования");
            case "proc" -> "+" + e.value() + " к проце «" + e.target() + "»";
            default -> e.kind() + " " + e.target();
        };
    }
}
