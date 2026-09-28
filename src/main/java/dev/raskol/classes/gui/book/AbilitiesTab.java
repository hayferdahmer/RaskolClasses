// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.install.InstallationType;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 1.11.4 (P4b): вкладка «Способности»: 5 слотов кита + слот инсталляции. */
public final class AbilitiesTab implements BookTabView {

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.ABILITIES;
    }

    @Override
    public void render(RenderCtx ctx) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        PlayerClass pc = ctx.pc();
        for (int i = 0; i < BookSlots.ABILITY_SLOTS.length; i++) {
            AbilityDef def = plugin.getAbilities().getBySlot(pc, i + 1);
            if (def != null) {
                ctx.inv().setItem(BookSlots.ABILITY_SLOTS[i], abilityItem(plugin, player, pc, def));
            }
        }
        InstallationType type = InstallationType.forClass(pc);
        if (type != null) {
            ctx.inv().setItem(BookSlots.SLOT_INSTALL, installItem(plugin, player, pc, type));
        }
    }

    @Override
    public void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        PlayerClass pc = ctx.pc();
        int idx = BookSlots.indexOf(BookSlots.ABILITY_SLOTS, slot);
        if (idx >= 0) {
            AbilityDef def = plugin.getAbilities().getBySlot(pc, idx + 1);
            if (def == null) {
                return;
            }
            if (left) {
                if (plugin.getAbilities().tryCast(player, def)) {
                    plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
                }
            } else {
                if (countAbilityScrolls(plugin, player, def.id()) > 0) {
                    player.sendMessage(Component.text(BookItems.msg(plugin,
                            "book.msg.scroll.dup", "Свиток уже в инвентаре — дубль не выдан."),
                            NamedTextColor.GRAY));
                } else {
                    player.getInventory().addItem(plugin.getTokens().create(def, pc));
                    player.sendMessage(Component.text(BookItems.msg(plugin,
                            "book.msg.scroll.got", "Свиток получен: "), NamedTextColor.GRAY)
                            .append(Component.text(def.displayName(), pc.getColor())));
                }
            }
            ctx.refresh();
            return;
        }
        if (slot == BookSlots.SLOT_INSTALL) {
            InstallationType type = InstallationType.forClass(pc);
            if (type == null) {
                return;
            }
            if (left) {
                plugin.getInstallations().tryPlace(player);
            } else {
                if (countInstallScrolls(plugin, player, type) > 0) {
                    player.sendMessage(Component.text(BookItems.msg(plugin,
                            "book.msg.scroll.dup", "Свиток уже в инвентаре — дубль не выдан."),
                            NamedTextColor.GRAY));
                } else {
                    player.getInventory().addItem(plugin.getInstallToken().create(type, pc));
                    player.sendMessage(Component.text(BookItems.msg(plugin,
                            "book.msg.scroll.got", "Свиток получен: "), NamedTextColor.GRAY)
                            .append(Component.text(type.displayName(), pc.getColor())));
                }
            }
            ctx.refresh();
        }
    }

    /* ------------------------------ предмет-билдеры ------------------------------ */

    private ItemStack abilityItem(RaskolClasses plugin, Player player, PlayerClass pc, AbilityDef def) {
        UUID uuid = player.getUniqueId();
        long remaining = plugin.getCooldowns().getRemainingMillis(uuid, def.id());
        double resource = plugin.getResources().getValue(uuid);
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        boolean unlocked = level == SkillLevelProvider.NO_SKILL_SYSTEM
                || level >= def.unlockLevel();
        boolean ready = remaining <= 0 && resource >= def.cost() && unlocked;
        int scrolls = countAbilityScrolls(plugin, player, def.id());

        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            Component name = unlocked
                    ? TextFx.gradient("[" + def.slot() + "] " + def.displayName(),
                            plugin.getRaskolConfig().themeOf(pc).primary(),
                            plugin.getRaskolConfig().themeOf(pc).secondary())
                    : Component.text("[" + def.slot() + "] " + def.displayName(), NamedTextColor.DARK_GRAY);
            meta.displayName(name);
            List<Component> lore = new ArrayList<>();
            String desc = plugin.getRaskolConfig().abilityDescription(pc, def.id(), "");
            if (!desc.isEmpty()) {
                lore.add(Component.text(desc, NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Цена: ", NamedTextColor.GRAY)
                    .append(Component.text(def.cost() + " " + pc.getResourceName(),
                            resource >= def.cost() ? NamedTextColor.WHITE : NamedTextColor.RED)));
            if (remaining > 0) {
                lore.add(Component.text(BookItems.msg(plugin, "book.recharging", "Перезарядка: {sec} с")
                        .replace("{sec}", String.valueOf(remaining / 1000L + 1)),
                        NamedTextColor.RED));
            } else {
                lore.add(Component.text(BookItems.msg(plugin, "book.cooldown", "Кулдаун: {sec} с")
                        .replace("{sec}", String.valueOf(def.cooldownMillis() / 1000L)),
                        NamedTextColor.GRAY));
            }
            lore.add(Component.text(BookItems.msg(plugin, "book.unlock", "Открытие: уровень {level}")
                    .replace("{level}", String.valueOf(def.unlockLevel())),
                    unlocked ? NamedTextColor.GRAY : NamedTextColor.RED));
            lore.add(Component.text(scrolls > 0
                    ? BookItems.msg(plugin, "book.scroll.have", "Свиток: в инвентаре ({count})")
                            .replace("{count}", String.valueOf(scrolls))
                    : BookItems.msg(plugin, "book.scroll.none", "Свиток: нет"),
                    scrolls > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            if (!unlocked) {
                lore.add(Component.text("Заблокировано до уровня " + def.unlockLevel(), NamedTextColor.RED));
            } else {
                lore.add(Component.text(BookItems.msg(plugin, "book.use.left", "ЛКМ — применить"), NamedTextColor.YELLOW));
                lore.add(Component.text(BookItems.msg(plugin, "book.use.right", "ПКМ — свиток в хотбар"), NamedTextColor.GRAY));
            }
            meta.lore(lore);
            if (ready) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private ItemStack installItem(RaskolClasses plugin, Player player, PlayerClass pc, InstallationType type) {
        int scrolls = countInstallScrolls(plugin, player, type);
        ItemStack item = new ItemStack(type.displayItem());
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient("⚙ " + type.displayName(),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(BookItems.msg(plugin,
                    "book.install.desc." + type.id().toLowerCase(Locale.ROOT), installDescDef(type)),
                    NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text(BookItems.msg(plugin, "book.install.active", "Активно: {count}/2 · TTL {ttl} с")
                    .replace("{count}", String.valueOf(
                            plugin.getInstallations().countOf(player.getUniqueId())))
                    .replace("{ttl}", String.valueOf(
                            plugin.getConfig().getInt("installations.ttl-seconds", 60))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(scrolls > 0
                    ? BookItems.msg(plugin, "book.scroll.have", "Свиток: в инвентаре ({count})")
                            .replace("{count}", String.valueOf(scrolls))
                    : BookItems.msg(plugin, "book.scroll.none", "Свиток: нет"),
                    scrolls > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text(BookItems.msg(plugin, "book.place.left", "ЛКМ — поставить здесь"), NamedTextColor.YELLOW));
            lore.add(Component.text(BookItems.msg(plugin, "book.place.right", "ПКМ — свиток постановки"), NamedTextColor.GRAY));
            meta.lore(lore);
        });
        return item;
    }

    /** 1.10.4: покрыт HERESY_CIRCLE = «Пентаграмма». */
    private static String installDescDef(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> "Аура: Resistance I союзникам в радиусе 6 на 8 с";
            case BEAR_TRAP -> "Мина: Slowness VI 2 с + 3 урона шагнувшему врагу";
            case LIGHT_WARD -> "Зона: +2 HP/с союзникам в радиусе 4 на 6 с";
            case FROST_RUNE -> "Руна-зона 8 блоков 30 с: урон+замедление врагам с нарастанием; магу внутри +3 маны/с и ИНТ×2";
            case SMOKE_BOMB -> "Мина: Blindness 2 с врагам + Speed I себе 3 с";
            case HERESY_CIRCLE -> "Пентаграмма: круг 6 блоков 25 с: врагам маг-урон и запрет лечения; чернокнижнику +3 Скверны/с; в аду урон ×6";
        };
    }

    private static int countAbilityScrolls(RaskolClasses plugin, Player player, String id) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && id.equals(plugin.getTokens().readId(item))) {
                count++;
            }
        }
        return count;
    }

    private static int countInstallScrolls(RaskolClasses plugin, Player player, InstallationType type) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && plugin.getInstallToken().readType(item) == type) {
                count++;
            }
        }
        return count;
    }
}
