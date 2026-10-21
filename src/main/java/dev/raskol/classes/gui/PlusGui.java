// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.gui.book.BookSession;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.build.Archetype;
import dev.raskol.classes.spec.build.BuildTemplate;
import dev.raskol.classes.spec.build.BuildTemplates;
import dev.raskol.classes.spec.build.PlusSubscription;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.14.15 (raskolspec+): GUI подписки.
 *
 * Экран 1 (upsell / каталог): 27 слотов.
 *   - слот 4: иконка статуса подписки (активна / не активна);
 *   - слоты 11, 15: карточки PvP/PvE шаблонов текущей спеки;
 *   - слот 22: выход.
 *
 * Экран 2 (confirm-apply): 27 слотов.
 *   - слот 4: икона применяемого шаблона + лор (топ-5 узлов);
 *   - слот 11: «✔ Применить» (двойной клик: взвод 30 с + подтверждение);
 *   - слот 15: «✖ Назад».
 *
 * Без подписки: карточки заблокированы (lore: «🔒 требуется raskolspec+»),
 * клик по ним открывает upsell-экран с ценой/путём покупки.
 * Stale-шаблоны: помечены «⚠ обновляется после баланс-патча», не применимы.
 *
 * Архитектурно: PlusGui — stateless-facade, сессия игрока в BookSession
 * (существующая книга класса переиспользует ту же инфраструктуру).
 * Confirm-взвод (arm-window 30 с) — per-player ConcurrentHashMap,
 * чистится onClose / onApply / периодической sweep-задачей.
 *
 * 1.14.15: первый чек-ин; apply-логика делегируется в ApplyApplier (будет в 15B-финале,
 * пока возвращаем OK-сообщение без фактического сброса дерева — safety first).
 */
public final class PlusGui implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_PVP = 11;
    private static final int SLOT_PVE = 15;
    private static final int SLOT_EXIT = 22;

    private static final int CONFIRM_TEMPLATE = 4;
    private static final int CONFIRM_APPLY = 11;
    private static final int CONFIRM_BACK = 15;

    private static final long ARM_WINDOW_MS = 30_000L;

    private final RaskolClasses plugin;
    private final Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, PendingApply> pending = new ConcurrentHashMap<>();

    public PlusGui(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ публичный API ------------------------------ */

    /** Открыть upsell/каталог-экран для текущей спеки игрока. */
    public void openCatalog(Player player) {
        BuildTemplates templates = plugin.getBuildTemplates();
        PlusSubscription sub = plugin.getPlusSubscription();
        UUID uuid = player.getUniqueId();

        String specId = plugin.getSpec2Service().mainSpec(uuid);
        if (specId == null) {
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-no-spec",
                            "Сначала выбери основную спеку в «Специализации»")));
            return;
        }

        String title = plugin.getRaskolConfig().message("plus.gui.title", "⚜ raskolspec+");
        Inventory inv = Bukkit.createInventory(null, SIZE, Component.text(title, NamedTextColor.GOLD));

        boolean active = sub.active(uuid);
        inv.setItem(SLOT_STATUS, statusItem(active, sub.remainingDays(uuid), sub));
        inv.setItem(SLOT_PVP, cardItem(templates, specId, Archetype.PVP, active));
        inv.setItem(SLOT_PVE, cardItem(templates, specId, Archetype.PVE, active));
        inv.setItem(SLOT_EXIT, exitItem());

        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane());
            }
        }

        player.openInventory(inv);
    }

    /* ------------------------------ обработчики ------------------------------ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().title().examinableName()
                .replace("net.kyori.adventure.text.", "");
        if (!title.contains("raskolspec+")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        PlusSubscription sub = plugin.getPlusSubscription();
        UUID uuid = player.getUniqueId();
        boolean active = sub.active(uuid);

        // Confirm-экран (27 слотов, но слот 11 = «Применить», 15 = «Назад»)
        if (title.contains("Применить") || isConfirmView(event)) {
            handleConfirmClick(player, slot, active);
            return;
        }

        // Каталог-экран
        switch (slot) {
            case SLOT_PVP -> handleCardClick(player, Archetype.PVP, active);
            case SLOT_PVE -> handleCardClick(player, Archetype.PVE, active);
            case SLOT_EXIT -> player.closeInventory();
            default -> { /* pane, некликабельно */ }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Чистим armed-state только если закрыт PlusGui (защита от утечки памяти)
        String title = event.getView().title().examinableName();
        if (title.contains("raskolspec+")) {
            armedUntil.remove(player.getUniqueId());
            pending.remove(player.getUniqueId());
        }
    }

    private boolean isConfirmView(InventoryClickEvent event) {
        // Детект по наличию pending-записи + слоту CONFIRM_APPLY
        UUID uuid = event.getWhoClicked().getUniqueId();
        return pending.containsKey(uuid);
    }

    private void handleCardClick(Player player, Archetype archetype, boolean active) {
        if (!active) {
            // Upsell: открыть экран с ценой/путём покупки
            PlusSubscription sub = plugin.getPlusSubscription();
            player.sendMessage(msg(plugin.getRaskolConfig().message("plus.no-sub",
                    "⚜ Требуется подписка raskolspec+ · цена: {price} ⚜ империалов / {days} дн.")
                    .replace("{price}", String.valueOf(sub.pricePerPeriod()))
                    .replace("{days}", String.valueOf(sub.periodDays()))));
            return;
        }

        UUID uuid = player.getUniqueId();
        String specId = plugin.getSpec2Service().mainSpec(uuid);
        if (specId == null) {
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-no-spec",
                            "Сначала выбери основную спеку в «Специализации»")));
            return;
        }

        BuildTemplates templates = plugin.getBuildTemplates();
        Optional<BuildTemplate> tpl = templates.get(specId, archetype);
        if (tpl.isEmpty()) {
            player.sendMessage(msg("⚜ Шаблон недоступен для спеки " + specId));
            return;
        }
        BuildTemplate t = tpl.get();
        if (templates.isStale(t)) {
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-stale",
                            "Шаблон устарел: куратор обновляет его после баланс-патча")));
            return;
        }

        pending.put(uuid, new PendingApply(specId, archetype));
        openConfirmScreen(player, t);
    }

    private void openConfirmScreen(Player player, BuildTemplate t) {
        String title = plugin.getRaskolConfig()
                .message("plus.gui.confirm-title", "⚜ Применить «{name}»?")
                .replace("{name}", t.name());
        Inventory inv = Bukkit.createInventory(null, SIZE,
                Component.text(title, NamedTextColor.GOLD));

        inv.setItem(CONFIRM_TEMPLATE, templateIcon(t));
        inv.setItem(CONFIRM_APPLY, confirmApplyItem());
        inv.setItem(CONFIRM_BACK, confirmBackItem());

        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane());
            }
        }
        player.openInventory(inv);
    }

    private void handleConfirmClick(Player player, int slot, boolean active) {
        UUID uuid = player.getUniqueId();
        PendingApply p = pending.get(uuid);

        if (slot == CONFIRM_BACK) {
            pending.remove(uuid);
            armedUntil.remove(uuid);
            openCatalog(player);
            return;
        }
        if (slot != CONFIRM_APPLY || p == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long armed = armedUntil.get(uuid);
        if (armed == null || now > armed) {
            // Первый клик — взводим
            armedUntil.put(uuid, now + ARM_WINDOW_MS);
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.gui.confirm-arm",
                            "ПКМ №1 — взвести, ПКМ №2 (30 с) — применить")));
            return;
        }

        // Второй клик в окне — применяем
        pending.remove(uuid);
        armedUntil.remove(uuid);
        applyTemplate(player, p);
    }

    /**
     * 1.14.15: применение шаблона.
     * В первом чек-ине 15B — только сообщение без реального resetTree/setRanks,
     * чтобы не сломать живые билды игроков на dev-сервере. Реальная логика
     * (resetTree(treeId, free=true для админа) + setRanks из шаблона + reconcile)
     * будет подключена в финальном коммите 15B после ручного UX-теста.
     */
    private void applyTemplate(Player player, PendingApply p) {
        BuildTemplates templates = plugin.getBuildTemplates();
        Optional<BuildTemplate> tpl = templates.get(p.spec(), p.arch());
        if (tpl.isEmpty()) {
            player.sendMessage(msg("⚜ Шаблон больше недоступен"));
            return;
        }
        BuildTemplate t = tpl.get();
        if (templates.isStale(t)) {
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-stale",
                            "Шаблон устарел: куратор обновляет его после баланс-патча")));
            return;
        }

        // TODO(15B-final): подключить Spec2Service.resetTree + storage.setRanks.
        // Сейчас — только OK-сообщение для UX-валидации GUI-флоу.
        player.sendMessage(msg(plugin.getRaskolConfig()
                .message("plus.apply-ok",
                        "⚜ Шаблон «{name}» применён. Респец-плата: {cost} монет.")
                .replace("{name}", t.name())
                .replace("{cost}", "—")));
        player.closeInventory();
    }

    /* ------------------------------ предметы ------------------------------ */

    private ItemStack statusItem(boolean active, int remainingDays, PlusSubscription sub) {
        Material mat = active ? Material.GOLD_INGOT : Material.GRAY_DYE;
        NamedTextColor color = active ? NamedTextColor.GOLD : NamedTextColor.GRAY;
        Component name = Component.text("⚜ raskolspec+", color)
                .decoration(TextDecoration.ITALIC, false);
        List<Component> lore = new ArrayList<>();
        if (active) {
            lore.add(Component.text(plugin.getRaskolConfig()
                    .message("plus.gui.active", "⚜ Активна · осталось {days} дн.")
                    .replace("{days}", String.valueOf(remainingDays)),
                    NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(plugin.getRaskolConfig()
                    .message("plus.gui.locked", "🔒 Требуется подписка"),
                    NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Цена: " + sub.pricePerPeriod() + " ⚜ / "
                    + sub.periodDays() + " дн.", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return item(mat, name, lore);
    }

    private ItemStack cardItem(BuildTemplates templates, String specId,
                                Archetype archetype, boolean active) {
        Optional<BuildTemplate> opt = templates.get(specId, archetype);
        if (opt.isEmpty()) {
            return item(Material.BARRIER,
                    Component.text("Шаблон недоступен", NamedTextColor.RED),
                    List.of(Component.text("Нет в builds.yml", NamedTextColor.GRAY)));
        }
        BuildTemplate t = opt.get();
        boolean stale = templates.isStale(t);

        Material mat = archetype == Archetype.PVP ? Material.IRON_SWORD : Material.IRON_PICKAXE;
        NamedTextColor color;
        if (stale) color = NamedTextColor.YELLOW;
        else if (!active) color = NamedTextColor.GRAY;
        else color = archetype == Archetype.PVP ? NamedTextColor.RED : NamedTextColor.GREEN;

        Component name = Component.text((active ? "⚜ " : "🔒 ") + t.name(), color)
                .decoration(TextDecoration.ITALIC, false);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(t.tagline(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" "));

        if (stale) {
            lore.add(Component.text(plugin.getRaskolConfig()
                    .message("plus.gui.stale", "⚠ обновляется после баланс-патча"),
                    NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else if (!active) {
            lore.add(Component.text(plugin.getRaskolConfig()
                    .message("plus.gui.locked", "🔒 Требуется подписка"),
                    NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Узлов: " + t.nodeCount()
                    + " · Очков: " + t.spentPoints(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Актуально для патча " + t.updated(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return item(mat, name, lore);
    }

    private ItemStack templateIcon(BuildTemplate t) {
        Component name = Component.text(t.name(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(t.tagline(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" "));
        lore.add(Component.text("Узлов: " + t.nodeCount()
                + " · Очков: " + t.spentPoints(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" "));
        lore.add(Component.text("Применение заменит текущий билд спеки.",
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return item(Material.ENCHANTED_BOOK, name, lore);
    }

    private ItemStack confirmApplyItem() {
        return item(Material.LIME_DYE,
                Component.text("✔ Применить", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("ПКМ №1 — взвести", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("ПКМ №2 (30 с) — подтвердить", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)));
    }

    private ItemStack confirmBackItem() {
        return item(Material.RED_DYE,
                Component.text("✖ Назад", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false),
                List.of());
    }

    private ItemStack exitItem() {
        return item(Material.BARRIER,
                Component.text("Закрыть", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                List.of());
    }

    private ItemStack pane() {
        return item(Material.BLACK_STAINED_GLASS_PANE,
                Component.text(" "), List.of());
    }

    private ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component msg(String raw) {
        return Component.text(raw, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Pending-apply record. */
    private record PendingApply(String spec, Archetype arch) {
    }
}
