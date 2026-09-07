// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Книга класса (1.5.4): всё классовое в одном GUI вместо россыпи команд.
 * Вкладки: СПОСОБНОСТИ (ЛКМ — каст, ПКМ — свиток в хотбар; инсталляция там же),
 * СПЕЦИАЛИЗАЦИИ (ПКМ — выбор / свиток активки; кристалл ниже — респец с двойным
 * подтверждением), КЛАСС (пассивки, корона/титул).
 * Заменяет /rc bind, /rc respec и старое меню способностей.
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
                Component.text("Книга класса: ").append(Component.text(pc.getDisplayName(), pc.getColor())));
        book.inventory = inv;
        book.fill(plugin, player, pc);
        player.openInventory(inv);
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        if (theme != null && theme.sound() != null) {
            plugin.getFx().playSound(player.getLocation(), theme.sound(), 0.5f, 1.1f);
        }
    }

    private void fill(RaskolClasses plugin, Player player, PlayerClass pc) {
        for (int i = 9; i < 36; i++) {
            inventory.setItem(i, filler());
        }
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler());
        }
        inventory.setItem(SLOT_EMBLEM, emblem(plugin, player, pc));
        inventory.setItem(SLOT_TAB_ABILITIES, tabIcon(Material.BOOK, "Способности",
                tab == Tab.ABILITIES));
        inventory.setItem(SLOT_TAB_SPECS, tabIcon(Material.NETHER_STAR, "Специализации",
                tab == Tab.SPECS));
        inventory.setItem(SLOT_TAB_CLASS, tabIcon(Material.NAME_TAG, "Класс и пассивки",
                tab == Tab.CLASS));
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
                inventory.setItem(SLOT_CROWN, crownItem(plugin, player, pc));
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

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private static ItemStack tabIcon(Material material, String name, boolean active) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name,
                    active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            if (active) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Клик — открыть вкладку", NamedTextColor.DARK_GRAY));
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
            lore.add(Component.text(resourceRules(pc), NamedTextColor.GRAY));
            lore.add(Component.text("Ресурс сейчас: "
                    + (int) plugin.getResources().getValue(player.getUniqueId()) + "/100",
                    NamedTextColor.AQUA));
            lore.add(Component.text("Корона: "
                    + plugin.getFlavorService().crownDisplayName(player.getUniqueId()),
                    NamedTextColor.GOLD));
            meta.lore(lore);
        });
        return item;
    }

    private static String resourceRules(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "Ярость: −5/с вне боя; +10 за урон (нанёс/получил)";
            case HUNTER -> "Концентрация: +5/с вне боя, 0 в бою";
            case PRIEST -> "Свет: +2/с всегда; +5 за событие лечения";
            case MAGE -> "Мана: +3/4/5/6 в секунду по порогам 25/50/75";
            case ROGUE -> "Энергия: +10/с";
        };
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

        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient("[" + def.slot() + "] " + def.displayName(),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            String desc = cfg.abilityDescription(pc, def.id(), "");
            if (!desc.isEmpty()) {
                lore.add(Component.text(desc, NamedTextColor.WHITE));
            }
            lore.add(Component.text("Цена: " + def.cost() + " " + pc.getResourceName(),
                    resource >= def.cost() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (remaining > 0) {
                lore.add(Component.text("Перезарядка: " + (remaining / 1000L + 1) + " с",
                        NamedTextColor.RED));
            } else {
                lore.add(Component.text("Кулдаун: " + def.cooldownMillis() / 1000L + " с",
                        NamedTextColor.GRAY));
            }
            lore.add(Component.text("Открытие: уровень " + def.unlockLevel(),
                    unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text(""));
            lore.add(Component.text("ЛКМ — применить", NamedTextColor.GREEN));
            lore.add(Component.text("ПКМ — свиток в хотбар", NamedTextColor.YELLOW));
            meta.lore(lore);
            if (ready) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private ItemStack installItem(RaskolClasses plugin, Player player, PlayerClass pc, InstallationType type) {
        ItemStack item = new ItemStack(type.displayItem());
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient("⚙ " + type.displayName(),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(installDesc(type), NamedTextColor.WHITE));
            lore.add(Component.text("Активно: " + plugin.getInstallations().countOf(player.getUniqueId())
                    + "/2 · TTL 60 с", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("ЛКМ — поставить здесь", NamedTextColor.GREEN));
            lore.add(Component.text("ПКМ — свиток постановки", NamedTextColor.YELLOW));
            meta.lore(lore);
        });
        return item;
    }

    private static String installDesc(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> "Аура: Resistance I союзникам в радиусе 6 на 8 с";
            case BEAR_TRAP -> "Мина: Slowness VI 2 с + 3 урона шагнувшему врагу";
            case LIGHT_WARD -> "Зона: +2 HP/с союзникам в радиусе 4 на 6 с";
            case FROST_RUNE -> "Мина: 4 урона + Slowness II 3 с врагам в радиусе 3";
            case SMOKE_BOMB -> "Мина: Blindness 2 с врагам + Speed I себе 3 с";
        };
    }

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
                lore.add(Component.text("Пассив: " + def.passiveDescription(),
                        NamedTextColor.WHITE));
                lore.add(Component.text("Актив: " + def.activeDescription(),
                        NamedTextColor.WHITE));
                lore.add(Component.text("Цена актива: " + def.activeCost() + " рес · КД: "
                        + def.activeCooldown() + " с", NamedTextColor.AQUA));
            }
            lore.add(Component.text(""));
            if (current == spec) {
                lore.add(Component.text("Выбрана тобой", NamedTextColor.GREEN));
                lore.add(Component.text("ПКМ — свиток активки в хотбар", NamedTextColor.YELLOW));
            } else if (current == null) {
                lore.add(Component.text("Не выбрана · ПКМ — выбрать (уровень 40+)",
                        NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("Выбрана другая спека — отречение ниже",
                        NamedTextColor.RED));
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
            meta.displayName(Component.text("Отречение от пути", NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            if (current == null) {
                lore.add(Component.text("Спеки нет — отрекаться не от чего",
                        NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("Текущая спека: " + current.displayName(),
                        NamedTextColor.WHITE));
                lore.add(Component.text("Цена: " + cost + " монет (сжигаются)",
                        NamedTextColor.RED));
                lore.add(Component.text(""));
                lore.add(Component.text("ПКМ №1 — взвести, ПКМ №2 (30 с) — отречься",
                        NamedTextColor.YELLOW));
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
            meta.displayName(Component.text("Корона и титул", NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Корона: "
                    + plugin.getFlavorService().crownDisplayName(uuid), NamedTextColor.GOLD));
            lore.add(Component.text("Титул: " + (title.isEmpty() ? "—" : title),
                    NamedTextColor.WHITE));
            lore.add(Component.text("Аура-партикл видна союзникам и врагам",
                    NamedTextColor.DARK_GRAY));
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

    /** Обработчик кликов книги. */
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
                return; // клик по своему инвентарю
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
                            player.getInventory().addItem(plugin.getTokens().create(def, pc));
                            player.sendMessage(Component.text("Свиток получен: ", NamedTextColor.GRAY)
                                    .append(Component.text(def.displayName(), pc.getColor())));
                        }
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
                            player.getInventory().addItem(plugin.getInstallToken().create(type, pc));
                            player.sendMessage(Component.text("Свиток получен: ", NamedTextColor.GRAY)
                                    .append(Component.text(type.displayName(), pc.getColor())));
                        }
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
                            if (plugin.getSpecService().choose(player, spec)) {
                                open(plugin, player, Tab.SPECS);
                            }
                        } else if (current == spec) {
                            player.getInventory().addItem(plugin.getSpecToken().create(spec, pc));
                            player.sendMessage(Component.text("Свиток получен: ", NamedTextColor.GRAY)
                                    .append(Component.text(spec.displayName(), pc.getColor())));
                        } else {
                            player.sendMessage(Component.text("Спека уже выбрана: "
                                    + current.displayName() + ". Отречение — кристалл ниже.",
                                    NamedTextColor.RED));
                        }
                        return;
                    }
                    if (slot == SLOT_RESPEC && right) {
                        SpecService service = plugin.getSpecService();
                        Spec current = service.getSpec(player.getUniqueId());
                        if (current == null) {
                            player.sendMessage(Component.text("Спеки нет — отрекаться не от чего.",
                                    NamedTextColor.GRAY));
                            return;
                        }
                        SpecService.RespecResult result = service.confirmRespec(player);
                        if (result == SpecService.RespecResult.NOT_PENDING) {
                            service.requestRespec(player);
                            player.sendMessage(Component.text("Отречение взведено: ПКМ по кристаллу ещё раз в течение 30 с. Цена: "
                                    + service.respecCost(player) + " монет", NamedTextColor.YELLOW));
                        } else if (result == SpecService.RespecResult.OK) {
                            player.sendMessage(Component.text("Путь сброшен. Выбери новую спеку.",
                                    NamedTextColor.GREEN));
                            open(plugin, player, Tab.SPECS);
                        } else if (result == SpecService.RespecResult.POOR) {
                            player.sendMessage(Component.text("Не хватает монет на отречение.",
                                    NamedTextColor.RED));
                        } else if (result == SpecService.RespecResult.NO_ECONOMY) {
                            player.sendMessage(Component.text("Экономика недоступна — респец отключён.",
                                    NamedTextColor.RED));
                        }
                    }
                }
                case CLASS -> {
                    // информационная вкладка — клики ничего не делают
                }
            }
        }
    }
}
