// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.build.Archetype;
import dev.raskol.classes.spec.build.BuildTemplate;
import dev.raskol.classes.spec.build.BuildTemplates;
import dev.raskol.classes.spec.build.PlusSubscription;
import dev.raskol.classes.spec.service.Spec2Service;
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
import org.bukkit.inventory.InventoryHolder;
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
 * Экран 1 (CATALOG, 27 слотов):
 *   - слот 4: иконка статуса подписки (активна / не активна + цена);
 *   - слоты 11, 15: карточки PvP/PvE шаблонов текущей основной спеки;
 *   - слот 22: выход.
 *
 * Экран 2 (CONFIRM, 27 слотов):
 *   - слот 4: икона применяемого шаблона + лор (узлы/очки/патч);
 *   - слот 11: «✔ Применить» → после первого клика визуально переходит в состояние
 *     «⚠ Подтвердить применение» (1.14.16-fix: клик больше не выглядит мёртвым);
 *   - слот 15: «✖ Назад».
 *
 * Без подписки: карточки показывают тизер (имя/tagline/счётчики + «🔒 требуется
 * raskolspec+»), клик шлёт upsell-сообщение с ценой и путём покупки (тизер-модель
 * Dota Plus). Stale-шаблоны помечаются «⚠ обновляется после баланс-патча».
 *
 * 1.14.16 (Волна 15C): applyTemplate делегирует в Spec2Service.applyBuild
 * (пре-проверки parity/очки/экономика → списание респеца дерева → setRanks →
 * reconcile → защитный rollback). Админ-путь (raskolclasses.admin.plus) — бесплатно.
 *
 * 1.14.16-fix2 (КРИТИЧЕСКИЙ): onClose чистил pending/armed при закрытии ЛЮБОГО
 * PlusHolder-инвентаря. Открытие confirm-экрана (player.openInventory) сначала
 * закрывает каталог → InventoryCloseEvent каталога стирал pending → клики по
 * «Применить» молча игнорировались (p == null). Теперь состояние чистится ТОЛЬКО
 * при закрытии CONFIRM-экрана; переход каталог→confirm сохраняет pending.
 * Дополнительно: пустой pending в confirm больше не молчаливый return, а сообщение
 * «сессия истекла» (диагностика вместо тишины).
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

    /** Экран GUI — хранится в holder'е, клик маршрутизируется по нему. */
    public enum Screen { CATALOG, CONFIRM }

    /** Holder вместо парсинга title (паттерн FoliantService.FoliantHolder). */
    private static final class PlusHolder implements InventoryHolder {
        private final Screen screen;
        private Inventory inventory;

        PlusHolder(Screen screen) {
            this.screen = screen;
        }

        Screen screen() {
            return screen;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RaskolClasses plugin;
    private final Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, PendingApply> pending = new ConcurrentHashMap<>();

    public PlusGui(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ публичный API ------------------------------ */

    /** Открыть каталог-экран для основной спеки игрока. */
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

        PlusHolder holder = new PlusHolder(Screen.CATALOG);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text(plugin.getRaskolConfig()
                        .message("plus.gui.title", "⚜ raskolspec+"), NamedTextColor.GOLD));
        holder.inventory = inv;

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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PlusHolder holder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return; // клик в нижний инвентарь игрока — только блокировка
        }

        PlusSubscription sub = plugin.getPlusSubscription();
        boolean active = sub.active(player.getUniqueId());

        switch (holder.screen()) {
            case CATALOG -> {
                switch (slot) {
                    case SLOT_PVP -> handleCardClick(player, Archetype.PVP, active);
                    case SLOT_PVE -> handleCardClick(player, Archetype.PVE, active);
                    case SLOT_EXIT -> player.closeInventory();
                    default -> { /* pane/status — некликабельно */ }
                }
            }
            case CONFIRM -> handleConfirmClick(player, slot);
        }
    }

    /**
     * 1.14.16-fix2: чистим состояние ТОЛЬКО при закрытии CONFIRM-экрана.
     * Закрытие каталога (в т.ч. автоматическое при открытии confirm) состояние
     * НЕ трогает — иначе pending умирал до первого клика по «Применить».
     * Реальный выход игрока из GUI (ESC/клик мимо) закрывает именно текущий экран:
     * если это confirm — pending/armed очищаются; если каталог — очищать нечего.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof PlusHolder holder
                && holder.screen() == Screen.CONFIRM) {
            armedUntil.remove(player.getUniqueId());
            pending.remove(player.getUniqueId());
        }
    }

    private void handleCardClick(Player player, Archetype archetype, boolean active) {
        PlusSubscription sub = plugin.getPlusSubscription();
        if (!active) {
            // Тизер-модель: upsell-сообщение с ценой и путём покупки
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.no-sub",
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
        PlusHolder holder = new PlusHolder(Screen.CONFIRM);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text(plugin.getRaskolConfig()
                        .message("plus.gui.confirm-title", "⚜ Применить «{name}»?")
                        .replace("{name}", t.name()), NamedTextColor.GOLD));
        holder.inventory = inv;

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

    private void handleConfirmClick(Player player, int slot) {
        UUID uuid = player.getUniqueId();

        if (slot == CONFIRM_BACK) {
            pending.remove(uuid);
            armedUntil.remove(uuid);
            openCatalog(player);
            return;
        }
        if (slot != CONFIRM_APPLY) {
            return;
        }
        PendingApply p = pending.get(uuid);
        if (p == null) {
            // 1.14.16-fix2: диагностика вместо тишины (нормально недостижимо,
            // оставлено как страховка от будущих регрессий жизненного цикла pending)
            player.sendMessage(msg("Сессия применения истекла — открой шаблон заново из каталога"));
            openCatalog(player);
            return;
        }

        long now = System.currentTimeMillis();
        Long armed = armedUntil.get(uuid);
        if (armed == null || now > armed) {
            // Первый клик — взвод: ВИЗУАЛЬНАЯ обратная связь в самом GUI
            armedUntil.put(uuid, now + ARM_WINDOW_MS);
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof PlusHolder) {
                top.setItem(CONFIRM_APPLY, confirmArmedItem());
            }
            player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.gui.confirm-arm",
                            "Клик №1 — взвести, клик №2 (30 с) — применить")));
            return;
        }

        // Второй клик в окне — применение
        pending.remove(uuid);
        armedUntil.remove(uuid);
        applyTemplate(player, p);
    }

    /**
     * 1.14.16 (Волна 15C): реальное применение шаблона через Spec2Service.applyBuild.
     * Экономика: списывается только стоимость респеца дерева (админ — бесплатно).
     * Все отказы приходят ДО списания (инвариант applyBuild) и сопровождаются
     * сообщением в чат с числами (очки/монеты), чтобы игрок понимал причину.
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

        boolean free = player.hasPermission("raskolclasses.admin.plus");
        Spec2Service.BuildApplyOutcome out =
                plugin.getSpec2Service().applyBuild(player, t.spec(), t.ranks(), free);

        switch (out.result()) {
            case OK -> {
                player.sendMessage(msg(plugin.getRaskolConfig()
                        .message("plus.apply-ok",
                                "⚜ Шаблон «{name}» применён. Респец-плата: {cost} монет.")
                        .replace("{name}", t.name())
                        .replace("{cost}", free ? "0 (админ-путь)" : String.valueOf(out.cost()))));
                player.closeInventory();
            }
            case POOR -> player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-poor", "Не хватает монет на респец: нужно {cost}")
                    .replace("{cost}", String.valueOf(out.cost()))));
            case NO_ECONOMY -> player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-no-econ", "Экономика недоступна — применение отключено")));
            case NO_MAIN -> player.sendMessage(msg(plugin.getRaskolConfig()
                    .message("plus.apply-no-spec",
                            "Сначала выбери основную спеку в «Специализации»")));
            case NOT_ENOUGH_POINTS -> player.sendMessage(msg(
                    "Не хватает очков путей: нужно " + out.needPoints()
                            + ", доступно " + out.poolPoints() + " (после сброса дерева)"));
            case WRONG_CLASS_TREE, TREE_NOT_FOUND -> player.sendMessage(
                    msg("⚜ Шаблон недоступен для текущей спеки"));
            case TEMPLATE_INVALID -> player.sendMessage(
                    msg("⚜ Шаблон несовместим с правилами дерева (сообщи админу)"));
            case RATE_LIMITED -> player.sendMessage(msg("Слишком часто: повтори через секунду"));
            case PRUNED -> player.sendMessage(
                    msg("⚜ Применение отклонено сервером: изменения откачены. Сообщи админу."));
        }
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
        if (stale) {
            color = NamedTextColor.YELLOW;
        } else if (!active) {
            color = NamedTextColor.GRAY;
        } else {
            color = archetype == Archetype.PVP ? NamedTextColor.RED : NamedTextColor.GREEN;
        }

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
            // Тизер: существование меты видно, содержимое платно
            lore.add(Component.text("Узлов: " + t.nodeCount()
                    + " · Очков: " + t.spentPoints(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
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
                        Component.text("Клик №1 — взвести подтверждение", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Клик №2 (30 с) — применить шаблон", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)));
    }

    /** 1.14.16-fix: взведённое состояние кнопки применения — видимая обратная связь. */
    private ItemStack confirmArmedItem() {
        return item(Material.ORANGE_DYE,
                Component.text("⚠ Подтвердить применение", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Взведено: кликни ещё раз в течение 30 с",
                                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        Component.text("Билд дерева будет ЗАМЕНЁН шаблоном",
                                NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
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

    /** Pending-apply: спека + архетип выбранного шаблона. */
    private record PendingApply(String spec, Archetype arch) {
    }
}
