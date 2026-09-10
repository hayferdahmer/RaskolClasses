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
 * 1.7.0 пакет 1: предмет-эмблема атрибутов (слот 16 вкладки «Класс»).
 * 1.7.5: вкладка SPECS показывает только пассивную идентичность — строки
 * активки/цены/свитка удалены, выдача свитка активки убрана.
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
    private static final int SLOT_RESIST = 14;
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
                inventory.setItem(SLOT_ATTRIBUTES, attributesItem(plugin, player, pc));
            }
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

    private static String msg(RaskolClasses plugin, String key, String def) {
        return plugin.getRaskolConfig().message(key, def);
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

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private static ItemStack tabIcon(RaskolClasses plugin, Material material,
                                     String key, String def, boolean active) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(msg(plugin, key, def),
                    active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            if (active) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin, "book.tab.hint", "Клик — открыть вкладку"),
                    NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private static Material emblemMaterial(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> Material.IRON_SWORD;
            case HUNTER -> Material.BOW;
            case PRIEST -> Material.BELL;
            case MAGE -> Material.BLAZE_ROD;
            case ROGUE -> Material.SHEARS;
        };
    }

    private ItemStack emblem(RaskolClasses plugin, Player player, PlayerClass pc) {
        ItemStack item = new ItemStack(emblemMaterial(pc));
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(pc.getDisplayName(),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin,
                    "book.resource." + pc.name().toLowerCase(), resourceRulesDef(pc)),
                    NamedTextColor.GRAY));
            lore.add(Component.text(msg(plugin, "book.emblem.resource", "Ресурс сейчас: {value}/100")
                    .replace("{value}", String.valueOf(
                            (int) plugin.getResources().getValue(player.getUniqueId()))),
                    NamedTextColor.AQUA));
            lore.add(Component.text(msg(plugin, "book.emblem.crown", "Корона: {name}")
                    .replace("{name}", plugin.getFlavorService()
                            .crownDisplayName(player.getUniqueId())),
                    NamedTextColor.GOLD));
            meta.lore(lore);
        });
        return item;
    }

    private static String resourceRulesDef(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "Ярость: −5/с вне боя; +10 за урон (нанёс/получил)";
            case HUNTER -> "Концентрация: +5/с вне боя, 0 в бою";
            case PRIEST -> "Свет: +2/с всегда; +5 за событие лечения";
            case MAGE -> "Мана: +3/4/5/6 в секунду по порогам 25/50/75";
            case ROGUE -> "Энергия: +10/с";
        };
    }

    private ItemStack resistItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        ResistService.Breakdown rb = plugin.getResists().breakdown(uuid);
        ItemStack item = new ItemStack(Material.SHIELD);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(msg(plugin, "book.resist.title", "Сопротивления"),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin, "book.resist.phys", "Физ: {total}% (база {base}%)")
                    .replace("{total}", String.valueOf((int) rb.physicalTotal()))
                    .replace("{base}", String.valueOf((int) rb.basePhysical())),
                    NamedTextColor.GREEN));
            lore.add(Component.text(msg(plugin, "book.resist.magic", "Маг: {total}% (база {base}%)")
                    .replace("{total}", String.valueOf((int) rb.magicTotal()))
                    .replace("{base}", String.valueOf((int) rb.baseMagic())),
                    NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.text(""));
            if (rb.active().isEmpty()) {
                lore.add(Component.text(msg(plugin, "book.resist.none",
                        "Активных модификаторов нет"), NamedTextColor.DARK_GRAY));
            } else {
                for (ResistService.Modifier m : rb.active()) {
                    lore.add(Component.text(msg(plugin, "book.resist.mod",
                            "• {source}: +{phys} физ / +{magic} маг")
                            .replace("{source}", m.source())
                            .replace("{phys}", String.valueOf((int) m.physicalPct()))
                            .replace("{magic}", String.valueOf((int) m.magicPct())),
                            NamedTextColor.GRAY));
                }
            }
            lore.add(Component.text(msg(plugin, "book.resist.cap", "Кап: {cap}%")
                    .replace("{cap}", String.valueOf((int) plugin.getResists().cap())),
                    NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack attributesItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        AttributeService attrs = plugin.getAttributes();
        AttributeType main = attrs.mainOf(pc);
        double str = attrs.value(uuid, AttributeType.STR);
        double agi = attrs.value(uuid, AttributeType.AGI);
        double intel = attrs.value(uuid, AttributeType.INT);
        double[] eff = attrs.effectiveAvoidance(uuid);
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(
                    msg(plugin, "book.attributes.title", "Атрибуты класса"),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text((main == AttributeType.STR ? "★ " : "  ")
                    + msg(plugin, "book.attributes.str", "СИЛА: {value}")
                    .replace("{value}", String.valueOf((int) str)), NamedTextColor.RED));
            lore.add(Component.text((main == AttributeType.AGI ? "★ " : "  ")
                    + msg(plugin, "book.attributes.agi", "ЛОВКОСТЬ: {value}")
                    .replace("{value}", String.valueOf((int) agi)), NamedTextColor.GREEN));
            lore.add(Component.text((main == AttributeType.INT ? "★ " : "  ")
                    + msg(plugin, "book.attributes.int", "ИНТЕЛЛЕКТ: {value}")
                    .replace("{value}", String.valueOf((int) intel)), NamedTextColor.AQUA));
            lore.add(Component.text(""));
            lore.add(Component.text(msg(plugin, "book.attributes.hp", "Макс. HP: {value}")
                    .replace("{value}", String.valueOf((int) attrs.maxHp(uuid))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.attributes.dodge", "Уклонение: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f", eff[0])),
                    NamedTextColor.GREEN));
            lore.add(Component.text(msg(plugin, "book.attributes.parry", "Парирование: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f", eff[1])),
                    NamedTextColor.YELLOW));
            lore.add(Component.text(msg(plugin, "book.attributes.crit-melee", "Крит мили: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f",
                            attrs.critMeleeChance(uuid))), NamedTextColor.RED));
            lore.add(Component.text(msg(plugin, "book.attributes.crit-spell", "Крит магии: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f",
                            attrs.critSpellChance(uuid))), NamedTextColor.AQUA));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack abilityItem(RaskolClasses plugin, Player player, PlayerClass pc, AbilityDef def) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = player.getUniqueId();
        long remaining = plugin.getCooldowns().getRemainingMillis(uuid, def.id());
        double resource = plugin.getResources().getValue(uuid);
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        boolean unlocked = level == dev.raskol.classes.classsystem.SkillLevelProvider.NO_SKILL_SYSTEM
                || level >= def.unlockLevel();
        boolean ready = remaining <= 0 && resource >= def.cost() && unlocked;
        int scrolls = countAbilityScrolls(plugin, player, def.id());

        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient("[" + def.slot() + "] " + def.displayName(),
                    cfg.themeOf(pc).primary(), cfg.themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            String desc = cfg.abilityDescription(pc, def.id(), "");
            if (!desc.isEmpty()) {
                lore.add(Component.text(desc, NamedTextColor.WHITE));
            }
            lore.add(Component.text(msg(plugin, "book.cost", "Цена: {cost} {resource}")
                    .replace("{cost}", String.valueOf(def.cost()))
                    .replace("{resource}", pc.getResourceName()),
                    resource >= def.cost() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (remaining > 0) {
                lore.add(Component.text(msg(plugin, "book.recharging", "Перезарядка: {sec} с")
                        .replace("{sec}", String.valueOf(remaining / 1000L + 1)),
                        NamedTextColor.RED));
            } else {
                lore.add(Component.text(msg(plugin, "book.cooldown", "Кулдаун: {sec} с")
                        .replace("{sec}", String.valueOf(def.cooldownMillis() / 1000L)),
                        NamedTextColor.GRAY));
            }
            lore.add(Component.text(msg(plugin, "book.unlock", "Открытие: уровень {level}")
                    .replace("{level}", String.valueOf(def.unlockLevel())),
                    unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            double grantPhys = plugin.getConfig()
                    .getDouble("resist.grants." + def.id() + ".physical", 0.0);
            double grantMagic = plugin.getConfig()
                    .getDouble("resist.grants." + def.id() + ".magic", 0.0);
            if (grantPhys > 0.0) {
                int secs = cfg.durationSeconds(pc, def.id(), 0);
                lore.add(Component.text(msg(plugin, "book.resist.grant",
                        "Даёт: +{phys}% физрезиста на {sec} с")
                        .replace("{phys}", String.valueOf((int) grantPhys))
                        .replace("{sec}", String.valueOf(secs)), NamedTextColor.AQUA));
            }
            if (grantMagic > 0.0) {
                int secs = cfg.durationSeconds(pc, def.id(), 0);
                lore.add(Component.text(msg(plugin, "book.resist.grant-magic",
                        "Даёт: +{magic}% магрезиста на {sec} с")
                        .replace("{magic}", String.valueOf((int) grantMagic))
                        .replace("{sec}", String.valueOf(secs)), NamedTextColor.AQUA));
            }
            lore.add(Component.text(scrolls > 0
                    ? msg(plugin, "book.scroll.have", "Свиток: в инвентаре ({count})")
                            .replace("{count}", String.valueOf(scrolls))
                    : msg(plugin, "book.scroll.none", "Свиток: нет"),
                    scrolls > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text(msg(plugin, "book.use.left", "ЛКМ — применить"),
                    NamedTextColor.GREEN));
            lore.add(Component.text(msg(plugin, "book.use.right", "ПКМ — свиток в хотбар"),
                    NamedTextColor.YELLOW));
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
            lore.add(Component.text(msg(plugin,
                    "book.install.desc." + type.id(), installDescDef(type)), NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.install.active", "Активно: {count}/2 · TTL {ttl} с")
                    .replace("{count}", String.valueOf(
                            plugin.getInstallations().countOf(player.getUniqueId())))
                    .replace("{ttl}", String.valueOf(
                            plugin.getConfig().getInt("installations.ttl-seconds", 60))),
                    NamedTextColor.GRAY));
            lore.add(Component.text(scrolls > 0
                    ? msg(plugin, "book.scroll.have", "Свиток: в инвентаре ({count})")
                            .replace("{count}", String.valueOf(scrolls))
                    : msg(plugin, "book.scroll.none", "Свиток: нет"),
                    scrolls > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text(msg(plugin, "book.place.left", "ЛКМ — поставить здесь"),
                    NamedTextColor.GREEN));
            lore.add(Component.text(msg(plugin, "book.place.right", "ПКМ — свиток постановки"),
                    NamedTextColor.YELLOW));
            meta.lore(lore);
        });
        return item;
    }

    private static String installDescDef(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> "Аура: Resistance I союзникам в радиусе 6 на 8 с";
            case BEAR_TRAP -> "Мина: Slowness VI 2 с + 3 урона шагнувшему врагу";
            case LIGHT_WARD -> "Зона: +2 HP/с союзникам в радиусе 4 на 6 с";
            case FROST_RUNE -> "Мина: 4 урона + Slowness II 3 с врагам в радиусе 3";
            case SMOKE_BOMB -> "Мина: Blindness 2 с врагам + Speed I себе 3 с";
        };
    }

    /**
     * 1.7.5: спека = пассивная идентичность. Лор показывает только пассивку,
     * резист-грант Стража и статус выбора. Строки активки/цены/свитка удалены.
     */
    private ItemStack specItem(RaskolClasses plugin, Player player, PlayerClass pc, Spec spec) {
        SpecRegistry.SpecDef def = plugin.getSpecRegistry().get(spec);
        Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(spec.displayName(),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            if (def != null) {
                lore.add(Component.text(msg(plugin, "book.spec.passive", "Пассив: {text}")
                        .replace("{text}", def.passiveDescription()), NamedTextColor.WHITE));
                if (spec == Spec.GUARDIAN) {
                    double g = plugin.getConfig()
                            .getDouble("resist.specs.guardian.physical", 10.0);
                    lore.add(Component.text(msg(plugin, "book.resist.spec",
                            "Пассив: +{phys}% физрезиста постоянно")
                            .replace("{phys}", String.valueOf((int) g)),
                            NamedTextColor.AQUA));
                }
            }
            lore.add(Component.text(""));
            if (current == spec) {
                lore.add(Component.text(msg(plugin, "book.spec.chosen", "Выбрана тобой"),
                        NamedTextColor.GREEN));
                lore.add(Component.text("Пассивка работает постоянно",
                        NamedTextColor.DARK_GRAY));
            } else if (current == null) {
                lore.add(Component.text(msg(plugin, "book.spec.notchosen",
                        "Не выбрана · ПКМ — выбрать (уровень 40+)"), NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text(msg(plugin, "book.spec.other",
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
            meta.displayName(Component.text(msg(plugin, "book.respec.title", "Отречение от пути"),
                    NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            if (current == null) {
                lore.add(Component.text(msg(plugin, "book.respec.nospec",
                        "Спеки нет — отрекаться не от чего"), NamedTextColor.GRAY));
            } else {
                lore.add(Component.text(msg(plugin, "book.respec.current", "Текущая спека: {name}")
                        .replace("{name}", current.displayName()), NamedTextColor.WHITE));
                lore.add(Component.text(msg(plugin, "book.respec.price", "Цена: {price} монет (сжигаются)")
                        .replace("{price}", String.valueOf(cost)), NamedTextColor.RED));
                lore.add(Component.text(""));
                lore.add(Component.text(msg(plugin, "book.respec.hint",
                        "ПКМ №1 — взвести, ПКМ №2 (30 с) — отречься"), NamedTextColor.YELLOW));
            }
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack passiveItem(RaskolClasses plugin, PlayerClass pc, String id) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        item.editMeta(meta -> {
            meta.displayName(Component.text(cfg.passiveDisplayName(pc, id, id),
                    NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(cfg.passiveDescription(pc, id, ""), NamedTextColor.WHITE));
            lore.add(Component.text(passiveNumbers(pc, id), NamedTextColor.GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack crownItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        String title = plugin.getFlavorService().titleOf(uuid, pc);
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        item.editMeta(meta -> {
            meta.displayName(Component.text(msg(plugin, "book.crown.title", "Корона и титул"),
                    NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin, "book.crown.crown", "Корона: {name}")
                    .replace("{name}", plugin.getFlavorService().crownDisplayName(uuid)),
                    NamedTextColor.GOLD));
            lore.add(Component.text(msg(plugin, "book.crown.titleline", "Титул: {name}")
                    .replace("{name}", title.isEmpty() ? "—" : title), NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.crown.aura",
                    "Аура-партикл видна союзникам и врагам"), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private static String passiveNumbers(PlayerClass pc, String id) {
        return switch (id) {
            case "execute_passive" -> "20% шанс · ×3 · порог HP 20% · КД 6 с";
            case "predator" -> "порог HP 80% · ×1.2";
            case "grace" -> "×1.15 к исходящему лечению";
            case "mana_soaked" -> "порог маны 50 · −15% входящего урона";
            case "poisoned_blades" -> "30% шанс · Яд I 2 с · КД 3 с";
            case "sadism" -> "+3 урона со спины · КД 2 с";
            default -> "";
        };
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /** Обработчик кликов книги. 1.7.5: SPECS — только выбор и отречение. */
    public static final class ClickHandler implements Listener {

        private final RaskolClasses plugin;

        public ClickHandler(RaskolClasses plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ClassBook book)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            int slot = event.getRawSlot();
            if (slot != event.getSlot()) {
                return;
            }
            boolean left = event.getClick() == ClickType.LEFT;
            boolean right = event.getClick() == ClickType.RIGHT;
            if (!left && !right) {
                return;
            }
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            if (pc == null) {
                return;
            }
            if (slot == SLOT_TAB_ABILITIES) {
                open(plugin, player, Tab.ABILITIES);
                return;
            }
            if (slot == SLOT_TAB_SPECS) {
                open(plugin, player, Tab.SPECS);
                return;
            }
            if (slot == SLOT_TAB_CLASS) {
                open(plugin, player, Tab.CLASS);
                return;
            }
            switch (book.tab) {
                case ABILITIES -> {
                    int idx = indexOf(ABILITY_SLOTS, slot);
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
                                player.sendMessage(Component.text(msg(plugin,
                                        "book.msg.scroll.dup", "Свиток уже в инвентаре — дубль не выдан."),
                                        NamedTextColor.GRAY));
                            } else {
                                player.getInventory().addItem(plugin.getTokens().create(def, pc));
                                player.sendMessage(Component.text(msg(plugin,
                                        "book.msg.scroll.got", "Свиток получен: "), NamedTextColor.GRAY)
                                        .append(Component.text(def.displayName(), pc.getColor())));
                            }
                        }
                        book.refresh(plugin, player);
                        return;
                    }
                    if (slot == SLOT_INSTALL) {
                        InstallationType type = InstallationType.forClass(pc);
                        if (type == null) {
                            return;
                        }
                        if (left) {
                            plugin.getInstallations().tryPlace(player);
                        } else {
                            if (countInstallScrolls(plugin, player, type) > 0) {
                                player.sendMessage(Component.text(msg(plugin,
                                        "book.msg.scroll.dup", "Свиток уже в инвентаре — дубль не выдан."),
                                        NamedTextColor.GRAY));
                            } else {
                                player.getInventory().addItem(plugin.getInstallToken().create(type, pc));
                                player.sendMessage(Component.text(msg(plugin,
                                        "book.msg.scroll.got", "Свиток получен: "), NamedTextColor.GRAY)
                                        .append(Component.text(type.displayName(), pc.getColor())));
                            }
                        }
                        book.refresh(plugin, player);
                    }
                }
                case SPECS -> {
                    int idx = indexOf(SPEC_SLOTS, slot);
                    if (idx >= 0) {
                        List<Spec> specs = specsFor(pc);
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
                            // 1.7.5: свитков активок больше нет — информируем
                            player.sendMessage(Component.text(
                                    "Эта спека — твоя пассивная идентичность: её бонусы работают постоянно.",
                                    NamedTextColor.GRAY));
                        } else {
                            player.sendMessage(Component.text(msg(plugin,
                                    "book.msg.spec.other", "Спека уже выбрана: {name}. Отречение — кристалл ниже.")
                                    .replace("{name}", current.displayName()), NamedTextColor.RED));
                        }
                        book.refresh(plugin, player);
                        return;
                    }
                    if (slot == SLOT_RESPEC && right) {
                        SpecService service = plugin.getSpecService();
                        Spec current = service.getSpec(player.getUniqueId());
                        if (current == null) {
                            player.sendMessage(Component.text(msg(plugin,
                                    "book.msg.respec.none", "Спеки нет — отрекаться не от чего."),
                                    NamedTextColor.GRAY));
                            return;
                        }
                        SpecService.RespecResult result = service.confirmRespec(player);
                        if (result == SpecService.RespecResult.NOT_PENDING) {
                            service.requestRespec(player);
                            player.sendMessage(Component.text(msg(plugin,
                                    "book.msg.respec.arm", "Отречение взведено: ПКМ по кристаллу ещё раз в течение 30 с. Цена: {price} монет")
                                    .replace("{price}", String.valueOf(service.respecCost(player))),
                                    NamedTextColor.YELLOW));
                        } else if (result == SpecService.RespecResult.OK) {
                            player.sendMessage(Component.text(msg(plugin,
                                    "book.msg.respec.ok", "Путь сброшен. Выбери новую спеку."),
                                    NamedTextColor.GREEN));
                        } else if (result == SpecService.RespecResult.POOR) {
                            player.sendMessage(Component.text(msg(plugin,
                                    "book.msg.respec.poor", "Не хватает монет на отречение."),
                                    NamedTextColor.RED));
                        } else if (result == SpecService.RespecResult.NO_ECONOMY) {
                            player.sendMessage(Component.text(msg(plugin,
                                    "book.msg.respec.noecon", "Экономика недоступна — респец отключён."),
                                    NamedTextColor.RED));
                        }
                        book.refresh(plugin, player);
                    }
                }
                case CLASS -> {
                    // информационная вкладка — клики ничего не делают
                }
            }
        }
    }
}
