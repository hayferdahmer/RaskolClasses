// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.foliant;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.compat.AuthGate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;   // FIX 1.10.1: types.*, а не node.*
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.10.0: ФОЛИАНТ РАСКОЛА — переход Маг/Жрец → Чернокнижник.
 * 1.10.1-fix: импорт InheritanceNode из net.luckperms.api.node.types.
 *
 * Гейты: класс МАГ/ЖРЕЦ, уровень персонажа ≥40, спека сброшена, таланты сброшены, AuthGate.
 * Миграция: LP API (авто-создание class_warlock) → console-фолбэк; очистка
 * модификаторов/резистов/ресурса/пассивок; сгорание инсталляций; грант occult=10.
 * Фолиант сгорает ТОЛЬКО после успешного перехода.
 */
public final class FoliantService implements Listener {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private static final String WARLOCK_GROUP = "class_warlock";

    private static final class FoliantHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RaskolClasses plugin;
    private final NamespacedKey foliantKey;

    public FoliantService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.foliantKey = new NamespacedKey(plugin, "raskol_foliant");
    }

    /* -------------------------------- публичное API -------------------------------- */

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Фолиант Раскола", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Древний том, запечатанный кровью", NamedTextColor.GRAY));
        lore.add(Component.text("расколотого бога. Читается только", NamedTextColor.GRAY));
        lore.add(Component.text("Магом или Жрецом от 40 уровня.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("⚠ Переход необратим:", NamedTextColor.RED));
        lore.add(Component.text("  • Класс станет Чернокнижник", NamedTextColor.GRAY));
        lore.add(Component.text("  • Спека и таланты должны быть", NamedTextColor.GRAY));
        lore.add(Component.text("    сброшены ДО чтения", NamedTextColor.GRAY));
        lore.add(Component.text("  • Откроется дерево occult", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("ПКМ — прочесть страницу", NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(foliantKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean giveTo(Player player) {
        return player.getInventory().addItem(createItem()).isEmpty();
    }

    public boolean isFoliant(ItemStack item) {
        if (item == null || item.getType() != Material.WRITABLE_BOOK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(foliantKey, PersistentDataType.BYTE);
    }

    /* -------------------------------- гейты -------------------------------- */

    private TransitionResult checkGates(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return TransitionResult.NO_CLASS;
        }
        if (pc == PlayerClass.WARLOCK) {
            return TransitionResult.ALREADY_WARLOCK;
        }
        if (pc != PlayerClass.MAGE && pc != PlayerClass.PRIEST) {
            return TransitionResult.NOT_MAGE_OR_PRIEST;
        }
        if (plugin.getCharacterLevels().characterLevel(uuid) < 40) {
            return TransitionResult.TOO_LOW_LEVEL;
        }
        if (plugin.getSpecService().getSpec(uuid) != null) {
            return TransitionResult.SPEC_CHOSEN;
        }
        if (plugin.getTalentService().spentGlobal(uuid) > 0) {
            return TransitionResult.TALENTS_SPENT;
        }
        if (plugin.getConfig().getBoolean("compat.authme-gate", true)
                && !AuthGate.canAct(plugin, player)) {
            return TransitionResult.AUTH_GATE;
        }
        return TransitionResult.OK;
    }

    /* -------------------------------- переход -------------------------------- */

    private TransitionResult executeTransition(Player player) {
        UUID uuid = player.getUniqueId();

        if (!swapLuckPermsGroup(player)) {
            return TransitionResult.LP_FAILED;
        }

        plugin.getInstallations().removeAllOf(uuid);

        plugin.getAttributes().clear(uuid);
        plugin.getResists().clear(uuid);

        plugin.getResources().clear(uuid);
        if (plugin.getPassives() != null) {
            plugin.getPassives().clear(uuid);
        }

        grantOccultSkill(player, 10);

        plugin.getAttributes().invalidate(uuid);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PlayerClass now = plugin.getClassProvider().getClassOf(player);
            if (now != PlayerClass.WARLOCK) {
                plugin.getLogger().severe("Foliant: после миграции класс " + player.getName()
                        + " = " + now + " (ожидался WARLOCK). Проверь LP-группу "
                        + WARLOCK_GROUP + " и веса.");
            }
        });

        player.sendMessage(LEGACY.deserialize(plugin.getRaskolConfig().message(
                "foliant.transitioned", "§5§lФолиант сгорел. Ты — Чернокнижник.")));
        plugin.getFx().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.5f);
        plugin.getFx().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.5f);
        burstParticles(player);

        return TransitionResult.OK;
    }

    /* ------------------------------ LuckPerms-миграция ------------------------------ */

    private boolean swapLuckPermsGroup(Player player) {
        try {
            return swapViaApi(player.getUniqueId());
        } catch (Throwable t) {
            plugin.getLogger().warning("Foliant: LP API недоступен ("
                    + t.getClass().getSimpleName() + ") — фолбэк на console-команды");
            return swapViaConsole(player);
        }
    }

    private boolean swapViaApi(UUID uuid) {
        LuckPerms lp = LuckPermsProvider.get();
        if (lp.getGroupManager().getGroup(WARLOCK_GROUP) == null) {
            lp.getGroupManager().createAndLoadGroup(WARLOCK_GROUP).join();
            plugin.getLogger().info("Foliant: создана LP-группа " + WARLOCK_GROUP
                    + " (поставь weight 15 и префикс вручную, см. RUNBOOK X.1)");
        }
        User user = lp.getUserManager().loadUser(uuid).join();
        user.data().clear(NodeType.INHERITANCE, node -> {
            String group = node.getGroupName();
            return group != null && group.startsWith("class_");
        });
        user.data().add(InheritanceNode.builder(WARLOCK_GROUP).build());
        lp.getUserManager().saveUser(user).join();
        return true;
    }

    private boolean swapViaConsole(Player player) {
        String name = player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp creategroup " + WARLOCK_GROUP);
        for (PlayerClass pc : PlayerClass.values()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + name + " parent remove class_"
                            + pc.name().toLowerCase(Locale.ROOT));
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + name + " parent add " + WARLOCK_GROUP);
    }

    /* ------------------------------ грант occult ------------------------------ */

    private static final Object VOID_OK = new Object();

    private static Object invokeQuiet(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                continue;
            }
            try {
                Object r = m.invoke(target, args);
                return r != null ? r : VOID_OK;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Object invokeFirstExisting(Object target, String[] names, Object... args) {
        for (String n : names) {
            Object r = invokeQuiet(target, n, args);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private Object findOccultSkill(Object api) {
        Object registry = invokeFirstExisting(api,
                new String[]{"getGlobalRegistry", "getSkillRegistry", "getRegistry"});
        if (registry == null) {
            return null;
        }
        Object direct = invokeFirstExisting(registry,
                new String[]{"getSkill", "getSkillById", "getSkillByName"}, "occult");
        if (direct != null && direct != VOID_OK) {
            return direct;
        }
        Object collection = invokeQuiet(registry, "getSkills");
        if (collection instanceof Iterable<?> it) {
            for (Object sk : it) {
                Object id = invokeQuiet(sk, "getId");
                String idStr = id != null ? id.toString().toLowerCase(Locale.ROOT) : "";
                if (idStr.contains("occult")) {
                    return sk;
                }
            }
        }
        return null;
    }

    private void grantOccultSkill(Player player, int level) {
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object user = invokeQuiet(api, "getUser", player.getUniqueId());
            Object skill = findOccultSkill(api);
            if (user != null && skill != null && skill != VOID_OK) {
                Object done = invokeFirstExisting(user,
                        new String[]{"setSkillLevel", "setLevel", "addSkillLevel"}, skill, level);
                if (done != null) {
                    plugin.getLogger().info("Foliant: выдан occult=" + level
                            + " игроку " + player.getName() + " (AuraSkills API)");
                    return;
                }
            }
            if (skill == null || skill == VOID_OK) {
                plugin.getLogger().warning("Foliant: дерево occult не найдено в AuraSkills — "
                        + "добавь auraskills/occult в skills.yml (см. комплект 1.10.1)");
                return;
            }
        } catch (Throwable ignored) {
        }
        String cmd = plugin.getConfig().getString("foliant.occult-grant-command", "");
        if (cmd != null && !cmd.isEmpty()) {
            String exec = cmd.replace("{player}", player.getName())
                    .replace("{level}", String.valueOf(level));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exec);
            plugin.getLogger().info("Foliant: occult выдан командой: " + exec);
            return;
        }
        plugin.getLogger().warning("Foliant: не удалось выдать occult автоматически. "
                + "Варианты: (а) skills.yml с auraskills/occult; (б) ключ foliant.occult-grant-command; "
                + "(в) вручную. Игрок: " + player.getName());
    }

    private void burstParticles(Player player) {
        org.bukkit.Location loc = player.getLocation().add(0.0, 1.0, 0.0);
        try {
            player.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 60, 0.6, 0.6, 0.6, 0.1);
            player.getWorld().spawnParticle(Particle.SOUL, loc, 40, 0.8, 0.8, 0.8, 0.05);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 30, 0.5, 0.5, 0.5, 0.02);
        } catch (IllegalArgumentException e) {
            player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /* -------------------------------- GUI -------------------------------- */

    private void openConfirmGui(Player player) {
        FoliantHolder holder = new FoliantHolder();
        Inventory gui = Bukkit.createInventory(holder, 27,
                LEGACY.deserialize(plugin.getRaskolConfig().message(
                        "foliant.confirm-title", "§4§lФОЛИАНТ РАСКОЛА")));
        holder.inventory = gui;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        ItemStack yes = new ItemStack(Material.WRITTEN_BOOK);
        yes.editMeta(m -> {
            m.displayName(LEGACY.deserialize(plugin.getRaskolConfig().message(
                    "foliant.confirm-yes", "§c§lПрочесть страницу")));
            m.lore(List.of(
                    Component.text("Стать Чернокнижником", NamedTextColor.GRAY),
                    Component.text("необратимо.", NamedTextColor.GRAY)));
            m.addEnchant(Enchantment.LURE, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        gui.setItem(11, yes);

        ItemStack no = new ItemStack(Material.BARRIER);
        no.editMeta(m -> {
            m.displayName(LEGACY.deserialize(plugin.getRaskolConfig().message(
                    "foliant.confirm-no", "§7Отмена")));
            m.lore(List.of(
                    Component.text("Закрыть фолиант.", NamedTextColor.GRAY),
                    Component.text("Предмет не сгорит.", NamedTextColor.GRAY)));
        });
        gui.setItem(15, no);

        player.openInventory(gui);
    }

    private void consumeFoliant(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFoliant(item)) {
                player.getInventory().remove(item);
            }
        }
    }

    /* -------------------------------- события -------------------------------- */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!isFoliant(event.getItem()) || !event.getAction().isRightClick()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        TransitionResult gate = checkGates(player);
        if (gate != TransitionResult.OK) {
            player.sendMessage(Component.text(gate.message(plugin), NamedTextColor.RED));
            return;
        }
        openConfirmGui(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FoliantHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 11) {
            player.closeInventory();
            TransitionResult gate = checkGates(player);
            if (gate != TransitionResult.OK) {
                player.sendMessage(Component.text(gate.message(plugin), NamedTextColor.RED));
                return;
            }
            TransitionResult result = executeTransition(player);
            if (result == TransitionResult.OK) {
                consumeFoliant(player);
            } else {
                player.sendMessage(Component.text(result.message(plugin), NamedTextColor.RED));
            }
        } else if (slot == 15) {
            player.closeInventory();
            player.sendMessage(Component.text(
                    "Фолиант закрыт. Предмет остался в инвентаре.", NamedTextColor.GRAY));
        }
    }

    /* -------------------------------- результаты -------------------------------- */

    public enum TransitionResult {
        OK, LP_FAILED, NO_CLASS, ALREADY_WARLOCK, NOT_MAGE_OR_PRIEST,
        TOO_LOW_LEVEL, SPEC_CHOSEN, TALENTS_SPENT, AUTH_GATE;

        public String message(RaskolClasses plugin) {
            return switch (this) {
                case OK -> "Переход завершён.";
                case LP_FAILED -> "Ошибка миграции LuckPerms: группа " + WARLOCK_GROUP
                        + " не создана. Обратись к владельцу (см. лог).";
                case NO_CLASS -> plugin.getRaskolConfig().message(
                        "no-class", "Класс не выбран — посетите герольда");
                case ALREADY_WARLOCK -> plugin.getRaskolConfig().message(
                        "foliant.already-warlock", "Ты уже Чернокнижник");
                case NOT_MAGE_OR_PRIEST -> plugin.getRaskolConfig().message(
                        "foliant.not-mage-priest", "Фолиант может прочесть только Маг или Жрец");
                case TOO_LOW_LEVEL -> plugin.getRaskolConfig().message(
                        "foliant.too-low-level", "Для перехода нужен уровень персонажа 40");
                case SPEC_CHOSEN -> plugin.getRaskolConfig().message(
                        "foliant.spec-chosen",
                        "Сначала отрекись от пути: кристалл во вкладке «Специализации»");
                case TALENTS_SPENT -> plugin.getRaskolConfig().message(
                        "foliant.talents-spent",
                        "Сначала сбрось таланты: кристалл во вкладке «Таланты спеки»");
                case AUTH_GATE -> plugin.getRaskolConfig().message(
                        "gate.blocked", "Способности недоступны в этом режиме или до входа в аккаунт.");
            };
        }
    }
}
