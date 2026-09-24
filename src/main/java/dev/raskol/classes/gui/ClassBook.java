// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.install.InstallationType;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecService;
import dev.raskol.classes.talent.TalentModel;
import dev.raskol.classes.talent.TalentService;
import dev.raskol.classes.talent.TalentsRegistry;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Книга класса (1.9.3-gui): ЕДИНАЯ мрачная строгая дизайн-система.
 *
 * Каркас (все вкладки одинаковы):
 *   Row0: panes + [4 ЭМБЛЕМА] + panes
 *   Row1-4: контент вкладки
 *   Row5: [45 Способности][46 Спеки][47 Класс][48 Таланты][49 Закрыть] + panes
 *
 * Правила системы:
 *  - Рамка/фон: BLACK_STAINED_GLASS_PANE (тёмная, строгая).
 *  - Навигация всегда видима → нет тупиков; активная вкладка = glow + §6, неактивная = §7.
 *  - Деструктив (респец / сброс талантов) ВСЕГДА в слоте 40.
 *  - Состояния предметов: готово = glow + §6/§a; кулдаун = §7 + §c; замок = §7/DARK_GRAY + §c;
 *    нет ресурса = §7 + §c. Никаких случайных цветов.
 *  - Лор-шаблон: desc(§7) → пусто → статы(§7 label: §f value) → пусто → hint(§e ЛКМ / §7 ПКМ).
 *  - Клики: ЛКМ = основное, ПКМ = вторичное, двойное ПКМ+30с = деструктив.
 *  - Таланты: инфо очков перенесено с 4 (где жила эмблема) на 22; эмблема больше не исчезает.
 */
public final class ClassBook implements InventoryHolder {

    public enum Tab { ABILITIES, SPECS, CLASS, TALENTS }

    private static final int SIZE = 54;
    private static final int SLOT_EMBLEM = 4;

    private static final int SLOT_TAB_ABILITIES = 45;
    private static final int SLOT_TAB_SPECS = 46;
    private static final int SLOT_TAB_CLASS = 47;
    private static final int SLOT_TAB_TALENTS = 48;
    private static final int SLOT_CLOSE = 49;

    private static final int[] ABILITY_SLOTS = {11, 12, 13, 14, 15};
    private static final int SLOT_INSTALL = 22;

    private static final int[] SPEC_SLOTS = {20, 24};
    private static final int SLOT_RESPEC = 40;

    private static final int[] PASSIVE_SLOTS = {29, 30, 31, 32};
    private static final int SLOT_ATTRIBUTES = 20;
    private static final int SLOT_RESIST = 22;
    private static final int SLOT_CROWN = 24;

    /** Порядок = порядок узлов TalentTree (ветви/тиры/капстоун/ульт). */
    private static final int[] TALENT_NODE_SLOTS = {2, 6, 10, 12, 14, 16, 20, 24, 31};
    private static final int SLOT_TALENT_INFO = 22;
    private static final int SLOT_TALENT_RESET = 40;
    private static final long RESET_ARM_MILLIS = 30_000L;

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

    /* --------------------------------- каркас --------------------------------- */

    private void fill(RaskolClasses plugin, Player player, PlayerClass pc) {
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler());
        }
        inventory.setItem(SLOT_EMBLEM, emblem(plugin, player, pc));
        inventory.setItem(SLOT_TAB_ABILITIES, tabIcon(plugin, Material.BOOK,
                "book.tab.abilities", "Способности", tab == Tab.ABILITIES));
        inventory.setItem(SLOT_TAB_SPECS, tabIcon(plugin, Material.NETHER_STAR,
                "book.tab.specs", "Специализации", tab == Tab.SPECS));
        inventory.setItem(SLOT_TAB_CLASS, tabIcon(plugin, Material.NAME_TAG,
                "book.tab.class", "Класс и пассивки", tab == Tab.CLASS));
        inventory.setItem(SLOT_TAB_TALENTS, tabIcon(plugin, Material.END_CRYSTAL,
                "book.tab.talents", "Таланты спеки", tab == Tab.TALENTS));
        inventory.setItem(SLOT_CLOSE, closeIcon(plugin));

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
                inventory.setItem(SLOT_ATTRIBUTES, attributesItem(plugin, player, pc));
                inventory.setItem(SLOT_RESIST, resistItem(plugin, player, pc));
                inventory.setItem(SLOT_CROWN, crownItem(plugin, player, pc));
            }
            case TALENTS -> fillTalents(plugin, player);
        }
    }

    /* ------------------------------ TALENTS ------------------------------ */

    private void fillTalents(RaskolClasses plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec == null) {
            ItemStack info = new ItemStack(Material.BARRIER);
            info.editMeta(meta -> {
                meta.displayName(Component.text("Таланты недоступны", NamedTextColor.RED));
                meta.lore(List.of(
                        Component.text("Сначала выбери специализацию", NamedTextColor.GRAY),
                        Component.text("во вкладке «Специализации» (уровень 40+)", NamedTextColor.GRAY)));
            });
            inventory.setItem(SLOT_TALENT_INFO, info);
            return;
        }
        TalentModel.TalentTree tree = TalentsRegistry.treeOf(spec.id());
        if (tree == null) {
            ItemStack info = new ItemStack(Material.BARRIER);
            info.editMeta(meta -> {
                meta.displayName(Component.text("Дерево не найдено", NamedTextColor.RED));
                meta.lore(List.of(Component.text("specId: " + spec.id(), NamedTextColor.GRAY)));
            });
            inventory.setItem(SLOT_TALENT_INFO, info);
            return;
        }
        TalentService talents = plugin.getTalentService();
        int available = talents.availablePoints(uuid, spec.id());
        List<String> owned = talents.purchased(uuid, spec.id());

        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        info.editMeta(meta -> {
            meta.displayName(Component.text("Таланты: " + spec.displayName(), NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
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
        inventory.setItem(SLOT_TALENT_INFO, info);

        List<TalentModel.TalentNode> nodes = tree.nodes();
        for (int i = 0; i < nodes.size() && i < TALENT_NODE_SLOTS.length; i++) {
            inventory.setItem(TALENT_NODE_SLOTS[i],
                    talentNodeItem(plugin, player, spec.id(), nodes.get(i), owned, available));
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
        inventory.setItem(SLOT_TALENT_RESET, reset);
    }

    private ItemStack talentNodeItem(RaskolClasses plugin, Player player, String specId,
                                     TalentModel.TalentNode node, List<String> owned, int available) {
        UUID uuid = player.getUniqueId();
        boolean isOwned = owned.contains(node.id());
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int gate = TalentModel.tierGate(node.tier(),
                plugin.getConfig().getInt("talents.start-level", 40));
        boolean tierOk = charLevel >= gate;
        boolean prereqOk = owned.containsAll(node.prereqs());
        boolean affordable = node.cost() <= available;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            NamedTextColor nameColor = isOwned ? NamedTextColor.GREEN
                    : (!tierOk || !prereqOk) ? NamedTextColor.DARK_GRAY
                    : affordable ? NamedTextColor.GOLD : NamedTextColor.RED;
            meta.displayName(Component.text(node.name(), nameColor));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(node.lore(), NamedTextColor.GRAY));
            lore.add(Component.text(describeEffect(node.effect()), NamedTextColor.WHITE));
            lore.add(Component.text("Тир " + node.tier() + " · цена " + node.cost() + " очк.",
                    NamedTextColor.GRAY));
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

    /* ------------------------------ остальные вкладки ------------------------------ */

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
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private static ItemStack tabIcon(RaskolClasses plugin, Material material,
                                     String key, String def, boolean active) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(msg(plugin, key, def),
                    active ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            if (active) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.lore(List.of(Component.text(msg(plugin, "book.tab.hint", "Клик — открыть вкладку"),
                    NamedTextColor.DARK_GRAY)));
        });
        return item;
    }

    private static ItemStack closeIcon(RaskolClasses plugin) {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> meta.displayName(Component.text("Закрыть", NamedTextColor.RED)));
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
                    "book.resource." + pc.name().toLowerCase(Locale.ROOT), resourceRulesDef(pc)),
                    NamedTextColor.GRAY));
            lore.add(Component.text(msg(plugin, "book.emblem.resource", "Ресурс сейчас: {value}/100")
                    .replace("{value}", String.valueOf(
                            (int) plugin.getResources().getValue(player.getUniqueId()))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.emblem.crown", "Корона: {name}")
                    .replace("{name}", plugin.getFlavorService()
                            .crownDisplayName(player.getUniqueId())),
                    NamedTextColor.GOLD));
            meta.lore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return item;
    }

    private static String resourceRulesDef(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "Ярость: −5/с вне боя; +10 за урон (нанёс/получил)";
            case HUNTER -> "Концентрация: +5/с вне боя; +4 за попадание в бою";
            case PRIEST -> "Свет: +2/с всегда; +5 за событие лечения";
            case MAGE -> "Мана: +1/1.5/2/2.5 в секунду по порогам 25/50/75";
            case ROGUE -> "Энергия: +10/с";
        };
    }

    private ItemStack resistItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        var rb = plugin.getResists().breakdown(uuid);
        ItemStack item = new ItemStack(Material.SHIELD);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(msg(plugin, "book.resist.title", "Сопротивления"),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin, "book.resist.phys", "Физ: {total}% (база {base}%)")
                    .replace("{total}", String.valueOf((int) rb.physicalTotal()))
                    .replace("{base}", String.valueOf((int) rb.basePhysical())),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.resist.magic", "Маг: {total}% (база {base}%)")
                    .replace("{total}", String.valueOf((int) rb.magicTotal()))
                    .replace("{base}", String.valueOf((int) rb.baseMagic())),
                    NamedTextColor.WHITE));
            lore.add(Component.empty());
            if (rb.active().isEmpty()) {
                lore.add(Component.text(msg(plugin, "book.resist.none",
                        "Активных модификаторов нет"), NamedTextColor.DARK_GRAY));
            } else {
                for (var m : rb.active()) {
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
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int topN = Math.max(1, plugin.getConfig().getInt("character-level.top-n", 5));
        int cap = (int) plugin.getConfig().getDouble("attributes.level-cap", 60.0);
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(
                    msg(plugin, "book.attributes.title", "Атрибуты класса"),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(msg(plugin, "book.attributes.char-level",
                    "Уровень персонажа: {value} (топ-{n} скиллов, кап {cap})")
                    .replace("{value}", String.valueOf(charLevel))
                    .replace("{n}", String.valueOf(topN))
                    .replace("{cap}", String.valueOf(cap)), NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text((main == AttributeType.STR ? "★ " : "  ")
                    + msg(plugin, "book.attributes.str", "СИЛА: {value}")
                    .replace("{value}", String.valueOf((int) str)), NamedTextColor.WHITE));
            lore.add(Component.text((main == AttributeType.AGI ? "★ " : "  ")
                    + msg(plugin, "book.attributes.agi", "ЛОВКОСТЬ: {value}")
                    .replace("{value}", String.valueOf((int) agi)), NamedTextColor.WHITE));
            lore.add(Component.text((main == AttributeType.INT ? "★ " : "  ")
                    + msg(plugin, "book.attributes.int", "ИНТЕЛЛЕКТ: {value}")
                    .replace("{value}", String.valueOf((int) intel)), NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text(msg(plugin, "book.attributes.hp", "Макс. HP: {value}")
                    .replace("{value}", String.valueOf((int) attrs.maxHp(uuid))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.attributes.dodge", "Уклонение: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f", eff[0])),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.attributes.parry", "Парирование: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f", eff[1])),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.attributes.crit-melee", "Крит мили: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f",
                            attrs.critMeleeChance(uuid))), NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.attributes.crit-spell", "Крит магии: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f",
                            attrs.critSpellChance(uuid))), NamedTextColor.WHITE));
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
            Component name = unlocked
                    ? TextFx.gradient("[" + def.slot() + "] " + def.displayName(),
                            cfg.themeOf(pc).primary(), cfg.themeOf(pc).secondary())
                    : Component.text("[" + def.slot() + "] " + def.displayName(), NamedTextColor.DARK_GRAY);
            meta.displayName(name);
            List<Component> lore = new ArrayList<>();
            String desc = cfg.abilityDescription(pc, def.id(), "");
            if (!desc.isEmpty()) {
                lore.add(Component.text(desc, NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Цена: ", NamedTextColor.GRAY)
                    .append(Component.text(def.cost() + " " + pc.getResourceName(),
                            resource >= def.cost() ? NamedTextColor.WHITE : NamedTextColor.RED)));
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
                    unlocked ? NamedTextColor.GRAY : NamedTextColor.RED));
            lore.add(Component.text(scrolls > 0
                    ? msg(plugin, "book.scroll.have", "Свиток: в инвентаре ({count})")
                            .replace("{count}", String.valueOf(scrolls))
                    : msg(plugin, "book.scroll.none", "Свиток: нет"),
                    scrolls > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            if (!unlocked) {
                lore.add(Component.text("Заблокировано до уровня " + def.unlockLevel(), NamedTextColor.RED));
            } else {
                lore.add(Component.text(msg(plugin, "book.use.left", "ЛКМ — применить"), NamedTextColor.YELLOW));
                lore.add(Component.text(msg(plugin, "book.use.right", "ПКМ — свиток в хотбар"), NamedTextColor.GRAY));
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
            lore.add(Component.text(msg(plugin,
                    "book.install.desc." + type.id().toLowerCase(Locale.ROOT), installDescDef(type)),
                    NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text(msg(plugin, "book.install.active", "Активно: {count}/2 · TTL {ttl} с")
                    .replace("{count}", String.valueOf(
                            plugin.getInstallations().countOf(player.getUniqueId())))
                    .replace("{ttl}", String.valueOf(
                            plugin.getConfig().getInt("installations.ttl-seconds", 60))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(scrolls > 0
                    ? msg(plugin, "book.scroll.have", "Свиток: в инвентаре ({count})")
                            .replace("{count}", String.valueOf(scrolls))
                    : msg(plugin, "book.scroll.none", "Свиток: нет"),
                    scrolls > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text(msg(plugin, "book.place.left", "ЛКМ — поставить здесь"), NamedTextColor.YELLOW));
            lore.add(Component.text(msg(plugin, "book.place.right", "ПКМ — свиток постановки"), NamedTextColor.GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private static String installDescDef(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> "Аура: Resistance I союзникам в радиусе 6 на 8 с";
            case BEAR_TRAP -> "Мина: Slowness VI 2 с + 3 урона шагнувшему врагу";
            case LIGHT_WARD -> "Зона: +2 HP/с союзникам в радиусе 4 на 6 с";
            case FROST_RUNE -> "Руна-зона 8 блоков 30 с: урон+замедление врагам с нарастанием; магу внутри +3 маны/с и ИНТ×2";
            case SMOKE_BOMB -> "Мина: Blindness 2 с врагам + Speed I себе 3 с";
        };
    }

    private ItemStack specItem(RaskolClasses plugin, Player player, PlayerClass pc, Spec spec) {
        var def = plugin.getSpecRegistry().get(spec);
        Spec current = plugin.getSpecService().getSpec(player.getUniqueId());
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.displayName(Component.text(spec.displayName(),
                    current == spec ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            List<Component> lore = new ArrayList<>();
            if (def != null) {
                lore.add(Component.text(msg(plugin, "book.spec.passive", "Пассив: {text}")
                        .replace("{text}", def.passiveDescription()), NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            if (current == spec) {
                lore.add(Component.text(msg(plugin, "book.spec.chosen", "Выбрана тобой"),
                        NamedTextColor.GREEN));
                lore.add(Component.text("Пассивка работает постоянно", NamedTextColor.DARK_GRAY));
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
                lore.add(Component.empty());
                lore.add(Component.text(msg(plugin, "book.respec.current", "Текущая спека: {name}")
                        .replace("{name}", current.displayName()), NamedTextColor.WHITE));
                lore.add(Component.text(msg(plugin, "book.respec.price", "Цена: {price} монет (сжигаются)")
                        .replace("{price}", String.valueOf(cost)), NamedTextColor.RED));
                lore.add(Component.empty());
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
                    NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(cfg.passiveDescription(pc, id, ""), NamedTextColor.GRAY));
            lore.add(Component.text(passiveNumbers(id), NamedTextColor.WHITE));
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
            lore.add(Component.empty());
            lore.add(Component.text(msg(plugin, "book.crown.crown", "Корона: {name}")
                    .replace("{name}", plugin.getFlavorService().crownDisplayName(uuid)),
                    NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.crown.titleline", "Титул: {name}")
                    .replace("{name}", title.isEmpty() ? "—" : title), NamedTextColor.WHITE));
            lore.add(Component.text(msg(plugin, "book.crown.aura",
                    "Аура-партикл видна союзникам и врагам"), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private static String passiveNumbers(String id) {
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

    /** Обработчик кликов книги (1.9.3-gui): единая семантика ЛКМ/ПКМ + деструктив. */
    public static final class ClickHandler implements Listener {

        private static final Map<UUID, Long> RESET_ARM = new ConcurrentHashMap<>();

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
            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == SLOT_TAB_ABILITIES) { open(plugin, player, Tab.ABILITIES); return; }
            if (slot == SLOT_TAB_SPECS) { open(plugin, player, Tab.SPECS); return; }
            if (slot == SLOT_TAB_CLASS) { open(plugin, player, Tab.CLASS); return; }
            if (slot == SLOT_TAB_TALENTS) { open(plugin, player, Tab.TALENTS); return; }

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
                            player.sendMessage(Component.text(
                                    "Спека уже выбрана: пассивка работает постоянно, свитков активок больше нет.",
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
                case TALENTS -> {
                    UUID uuid = player.getUniqueId();
                    if (slot == SLOT_TALENT_RESET && right) {
                        Long armed = RESET_ARM.get(uuid);
                        long now = System.currentTimeMillis();
                        if (armed == null || now - armed > RESET_ARM_MILLIS) {
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
                        book.refresh(plugin, player);
                        return;
                    }
                    int nodeIdx = indexOf(TALENT_NODE_SLOTS, slot);
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
                        book.refresh(plugin, player);
                    }
                }
                case CLASS -> {
                    // информационная вкладка — клики по контенту ничего не делают
                }
            }
        }
    }
}
