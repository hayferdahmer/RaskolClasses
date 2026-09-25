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
 * 1.9.3: ХУК RaskolGear (softdepend, без правок чужой репы).
 * Читает статы шмота из PDC (namespace "raskolgear") и агрегирует per-player:
 * физ/маг резист, +HP, шипы (reflect, только полный сет 4/4), факт наличия шмота.
 *
 * Разделение ответственности (без двойного применения):
 *  - Резисты шмота В БОЮ применяет сам RaskolGear (его ArmorDefenseListener).
 *    GearHook только читает их для ДИСПЛЕЯ (/rc debug, Книга) и сет-подсчёта.
 *  - +HP шмота применяет RaskolClasses: GearHook.hpBonus добавляется в формулу
 *    AttributeService.maxHp (ванильный AttributeModifier от RaskolGear при этом
 *    нейтрализуется carrier-логикой HpBarService, чтобы не было двойного HP).
 *  - Исходящий офенс: CombatService пропускает надбавку WP/SP, если в руке
 *    оружие RaskolGear (его WeaponDamageListener уже посчитал base+Power×coeff).
 *
 * Кэш per-player обновляется на join/respawn/InventoryClick(MONITOR)/quit.
 *
 * 1.9.3-r2: добавлен API для GUI (getEquippedWeapon, getEquippedArmor, getActiveSets).
 */
public final class GearHook implements Listener {

    /** Агрегированные статы шмота игрока. */
    public record GearStats(double phys, double magic, double hp, double reflect, boolean any) {
        public static final GearStats EMPTY = new GearStats(0, 0, 0, 0, false);
    }

    /** Экипированный предмет (для GUI). */
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

    public boolean isAvailable() {
        return gear != null && gear.isEnabled();
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

    /** Пересчёт кэша + инвалидация атрибутов/HP (вызывается событиями). */
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
        String cls = pdc.get(kClass, PersistentDataType.STRING);
        String rar = pdc.get(kRarity, PersistentDataType.STRING);
        return new EquippedItem(weapon, cls, rar, "mainhand");
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
            String cls = pdc.get(kClass, PersistentDataType.STRING);
            String rar = pdc.get(kRarity, PersistentDataType.STRING);
            String slot = pdc.get(kSlot, PersistentDataType.STRING);
            armor.add(new EquippedItem(item, cls, rar, slot));
        }
        return armor;
    }

    /* -------------------------------- события -------------------------------- */

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
