// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.9.3: ХУК RaskolGear (без правок чужой репы): читает статы шмота из PDC
 * (namespace "raskolgear") и агрегирует per-player.
 *
 * 1.9.3.1 FIX (циклический softdepend RaskolClasses↔RaskolGear):
 *  - порядок enable не определён, поэтому стартовый лог печатается по PRESENCE
 *    (плагин загружен), а не по enabled;
 *  - реальная активация логируется на PluginEnableEvent(RaskolGear) + прогрев кэша
 *    всех онлайн-игроков; деактивация — на PluginDisableEvent;
 *  - isAvailable() остаётся динамической проверкой enabled (рантайм-гейт статов).
 *
 * Разделение ответственности (без двойного применения):
 *  - резисты шмота В БОЮ применяет сам RaskolGear; GearHook читает их для дисплея;
 *  - +HP шмота применяет RaskolClasses через AttributeService.maxHp (gearHp);
 *  - исходящий офенс WP/SP пропускается CombatService для оружия с тегом WEAPON.
 */
public final class GearHook implements Listener {

    public record GearStats(double phys, double magic, double hp, double reflect, boolean any) {
        public static final GearStats EMPTY = new GearStats(0, 0, 0, 0, false);
    }

    public record EquippedItem(ItemStack item, String className, String rarity, String slot) {
    }

    private final RaskolClasses plugin;
    private final Plugin gear;

    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kRarity;
    private final NamespacedKey kPhys;
    private final NamespacedKey kMagic;
    private final NamespacedKey kHp;
    private final NamespacedKey kReflect;
    private final NamespacedKey kSlot;

    private final Map<UUID, GearStats> cache = new ConcurrentHashMap<>();

    public GearHook(RaskolClasses plugin) {
        this.plugin = plugin;
        this.gear = plugin.getServer().getPluginManager().getPlugin("RaskolGear");
        kType = new NamespacedKey("raskolgear", "gear_type");
        kClass = new NamespacedKey("raskolgear", "gear_class");
        kRarity = new NamespacedKey("raskolgear", "gear_rarity");
        kPhys = new NamespacedKey("raskolgear", "phys_resist");
        kMagic = new NamespacedKey("raskolgear", "magic_resist");
        kHp = new NamespacedKey("raskolgear", "hp_bonus");
        kReflect = new NamespacedKey("raskolgear", "reflect");
        kSlot = new NamespacedKey("raskolgear", "armor_slot");
    }

    /** Плагин RaskolGear присутствует (загружен), независимо от enabled. */
    public boolean isPresent() {
        return gear != null;
    }

    /** Плагин присутствует И включён — стат-чтение разрешено. */
    public boolean isAvailable() {
        return gear != null && gear.isEnabled();
    }

    /** 1.9.3.1: стартовый лог по presence (enable может прийти позже из-за цикла softdepend). */
    public void logStartup() {
        if (gear == null) {
            plugin.getLogger().info("RaskolGear: не найден — хук отключён (gear-статы = 0)");
        } else if (gear.isEnabled()) {
            plugin.getLogger().info("RaskolGear: хук активен (статы шмота читаются из PDC)");
        } else {
            plugin.getLogger().info("RaskolGear: найден (v" + gear.getDescription().getVersion()
                    + ") — хук активируется после его включения (порядок softdepend)");
        }
    }

    /* -------------------------------- кэш -------------------------------- */

    public GearStats stats(UUID uuid) {
        GearStats cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        Player p = plugin.getServer().getPlayer(uuid);
        if (p == null || !isAvailable()) {
            return GearStats.EMPTY;
        }
        GearStats computed = compute(p);
        cache.put(uuid, computed);
        return computed;
    }

    public double physResist(UUID uuid) { return stats(uuid).phys(); }
    public double magicResist(UUID uuid) { return stats(uuid).magic(); }
    public double hpBonus(UUID uuid) { return stats(uuid).hp(); }
    public double reflect(UUID uuid) { return stats(uuid).reflect(); }
    public boolean hasGear(UUID uuid) { return stats(uuid).any(); }

    public void refresh(Player player) {
        if (!isAvailable()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        GearStats next = compute(player);
        GearStats prev = cache.put(uuid, next);
        if (prev == null || prev.hp() != next.hp()) {
            plugin.getAttributes().invalidate(uuid);
        }
    }

    private void refreshAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            refresh(p);
        }
    }

    /* ------------------------------- чтение PDC ------------------------------- */

    private GearStats compute(Player player) {
        double phys = 0.0;
        double magic = 0.0;
        double hp = 0.0;
        boolean any = false;
        Map<String, Integer> setCount = new HashMap<>();

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            PersistentDataContainer pdc = armorPdc(armor);
            if (pdc == null) {
                continue;
            }
            any = true;
            phys += pdc.getOrDefault(kPhys, PersistentDataType.DOUBLE, 0.0);
            magic += pdc.getOrDefault(kMagic, PersistentDataType.DOUBLE, 0.0);
            hp += pdc.getOrDefault(kHp, PersistentDataType.DOUBLE, 0.0);
            String cls = pdc.get(kClass, PersistentDataType.STRING);
            String rar = pdc.get(kRarity, PersistentDataType.STRING);
            if (cls != null && rar != null) {
                setCount.merge(cls + ":" + rar, 1, Integer::sum);
            }
        }

        double reflect = 0.0;
        if (isAvailable()) {
            for (Map.Entry<String, Integer> e : setCount.entrySet()) {
                if (e.getValue() < 4) {
                    continue;
                }
                String[] parts = e.getKey().split(":");
                var cfg = gear.getConfig();
                if (cfg == null) {
                    continue;
                }
                phys += cfg.getDouble("armor." + parts[0] + "." + parts[1] + ".set-bonus.phys-resist", 0.0);
                magic += cfg.getDouble("armor." + parts[0] + "." + parts[1] + ".set-bonus.magic-resist", 0.0);
                reflect = Math.max(reflect,
                        cfg.getDouble("armor." + parts[0] + "." + parts[1] + ".reflect", 0.0));
            }
        }
        return new GearStats(phys, magic, hp, reflect, any);
    }

    private PersistentDataContainer armorPdc(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!"ARMOR".equals(pdc.get(kType, PersistentDataType.STRING))) {
            return null;
        }
        return pdc;
    }

    /* -------------------------------- API для GUI -------------------------------- */

    public EquippedItem getEquippedWeapon(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null) {
            return null;
        }
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!"WEAPON".equals(pdc.get(kType, PersistentDataType.STRING))) {
            return null;
        }
        return new EquippedItem(weapon,
                pdc.get(kClass, PersistentDataType.STRING),
                pdc.get(kRarity, PersistentDataType.STRING),
                "mainhand");
    }

    public List<EquippedItem> getEquippedArmor(Player player) {
        List<EquippedItem> armor = new ArrayList<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!"ARMOR".equals(pdc.get(kType, PersistentDataType.STRING))) {
                continue;
            }
            armor.add(new EquippedItem(item,
                    pdc.get(kClass, PersistentDataType.STRING),
                    pdc.get(kRarity, PersistentDataType.STRING),
                    pdc.get(kSlot, PersistentDataType.STRING)));
        }
        return armor;
    }

    /* -------------------------------- события -------------------------------- */

    /** 1.9.3.1: активация хука, когда RaskolGear включился позже нас. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!"RaskolGear".equals(event.getPlugin().getName())) {
            return;
        }
        plugin.getLogger().info("RaskolGear: хук активирован (статы шмота читаются из PDC)");
        refreshAll();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!"RaskolGear".equals(event.getPlugin().getName())) {
            return;
        }
        plugin.getLogger().warning("RaskolGear: выключен — хук деактивирован (gear-статы = 0)");
        cache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> refresh(event.getPlayer()), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p) {
            plugin.getServer().getScheduler().runTask(plugin, () -> refresh(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
