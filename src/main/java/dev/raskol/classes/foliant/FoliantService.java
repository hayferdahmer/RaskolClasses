// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.foliant;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.talent.TalentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.10.0: Фолиант Раскола — скрытый механизм перехода Маг/Жрец → Чернокнижник.
 *
 * Флоу:
 *   1) Админ выдаёт предмет: /rc foliant give <ник>
 *   2) Игрок (только МАГ или ЖРЕЦ, уровень персонажа ≥40) кликает ПКМ по фолианту
 *   3) Открывается GUI двойного подтверждения: «Прочесть страницу» / «Отмена»
 *   4) При подтверждении:
 *      - смена LP-группы class_mage/class_priest → class_warlock
 *      - сброс спеки (возврат очков талантов в общий пул)
 *      - грант occult=10 в AuraSkills (если API доступен)
 *      - очистка инсталляций/дебаффов старой спеки
 *      - предмет сгорает, сообщение «Фолиант сгорел. Ты — Чернокнижник.»
 *
 * Гейты (все проверяются ДО GUI):
 *   - класс МАГ или ЖРЕЦ (не воин/охотник/разбойник/уже чернокнижник)
 *   - уровень персонажа ≥ 40 (топ-N сводных скиллов)
 *   - AuthGate.canAct (если включён)
 *   - игрок онлайн
 *
 * Защита от дубля: предмет удаляется из инвентаря при любом исходе (успех/отмена/закрытие GUI).
 */
public final class FoliantService implements Listener {

    /** Тег PDC, по которому отличаем фолиант от обычного WRITABLE_BOOK. */
    private static final String PDC_KEY = "raskol_foliant";

    /** Ключ GUI-инвентаря для отличия от других инвентарей. */
    private static final String GUI_TITLE_RAW = "§4§lФОЛИАНТ РАСКОЛА";

    private final RaskolClasses plugin;
    private final NamespacedKey foliantKey;

    /** Игроки с открытым GUI (чтобы не обрабатывать случайные клики). */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public FoliantService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.foliantKey = new NamespacedKey(plugin, PDC_KEY);
    }

    /* -------------------------------- публичное API -------------------------------- */

    /** Создать предмет Фолианта Раскола (не кладёт в инвентарь — возвращает ItemStack). */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Фолиант Раскола", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("§7Древний том, запечатанный кровью", NamedTextColor.GRAY));
        lore.add(Component.text("§7расколотого бога. Читается только", NamedTextColor.GRAY));
        lore.add(Component.text("§7Магом или Жрецом от 40 уровня.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("§c§l⚠ Переход необратим:", NamedTextColor.RED));
        lore.add(Component.text("§7  • Класс станет §5Чернокнижник§7", NamedTextColor.GRAY));
        lore.add(Component.text("§7  • Спека будет сброшена", NamedTextColor.GRAY));
        lore.add(Component.text("§7  • Очки талантов вернутся в пул", NamedTextColor.GRAY));
        lore.add(Component.text("§7  • Открыть occult и начать заново", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("§eПКМ — прочесть страницу", NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(foliantKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Положить фолиант в инвентарь игрока. false — нет места. */
    public boolean giveTo(Player player) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(createItem());
        return overflow.isEmpty();
    }

    /** true, если ItemStack — фолиант. */
    public boolean isFoliant(ItemStack item) {
        if (item == null || item.getType() != Material.WRITABLE_BOOK || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(foliantKey, PersistentDataType.BYTE);
    }

    /** Программный переход (для /rc debug simulate или внешнего API). Возвращает результат. */
    public TransitionResult transition(Player player, boolean bypassGates) {
        if (!bypassGates) {
            TransitionResult gate = checkGates(player);
            if (gate != TransitionResult.OK) {
                return gate;
            }
        }
        return executeTransition(player);
    }

    /* -------------------------------- гейты -------------------------------- */

    private TransitionResult checkGates(Player player) {
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
        int charLevel = plugin.getCharacterLevels().characterLevel(player.getUniqueId());
        if (charLevel < 40) {
            return TransitionResult.TOO_LOW_LEVEL;
        }
        if (plugin.getConfig().getBoolean("compat.authme-gate", true)
                && !dev.raskol.classes.compat.AuthGate.canAct(plugin, player)) {
            return TransitionResult.AUTH_GATE;
        }
        return TransitionResult.OK;
    }

    /* -------------------------------- переход -------------------------------- */

    private TransitionResult executeTransition(Player player) {
        UUID uuid = player.getUniqueId();

        // 1) Смена LP-группы: class_mage/class_priest → class_warlock
        boolean lpOk = swapLuckPermsGroup(player);

        // 2) Сброс спеки и возврат очков талантов
        Spec currentSpec = plugin.getSpecService().getSpec(uuid);
        if (currentSpec != null) {
            // Тихий сброс: bypass-флаг true обходит стоимость
            TalentService.ResetResult resetResult =
                    plugin.getTalentService().reset(player, true);
            plugin.getSpecService().forceReset(uuid);
            plugin.getTalentService().resetSpecTree(uuid, currentSpec.id());
            if (resetResult != TalentService.ResetResult.OK
                    && resetResult != TalentService.ResetResult.NO_PURCHASED) {
                plugin.getLogger().warning("Foliant: сброс талантов " + uuid
                        + " вернул " + resetResult);
            }
        }

        // 3) Очистка инсталляций
        plugin.getInstallations().removeAllOf(uuid);

        // 4) Очистка атрибутов-модификаторов и резистов (старые гранты)
        plugin.getAttributes().clear(uuid);
        plugin.getResists().clear(uuid);

        // 5) Грант occult=10 в AuraSkills (если API доступен)
        grantOccultSkill(player, 10);

        // 6) Очистка кастомных данных
        plugin.getCooldowns().clear(uuid);
        plugin.getResources().clear(uuid);
        if (plugin.getEffects() != null) {
            plugin.getEffects().clear(uuid);
        }

        // 7) Сброс маркеров пассивок
        if (plugin.getPassives() != null) {
            plugin.getPassives().clear(uuid);
        }

        // 8) Инвалидация кэшей атрибутов и синхронизация carrier
        plugin.getAttributes().invalidate(uuid);

        // 9) Сообщения
        String msg = plugin.getRaskolConfig().message("foliant.transitioned",
                "§5§lФолиант сгорел. Ты — Чернокнижник.");
        player.sendMessage(parseLegacy(msg));
        plugin.getFx().playSound(player.getLocation(),
                org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.5f);
        plugin.getFx().playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.5f);
        burstParticles(player);

        // 10) Обновление провайдера класса
        plugin.getClassProvider().invalidate(uuid);

        return lpOk ? TransitionResult.OK : TransitionResult.LP_FAILED;
    }

    /** Смена LP-группы через exec-команду (надёжнее прямого API — работает всегда). */
    private boolean swapLuckPermsGroup(Player player) {
        String name = player.getName();
        // Снять старые class_* группы
        for (PlayerClass pc : PlayerClass.values()) {
            String group = "class_" + pc.name().toLowerCase(Locale.ROOT);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + name + " parent remove " + group);
        }
        // Поставить новую
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + name + " parent add class_warlock");
        if (!ok) {
            plugin.getLogger().severe("Foliant: не удалось выдать class_warlock игроку " + name);
        }
        return ok;
    }

    /** Попытка выставить уровень occult=10 через AuraSkills API (безопасно — try/catch). */
    private void grantOccultSkill(Player player, int level) {
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object user = apiClass.getMethod("getUser", UUID.class)
                    .invoke(api, player.getUniqueId());
            Object registry = apiClass.getMethod("getSkillRegistry").invoke(api);
            Object skill = registry.getClass().getMethod("getSkill", String.class)
                    .invoke(registry, "occult");
            if (skill != null) {
                user.getClass().getMethod("setSkillLevel", skill.getClass(), int.class)
                        .invoke(user, skill, level);
                plugin.getLogger().info("Foliant: выдан occult=" + level
                        + " игроку " + player.getName());
            }
        } catch (ReflectiveOperationException e) {
            // AuraSkills отсутствует или API изменился — молча пропускаем,
            // игрок сможет прокачать occult вручную через /sk
            plugin.getLogger().info("Foliant: AuraSkills API недоступен ("
                    + e.getClass().getSimpleName() + "), occult не выдан автоматически.");
        }
    }

    private void burstParticles(Player player) {
        org.bukkit.Location loc = player.getLocation().add(0, 1, 0);
        try {
            player.getWorld().spawnParticle(org.bukkit.Particle.SCULK_SOUL, loc,
                    60, 0.6, 0.6, 0.6, 0.1);
            player.getWorld().spawnParticle(org.bukkit.Particle.SOUL, loc,
                    40, 0.8, 0.8, 0.8, 0.05);
            player.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, loc,
                    30, 0.5, 0.5, 0.5, 0.02);
        } catch (IllegalArgumentException ignored) {
            // fallback: любой доступный партикл
            player.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 5, 0.3, 0.3, 0.3, 0.0);
        }
    }

    private Component parseLegacy(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(text);
    }

    /* -------------------------------- GUI -------------------------------- */

    private void openConfirmGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
                parseLegacy(plugin.getRaskolConfig().message(
                        "foliant.confirm-title", GUI_TITLE_RAW)));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // Слот 11 — «Прочесть страницу»
        ItemStack yes = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta yesMeta = yes.getItemMeta();
        yesMeta.displayName(parseLegacy(plugin.getRaskolConfig().message(
                "foliant.confirm-yes", "§c§lПрочесть страницу")));
        yesMeta.lore(List.of(
                Component.text("§7Стать Чернокнижником", NamedTextColor.GRAY),
                Component.text("§7необратимо.", NamedTextColor.GRAY)));
        yesMeta.addEnchant(Enchantment.LURE, 1, true);
        yesMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        yes.setItemMeta(yesMeta);
        gui.setItem(11, yes);

        // Слот 15 — «Отмена»
        ItemStack no = new ItemStack(Material.BARRIER);
        ItemMeta noMeta = no.getItemMeta();
        noMeta.displayName(parseLegacy(plugin.getRaskolConfig().message(
                "foliant.confirm-no", "§7Отмена")));
        noMeta.lore(List.of(
                Component.text("§7Закрыть фолиант.", NamedTextColor.GRAY),
                Component.text("§7Предмет не сгорит.", NamedTextColor.GRAY)));
        no.setItemMeta(noMeta);
        gui.setItem(15, no);

        pending.add(player.getUniqueId());
        player.openInventory(gui);
    }

    private boolean isFoliantGui(Inventory inv) {
        if (inv == null) {
            return false;
        }
        Component title = inv.getTitle();
        if (title == null) {
            return false;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(title);
        return plain.contains("ФОЛИАНТ РАСКОЛА");
    }

    /** Удалить все фолианты из инвентаря игрока. */
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
        ItemStack item = event.getItem();
        if (!isFoliant(item)) {
            return;
        }
        if (!event.getAction().isRightClick()) {
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isFoliantGui(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 11) {
            // «Прочесть страницу»
            player.closeInventory();
            consumeFoliant(player);
            TransitionResult result = transition(player, false);
            if (result != TransitionResult.OK && result != TransitionResult.LP_FAILED) {
                // Гейт сломался между открытием GUI и кликом (маловероятно, но на всякий случай)
                player.sendMessage(Component.text(result.message(plugin), NamedTextColor.RED));
                // Возвращаем фолиант
                giveTo(player);
            }
        } else if (slot == 15) {
            // «Отмена»
            player.closeInventory();
            player.sendMessage(Component.text("Фолиант закрыт. Предмет остался в инвентаре.",
                    NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isFoliantGui(event.getInventory())) {
            return;
        }
        pending.remove(player.getUniqueId());
    }

    /* -------------------------------- enum результатов -------------------------------- */

    public enum TransitionResult {
        OK,
        LP_FAILED,
        NO_CLASS,
        ALREADY_WARLOCK,
        NOT_MAGE_OR_PRIEST,
        TOO_LOW_LEVEL,
        AUTH_GATE;

        public String message(RaskolClasses plugin) {
            return switch (this) {
                case OK -> "Переход завершён.";
                case LP_FAILED -> "Переход завершён, но LP-группа не сменилась (проверь консоль).";
                case NO_CLASS -> plugin.getRaskolConfig().message(
                        "no-class", "Класс не выбран — посетите герольда");
                case ALREADY_WARLOCK -> plugin.getRaskolConfig().message(
                        "foliant.already-warlock", "Ты уже Чернокнижник");
                case NOT_MAGE_OR_PRIEST -> plugin.getRaskolConfig().message(
                        "foliant.not-mage-priest", "Фолиант может прочесть только Маг или Жрец");
                case TOO_LOW_LEVEL -> plugin.getRaskolConfig().message(
                        "foliant.too-low-level", "Для перехода нужен уровень персонажа 40");
                case AUTH_GATE -> plugin.getRaskolConfig().message(
                        "gate.blocked", "Способности недоступны в этом режиме или до входа в аккаунт.");
            };
        }
    }
}
