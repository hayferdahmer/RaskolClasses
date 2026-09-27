// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.foliant;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.compat.AuthGate;
import dev.raskol.classes.storage.SafeStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;   // FIX 1.11.2: entity.*, а не player.*
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * 1.10.0: ФОЛИАНТ ДУШ (скрытый путь Чернокнижника).
 * 1.11.2 (S1): санитайзер ника в console-фолбэке LP-миграции (инъекции).
 * 1.11.2 (S2): дроп только с пиглинов ада и только при уроне игрока (getKiller);
 *         скрытый релок-цикл после выпадения: ≥1 моб вне ада + ≥1 смерть +
 *         ≥1000 пиглинов без шанса (foliant-lock.yml, персист).
 * 1.11.2 (S3): том не падает с игрока на смерть, не выбрасывается (Q),
 *         дроп-ролл идёт напрямую в инвентарь; наземный экземпляр (полный
 *         инвентарь) несёт PDC-владельца и поднимается только им.
 *         Продажа (аукцион/ChestShop) НЕ блокируется.
 * 1.11.2: авто-созданной группе class_warlock копируется вес класс-группы.
 * 1.11.2-fix: импорт PlayerDeathEvent из org.bukkit.event.entity.
 */
public final class FoliantService implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private static final String WARLOCK_GROUP = "class_warlock";
    private static final java.util.regex.Pattern SAFE_NAME =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    /** Скрытое состояние релок-цикла игрока. */
    private static final class RelockState {
        boolean locked;
        int outKills;
        int deaths;
        int noKills;
    }

    private static final class FoliantHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RaskolClasses plugin;
    private final NamespacedKey foliantKey;
    private final NamespacedKey ownerKey;

    private final Map<UUID, RelockState> locks = new ConcurrentHashMap<>();
    private final File lockFile;
    private final YamlConfiguration lockStore;
    private volatile boolean lockDirty = false;

    public FoliantService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.foliantKey = new NamespacedKey(plugin, "raskol_foliant");
        this.ownerKey = new NamespacedKey(plugin, "raskol_foliant_owner");
        this.lockFile = new File(plugin.getDataFolder(), "foliant-lock.yml");
        this.lockStore = SafeStorage.loadWithFallback(lockFile, LOGGER);
        loadLocks();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (lockDirty) {
                saveLocks();
            }
        }, 1200L, 1200L);
    }

    /* -------------------------------- персист релока -------------------------------- */

    private void loadLocks() {
        for (String key : lockStore.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                RelockState st = new RelockState();
                st.locked = lockStore.getBoolean(key + ".locked", false);
                st.outKills = lockStore.getInt(key + ".out-kills", 0);
                st.deaths = lockStore.getInt(key + ".deaths", 0);
                st.noKills = lockStore.getInt(key + ".no-kills", 0);
                if (st.locked) {
                    locks.put(uuid, st);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private synchronized void saveLocks() {
        for (Map.Entry<UUID, RelockState> e : locks.entrySet()) {
            String k = e.getKey().toString();
            lockStore.set(k + ".locked", e.getValue().locked);
            lockStore.set(k + ".out-kills", e.getValue().outKills);
            lockStore.set(k + ".deaths", e.getValue().deaths);
            lockStore.set(k + ".no-kills", e.getValue().noKills);
        }
        SafeStorage.saveAtomic(lockStore, lockFile, LOGGER);
        lockDirty = false;
    }

    private RelockState lockState(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new RelockState());
    }

    private boolean relockEnabled() {
        return plugin.getConfig().getBoolean("foliant.relock.enabled", true);
    }

    private void checkUnlock(Player p, RelockState st) {
        int needOut = Math.max(1, plugin.getConfig().getInt("foliant.relock.kills-outside-nether", 1));
        int needDeaths = Math.max(1, plugin.getConfig().getInt("foliant.relock.deaths", 1));
        int needNo = Math.max(1, plugin.getConfig().getInt("foliant.relock.piglin-kills-no-chance", 1000));
        if (st.outKills >= needOut && st.deaths >= needDeaths && st.noKills >= needNo) {
            st.locked = false;
            st.outKills = 0;
            st.deaths = 0;
            st.noKills = 0;
            saveLocks();
            LOGGER.info("Foliant: релок-цикл завершён игроком " + p.getName()
                    + " (шанс дропа восстановлен)");
        }
    }

    /* -------------------------------- предмет -------------------------------- */

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(plugin.getConfig().getString(
                "foliant.item-name", "§5§lФолиант Душ. Том I")));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Древний том, запечатанный кровью", NamedTextColor.GRAY));
        lore.add(Component.text("расколотого бога. Страницы шепчут", NamedTextColor.GRAY));
        lore.add(Component.text("тем, кто знает письмена света и веры.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Прочесть страницу может лишь Маг", NamedTextColor.DARK_PURPLE));
        lore.add(Component.text("или Жрец не ниже 40 уровня.", NamedTextColor.DARK_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text("ПКМ — открыть том", NamedTextColor.YELLOW));
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

    private String ownerOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    private void spillGround(Player p, ItemStack item) {
        ItemStack tagged = item.clone();
        tagged.editMeta(m -> m.getPersistentDataContainer().set(
                ownerKey, PersistentDataType.STRING, p.getUniqueId().toString()));
        Item dropped = p.getWorld().dropItem(p.getLocation(), tagged);
        dropped.setPickupDelay(20);
    }

    /* ------------------------------ дроп и релок (S2) ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getKiller(); // урон нанёс именно игрок
        if (killer == null) {
            return;
        }
        boolean nether = event.getEntity().getWorld().getEnvironment() == World.Environment.NETHER;
        List<String> mobs = plugin.getConfig().getStringList("foliant.drop-mobs");
        boolean eligibleMob = mobs.isEmpty() || mobs.contains(event.getEntity().getType().name());

        RelockState st = locks.get(killer.getUniqueId());
        if (st != null && st.locked) {
            if (relockEnabled()) {
                if (!nether) {
                    st.outKills++;
                } else if (eligibleMob) {
                    st.noKills++;
                }
                checkUnlock(killer, st);
                lockDirty = true;
            }
            return; // шанс заблокирован до конца цикла
        }

        if (!plugin.getConfig().getBoolean("foliant.drop-enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("foliant.drop-nether-only", true) && !nether) {
            return;
        }
        if (!eligibleMob) {
            return;
        }
        double chancePercent = plugin.getConfig().getDouble("foliant.drop-chance-percent", 0.001);
        if (chancePercent <= 0.0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) >= chancePercent) {
            return;
        }

        // Дроп: напрямую в инвентарь (S3); переполнение → наземный экземпляр с владельцем
        ItemStack item = createItem();
        Map<Integer, ItemStack> overflow = killer.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            spillGround(killer, overflow.values().iterator().next());
        }
        if (relockEnabled()) {
            RelockState fresh = lockState(killer.getUniqueId());
            fresh.locked = true;
            fresh.outKills = 0;
            fresh.deaths = 0;
            fresh.noKills = 0;
            saveLocks();
        }
        plugin.getFx().playSound(killer.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.8f);
        if (killer.getWorld() != null) {
            killer.getWorld().spawnParticle(Particle.SCULK_SOUL,
                    killer.getLocation().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.05);
        }
        LOGGER.info("Foliant: том выпал с " + event.getEntity().getType().name()
                + " игроку " + killer.getName());
    }

    /* ------------------------------ soulbound (S3) ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        RelockState st = locks.get(uuid);
        if (st != null && st.locked && relockEnabled()) {
            st.deaths++;
            checkUnlock(event.getEntity(), st);
            lockDirty = true;
        }
        if (plugin.getConfig().getBoolean("foliant.soulbound.keep-on-death", true)) {
            event.getDrops().removeIf(this::isFoliant);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!isFoliant(event.getItemDrop().getItemStack())) {
            return;
        }
        if (plugin.getConfig().getBoolean("foliant.soulbound.prevent-drop", true)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Том не выпускает себя из рук.", NamedTextColor.DARK_PURPLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("foliant.soulbound.owner-lock-pickup", true)) {
            return;
        }
        ItemStack it = event.getItem().getItemStack();
        if (!isFoliant(it)) {
            return;
        }
        String owner = ownerOf(it);
        if (owner != null && !owner.equals(p.getUniqueId().toString())) {
            event.setCancelled(true);
        }
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

        grantSorcerySkill(player, 10);

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
                "foliant.transitioned", "§5§lТом рассыпался пеплом. Что-то внутри тебя проснулось.")));
        plugin.getFx().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.5f);
        plugin.getFx().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.5f);
        burstParticles(player);

        return TransitionResult.OK;
    }

    /* ------------------------------ LuckPerms-миграция ------------------------------ */

    /** S1: ник из консоли/оффлайн-режима может содержать что угодно — фильтр. */
    public static boolean isSafeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

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
        ensureGroup(lp);
        User user = lp.getUserManager().loadUser(uuid).join();
        List<InheritanceNode> toRemove = new ArrayList<>();
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (node.getGroupName().startsWith("class_")) {
                toRemove.add(node);
            }
        }
        for (InheritanceNode node : toRemove) {
            user.data().remove(node);
        }
        user.data().add(InheritanceNode.builder(WARLOCK_GROUP).build());
        lp.getUserManager().saveUser(user).join();
        return true;
    }

    /** Создаёт class_warlock при отсутствии и копирует вес существующей класс-группы. */
    private void ensureGroup(LuckPerms lp) {
        Group group = lp.getGroupManager().getGroup(WARLOCK_GROUP);
        if (group != null) {
            return;
        }
        group = lp.getGroupManager().createAndLoadGroup(WARLOCK_GROUP).join();
        int weight = 15;
        for (String src : new String[]{"class_mage", "class_priest", "class_warrior"}) {
            Group s = lp.getGroupManager().getGroup(src);
            if (s != null && s.getWeight().isPresent()) {
                weight = s.getWeight().get();
                break;
            }
        }
        group.data().add(Node.builder("weight").value(String.valueOf(weight)).build());
        lp.getGroupManager().saveGroup(group).join();
        plugin.getLogger().info("Foliant: создана LP-группа " + WARLOCK_GROUP
                + " с весом " + weight);
    }

    private boolean swapViaConsole(Player player) {
        String name = player.getName();
        if (!isSafeName(name)) { // S1: защита от инъекций в консольные команды
            plugin.getLogger().severe("Foliant: небезопасный ник '" + name
                    + "' — console-миграция отклонена (используй LP API)");
            return false;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp creategroup " + WARLOCK_GROUP);
        for (PlayerClass pc : PlayerClass.values()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + name + " parent remove class_"
                            + pc.name().toLowerCase(Locale.ROOT));
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + name + " parent add " + WARLOCK_GROUP);
    }

    /* ------------------------------ грант sorcery ------------------------------ */

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

    private Object findSorcerySkill(Object api) {
        Object registry = invokeFirstExisting(api,
                new String[]{"getGlobalRegistry", "getSkillRegistry", "getRegistry"});
        if (registry == null) {
            return null;
        }
        Object direct = invokeFirstExisting(registry,
                new String[]{"getSkill", "getSkillById", "getSkillByName"}, "sorcery");
        if (direct != null && direct != VOID_OK) {
            return direct;
        }
        Object collection = invokeQuiet(registry, "getSkills");
        if (collection instanceof Iterable<?> it) {
            for (Object sk : it) {
                Object id = invokeQuiet(sk, "getId");
                String idStr = id != null ? id.toString().toLowerCase(Locale.ROOT) : "";
                if (idStr.contains("sorcery")) {
                    return sk;
                }
            }
        }
        return null;
    }

    private void grantSorcerySkill(Player player, int level) {
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Object api = apiClass.getMethod("get").invoke(null);
            Object user = invokeQuiet(api, "getUser", player.getUniqueId());
            Object skill = findSorcerySkill(api);
            if (user != null && skill != null && skill != VOID_OK) {
                Object done = invokeFirstExisting(user,
                        new String[]{"setSkillLevel", "setLevel", "addSkillLevel"}, skill, level);
                if (done != null) {
                    plugin.getLogger().info("Foliant: выдан sorcery=" + level
                            + " игроку " + player.getName() + " (AuraSkills API)");
                    return;
                }
            }
            if (skill == null || skill == VOID_OK) {
                plugin.getLogger().warning("Foliant: дерево sorcery не найдено в AuraSkills");
                return;
            }
        } catch (Throwable ignored) {
        }
        String cmd = plugin.getConfig().getString("foliant.sorcery-grant-command", "");
        if (cmd != null && !cmd.isEmpty()) {
            String exec = cmd.replace("{player}", player.getName())
                    .replace("{level}", String.valueOf(level));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exec);
            plugin.getLogger().info("Foliant: sorcery выдан командой: " + exec);
            return;
        }
        plugin.getLogger().warning("Foliant: не удалось выдать sorcery автоматически. Игрок: "
                + player.getName());
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
                LEGACY.deserialize(plugin.getConfig().getString(
                        "foliant.gui-title", "§5§lФолиант Душ · Том I")));
        holder.inventory = gui;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        ItemStack yes = new ItemStack(Material.WRITTEN_BOOK);
        yes.editMeta(m -> {
            m.displayName(LEGACY.deserialize(plugin.getConfig().getString(
                    "foliant.gui-yes", "§c§lПрочесть страницу")));
            m.lore(List.of(
                    Component.text("Открыть том и принять", NamedTextColor.GRAY),
                    Component.text("то, что он даст.", NamedTextColor.GRAY)));
            m.addEnchant(Enchantment.LURE, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        gui.setItem(11, yes);

        ItemStack no = new ItemStack(Material.BARRIER);
        no.editMeta(m -> {
            m.displayName(LEGACY.deserialize(plugin.getConfig().getString(
                    "foliant.gui-no", "§7Закрыть том")));
            m.lore(List.of(
                    Component.text("Том подождёт другого часа.", NamedTextColor.GRAY)));
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
                    "Том закрыт. Он останется ждать.", NamedTextColor.GRAY));
        }
    }

    /* -------------------------------- результаты -------------------------------- */

    public enum TransitionResult {
        OK, LP_FAILED, NO_CLASS, ALREADY_WARLOCK, NOT_MAGE_OR_PRIEST,
        TOO_LOW_LEVEL, SPEC_CHOSEN, TALENTS_SPENT, AUTH_GATE;

        public String message(RaskolClasses plugin) {
            return switch (this) {
                case OK -> "Переход завершён.";
                case LP_FAILED -> "Том не открылся: ошибка миграции (см. лог).";
                case NO_CLASS -> plugin.getRaskolConfig().message(
                        "no-class", "Класс не выбран — посетите герольда");
                case ALREADY_WARLOCK -> plugin.getRaskolConfig().message(
                        "foliant.already-warlock", "Том уже прочитан тобой.");
                case NOT_MAGE_OR_PRIEST -> plugin.getRaskolConfig().message(
                        "foliant.not-mage-priest",
                        "Страницы не отвечают тебе. Том ждёт знающих письмена света или веры.");
                case TOO_LOW_LEVEL -> plugin.getRaskolConfig().message(
                        "foliant.too-low-level",
                        "Том тяжёл для твоего разума: нужен уровень персонажа 40.");
                case SPEC_CHOSEN -> plugin.getRaskolConfig().message(
                        "foliant.spec-chosen",
                        "Том не откроется, пока ты держишься старого пути.");
                case TALENTS_SPENT -> plugin.getRaskolConfig().message(
                        "foliant.talents-spent",
                        "Том не откроется, пока в тебе живы старые знания.");
                case AUTH_GATE -> plugin.getRaskolConfig().message(
                        "gate.blocked", "Способности недоступны в этом режиме или до входа в аккаунт.");
            };
        }
    }
}
