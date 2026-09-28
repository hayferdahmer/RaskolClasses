// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 1.11.4 (P4b): вкладка «Специализации»: выбор спеки + отречение (дабл-арм 30 с). */
public final class SpecsTab implements BookTabView {

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.SPECS;
    }

    @Override
    public void render(RenderCtx ctx) {
        List<Spec> specs = specsFor(ctx.pc());
        for (int i = 0; i < specs.size() && i < BookSlots.SPEC_SLOTS.length; i++) {
            ctx.inv().setItem(BookSlots.SPEC_SLOTS[i], specItem(ctx.plugin(), ctx.player(), specs.get(i)));
        }
        ctx.inv().setItem(BookSlots.SLOT_RESPEC, respecItem(ctx.plugin(), ctx.player()));
    }

    @Override
    public void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        int idx = BookSlots.indexOf(BookSlots.SPEC_SLOTS, slot);
        if (idx >= 0) {
            List<Spec> specs = specsFor(ctx.pc());
            if (idx >= specs.size()) {
                return;
            }
            Spec spec = specs.get(idx);
            Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
            if (!right) {
                return;
            }
            if (current == null) {
                plugin.getSpecService().choose(player, spec);
            } else if (current == spec) {
                player.sendMessage(Component.text(
                        "Спека уже выбрана: пассивка работает постоянно, свитков активок больше нет.",
                        NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text(BookItems.msg(plugin,
                        "book.msg.spec.other", "Спека уже выбрана: {name}. Отречение — кристалл ниже.")
                        .replace("{name}", current.displayName()), NamedTextColor.RED));
            }
            ctx.refresh();
            return;
        }
        if (slot == BookSlots.SLOT_RESPEC && right) {
            SpecService service = plugin.getSpecService();
            Spec current = service.getSpec(player.getUniqueId());
            if (current == null) {
                player.sendMessage(Component.text(BookItems.msg(plugin,
                        "book.msg.respec.none", "Спеки нет — отрекаться не от чего."),
                        NamedTextColor.GRAY));
                return;
            }
            SpecService.RespecResult result = service.confirmRespec(player);
            if (result == SpecService.RespecResult.NOT_PENDING) {
                service.requestRespec(player);
                player.sendMessage(Component.text(BookItems.msg(plugin,
                        "book.msg.respec.arm", "Отречение взведено: ПКМ по кристаллу ещё раз в течение 30 с. Цена: {price} монет")
                        .replace("{price}", String.valueOf(service.respecCost(player))),
                        NamedTextColor.YELLOW));
            } else if (result == SpecService.RespecResult.OK) {
                player.sendMessage(Component.text(BookItems.msg(plugin,
                        "book.msg.respec.ok", "Путь сброшен. Выбери новую спеку."),
                        NamedTextColor.GREEN));
            } else if (result == SpecService.RespecResult.POOR) {
                player.sendMessage(Component.text(BookItems.msg(plugin,
                        "book.msg.respec.poor", "Не хватает монет на отречение."),
                        NamedTextColor.RED));
            } else if (result == SpecService.RespecResult.NO_ECONOMY) {
                player.sendMessage(Component.text(BookItems.msg(plugin,
                        "book.msg.respec.noecon", "Экономика недоступна — респец отключён."),
                        NamedTextColor.RED));
            }
            ctx.refresh();
        }
    }

    private static List<Spec> specsFor(PlayerClass pc) {
        List<Spec> list = new ArrayList<>();
        for (Spec spec : Spec.values()) {
            if (spec.playerClass() == pc) {
                list.add(spec);
            }
        }
        return list;
    }

    private ItemStack specItem(RaskolClasses plugin, Player player, Spec spec) {
        var def = plugin.getSpecRegistry().get(spec);
        Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.displayName(Component.text(spec.displayName(),
                    current == spec ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            List<Component> lore = new ArrayList<>();
            if (def != null) {
                lore.add(Component.text(BookItems.msg(plugin, "book.spec.passive", "Пассив: {text}")
                        .replace("{text}", def.passiveDescription()), NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            if (current == spec) {
                lore.add(Component.text(BookItems.msg(plugin, "book.spec.chosen", "Выбрана тобой"),
                        NamedTextColor.GREEN));
                lore.add(Component.text("Пассивка работает постоянно", NamedTextColor.DARK_GRAY));
            } else if (current == null) {
                lore.add(Component.text(BookItems.msg(plugin, "book.spec.notchosen",
                        "Не выбрана · ПКМ — выбрать (уровень 40+)"), NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text(BookItems.msg(plugin, "book.spec.other",
                        "Выбрана другая спека — отречение ниже"), NamedTextColor.RED));
            }
            meta.lore(lore);
            if (current == spec) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private ItemStack respecItem(RaskolClasses plugin, Player player) {
        Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
        int cost = plugin.getSpecService().respecCost(player);
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        item.editMeta(meta -> {
            meta.displayName(Component.text(BookItems.msg(plugin, "book.respec.title", "Отречение от пути"),
                    NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            if (current == null) {
                lore.add(Component.text(BookItems.msg(plugin, "book.respec.nospec",
                        "Спеки нет — отрекаться не от чего"), NamedTextColor.GRAY));
            } else {
                lore.add(Component.empty());
                lore.add(Component.text(BookItems.msg(plugin, "book.respec.current", "Текущая спека: {name}")
                        .replace("{name}", current.displayName()), NamedTextColor.WHITE));
                lore.add(Component.text(BookItems.msg(plugin, "book.respec.price", "Цена: {price} монет (сжигаются)")
                        .replace("{price}", String.valueOf(cost)), NamedTextColor.RED));
                lore.add(Component.empty());
                lore.add(Component.text(BookItems.msg(plugin, "book.respec.hint",
                        "ПКМ №1 — взвести, ПКМ №2 (30 с) — отречься"), NamedTextColor.YELLOW));
            }
            meta.lore(lore);
        });
        return item;
    }
}
