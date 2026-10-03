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
 * 1.11.4 (P4b) → 1.14.0 (Б4): вкладка «Таланты спеки» с рангами узлов «2/5».
 * WoW-подобная сетка (BookSlots.TALENT_NODE_SLOTS): тир1→тир2→тир3→ульт.
 * ЛКМ по узлу = +1 ранг (стоит costPerRank очков); узел открыт, когда все
 * пререквизиты прокачаны до maxRank (аналог стрелок референса).
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
                            "во вкладке «Специализации» (уровень 15+)")));
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
        Map<String, Integer> owned = talents.purchased(uuid, spec.id());

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
            lore.add(Component.text("ЛКМ по узлу = +1 ранг (до 5/5)", NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Узел открыт, когда пререквизиты 5/5", NamedTextColor.YELLOW));
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
                TalentService.ResetResult result = plugin.getTalentService().reset(player, free);
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
            TalentService.PurchaseResult result = plugin.getTalentService().purchase(player, node.id());
            int newRank = plugin.getTalentService().purchased(uuid, spec.id())
                    .getOrDefault(node.id(), 0);
            player.sendMessage(Component.text(switch (result) {
                case OK -> "Талант «" + node.name() + "» — ранг " + newRank + "/" + node.maxRank() + ".";
                case TALENTS_DISABLED -> "Таланты отключены конфигурацией.";
                case NO_SPEC -> "Спека не выбрана.";
                case NODE_NOT_FOUND -> "Узел не найден в дереве.";
                case WRONG_TREE -> "Узел не из дерева активной спеки.";
                case TIER_GATE -> "Рановато: нужен уровень персонажа выше.";
                case PREREQ_MISSING -> "Сначала прокачай предыдущие узлы ветки до 5/5.";
                case NOT_ENOUGH_POINTS -> "Не хватает очков талантов.";
                case ALREADY_OWNED -> "Талант уже прокачан до максимума.";
                case RATE_LIMITED -> "Слишком часто: подожди мгновение и повтори.";
            }, result == TalentService.PurchaseResult.OK
                    ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            ctx.refresh();
        }
    }

    private ItemStack talentNodeItem(RaskolClasses plugin, Player player,
                                     TalentModel.TalentTree tree,
                                     TalentModel.TalentNode node,
                                     Map<String, Integer> owned, int available) {
        UUID uuid = player.getUniqueId();
        int rank = owned.getOrDefault(node.id(), 0);
        boolean isOwned = rank > 0;
        boolean maxed = rank >= node.maxRank();
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int gate = TalentModel.tierGate(node.tier(),
                plugin.getConfig().getInt("talents.start-level", 15));
        boolean tierOk = charLevel >= gate;
        boolean prereqOk = true;
        for (String prereqId : node.prereqs()) {
            TalentModel.TalentNode prereqNode = tree.find(prereqId);
            int prereqRank = owned.getOrDefault(prereqId, 0);
            if (prereqNode == null || prereqRank < prereqNode.maxRank()) {
                prereqOk = false;
                break;
            }
        }
        boolean affordable = node.costPerRank() <= available;
        boolean isUlt = node.tier() == 4;

        ItemStack item = new ItemStack(isUlt ? Material.BEACON : Material.NETHER_STAR);
        item.editMeta(meta -> {
            NamedTextColor nameColor = maxed ? NamedTextColor.GREEN
                    : isOwned ? NamedTextColor.GOLD
                    : (!tierOk || !prereqOk) ? NamedTextColor.DARK_GRAY
                    : affordable ? NamedTextColor.WHITE : NamedTextColor.RED;
            meta.displayName(Component.text((isUlt ? "★ " : "") + node.name()
                    + " " + rank + "/" + node.maxRank(), nameColor));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(node.lore(), NamedTextColor.GRAY));
            lore.add(Component.text(describeEffect(node.effect()), NamedTextColor.WHITE));
            lore.add(Component.text("Ветка " + node.branch() + " · тир " + node.tier()
                    + " · ранг " + node.costPerRank() + " очк.", branchColor(node.branch())));
            for (String prereqId : node.prereqs()) {
                TalentModel.TalentNode prereqNode = tree.find(prereqId);
                String prereqName = prereqNode != null ? prereqNode.name() : prereqId;
                int prereqRank = owned.getOrDefault(prereqId, 0);
                boolean prereqMaxed = prereqNode != null && prereqRank >= prereqNode.maxRank();
                lore.add(Component.text((prereqMaxed ? "↑ ✔ " : "↑ требует 5/5: ")
                        + "«" + prereqName + "»",
                        prereqMaxed ? NamedTextColor.DARK_GRAY : NamedTextColor.RED));
            }
            lore.add(Component.empty());
            if (maxed) {
                lore.add(Component.text("✔ МАКСИМУМ " + rank + "/" + node.maxRank(), NamedTextColor.GREEN));
            } else if (!tierOk) {
                lore.add(Component.text("Нужен уровень персонажа " + gate, NamedTextColor.RED));
            } else if (!prereqOk) {
                lore.add(Component.text("Нужны пререквизиты 5/5", NamedTextColor.RED));
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
            } + " за ранг";
            case "resist" -> "both".equals(e.target())
                    ? "+" + (int) e.value() + "% физ и +" + (int) e.value2() + "% маг резиста за ранг"
                    : "+" + (int) e.value() + "% " + ("phys".equals(e.target()) ? "физ" : "маг") + "резиста за ранг";
            case "kit_base" -> "+" + (int) e.value() + " к базе «" + e.target() + "» за ранг";
            case "kit_mult" -> "+" + (int) Math.round(e.value() * 100) + "% к коэф. «" + e.target() + "» за ранг";
            case "cd" -> "−" + (int) Math.round(e.value() * 100) + "% кулдауна «" + e.target() + "» за ранг";
            case "regen" -> "+" + (int) e.value() + " ресурс/с за ранг";
            case "avoid" -> "+" + (int) e.value() + "% " + ("dodge".equals(e.target()) ? "уклонения" : "парирования") + " за ранг";
            case "proc" -> "+" + e.value() + " к проце «" + e.target() + "» за ранг";
            default -> e.kind() + " " + e.target();
        };
    }
}
