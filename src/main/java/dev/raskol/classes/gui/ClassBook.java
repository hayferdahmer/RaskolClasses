// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.install.InstallationType;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;
import dev.raskol.classes.spec.SpecService;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Книга класса (1.5.4 + 1.5.5 + 1.5.9 + 1.6.6 + 1.7.0 пакет 1).
 * 1.6.6: сводка резистов (предмет-щит, слот 14) и grant-строки в лоре.
 * 1.6.7: ключ маг-гранта — плоский book.resist.grant-magic.
 * 1.7.0 пакет 1: предмет-эмблема атрибутов (слот 16 вкладки «Класс»):
 * значения STR/AGI/INT со звёздочкой у основного атрибута, maxHP по формуле,
 * эффективные уклонение/парирование (после DR), криты мили/магии.
 * Значения берутся из AttributeService — тот же источник, что бой/PAPI/дебаг.
 */
public final class ClassBook implements InventoryHolder {

    public enum Tab { ABILITIES, SPECS, CLASS }

    private static final int SIZE = 54;
    private static final int SLOT_EMBLEM = 4;
    private static final int SLOT_TAB_ABILITIES = 48;
    private static final int SLOT_TAB_SPECS = 50;
    private static final int SLOT_TAB_CLASS = 52;
    private static final int[] ABILITY_SLOTS = {10, 11, 12, 13, 14};
    private static final int SLOT_INSTALL = 16;
    private static final int[] SPEC_SLOTS = {11, 15};
    private static final int SLOT_RESPEC = 22;
    private static final int[] PASSIVE_SLOTS = {10, 11, 12, 13};
    private static final int SLOT_CROWN = 15;
    /** 1.6.6: сводка резистов во вкладке «Класс». */
    private static final int SLOT_RESIST = 14;
    /** 1.7.0 пакет 1: сводка атрибутов во вкладке «Класс». */
    private static final int SLOT_ATTRIBUTES = 16;

    private Inventory inventory;
    private final UUID owner;
    private final Tab tab;

    private ClassBook(UUID owner, Tab tab) {
        this.owner = owner;
        this.tab = tab;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(RaskolClasses plugin, Player player, Tab tab) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-class", "Класс не выбран — посетите герольда"), NamedTextColor.GRAY));
            return;
        }
        ClassBook book = new ClassBook(player.getUniqueId(), tab);
        Inventory inv = Bukkit.createInventory(book, SIZE,
                Component.text(plugin.getRaskolConfig().message("book.title", "Книга класса: "))
                        .append(Component.text(pc.getDisplayName(), pc.getColor())));
        book.inventory = inv;
        book.fill(plugin, player, pc);
        player.openInventory(inv);
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        if (theme != null && theme.sound() != null) {
            plugin.getFx().playSound(player.getLocation(), theme.sound(), 0.5f, 1.1f);
        }
    }

    /** 1.5.5: перерисовать содержимое открытой книги без переоткрытия. */
    public void refresh(RaskolClasses plugin, Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null || inventory == null) {
            return;
        }
        fill(plugin, player, pc);
    }

    private void fill(RaskolClasses plugin, Player player, PlayerClass pc) {
        for (int i = 9; i < 36; i++) {
            inventory.setItem(i, filler());
        }
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler());
        }
        inventory.setItem(SLOT_EMBLEM, emblem(plugin, player, pc));
        inventory.setItem(SLOT_TAB_ABILITIES, tabIcon(plugin, Material.BOOK,
                "book.tab.abilities", "Способности", tab == Tab.ABILITIES));
        inventory.setItem(SLOT_TAB_SPECS, tabIcon(plugin, Material.NETHER_STAR,
                "book.tab.specs", "Специализации", tab == Tab.SPECS));
        inventory.setItem(SLOT_TAB_CLASS, tabIcon(plugin, Material.NAME_TAG,
                "book.tab.class", "Класс и пассивки", tab == Tab.CLASS));
        switch (tab) {
            case ABILITIES -> {
                for (int i = 0; i < ABILITY_SLOTS.length; i++) {
                    AbilityDef def = plugin.getAbilities().getBySlot(pc, i + 1);
                    if (def != null) {
                        inventory.setItem(ABILITY_SLOTS[i], abilityItem(plugin, player, pc, def));
                    }
                }
                InstallationType type = InstallationType.forClass(pc);
                if (type != null) {
                    inventory.setItem(SLOT_INSTALL, installItem(plugin, player, pc, type));
                }
            }
            case SPECS -> {
                List<Spec> specs = specsFor(pc);
                for (int i = 0; i < specs.size() && i < SPEC_SLOTS.length; i++) {
                    inventory.setItem(SPEC_SLOTS[i], specItem(plugin, player, pc, specs.get(i)));
                }
                inventory.setItem(SLOT_RESPEC, respecItem(plugin, player));
            }
            case CLASS -> {
                List<String> passives = RaskolConfig.passiveIds(pc);
                for (int i = 0; i < passives.size() && i < PASSIVE_SLOTS.length; i++) {
                    inventory.setItem(PASSIVE_SLOTS[i], passiveItem(plugin, pc, passives.get(i)));
                }
                inventory.setItem(SLOT_RESIST, resistItem(plugin, player, pc));
                inventory.setItem(SLOT_CROWN, crownItem(plugin, player, pc));
                // 1.7.0 пакет 1: сводка атрибутов
                inventory.setItem(SLOT_ATTRIBUTES, attributesItem(plugin, player, pc));
           
