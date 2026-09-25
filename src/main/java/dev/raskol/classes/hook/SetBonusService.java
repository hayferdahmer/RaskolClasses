// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.combat.ResistService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.9.3: Управление сет-бонусами RaskolGear.
 * Читает PDC брони, считает количество предметов каждого сета (className:rarity),
 * при 4/4 регистрирует модификаторы в AttributeService (HP) и ResistService (резисты).
 * Source модификаторов: "set-bonus-{className}-{rarity}".
 */
public final class SetBonusService implements Listener {

    /** Активный сет-бонус игрока. */
    public record ActiveSet(String className, String rarity, int count, boolean full) {
    }

    private final RaskolClasses plugin;
    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kRarity;

    private final Map<UUID, List<ActiveSet>> activeSets = new ConcurrentHashMap<>();

    public SetBonusService(RaskolClasses plugin) {
        this.plugin = plugin;
        kType = new NamespacedKey("raskolgear", "gear_type");
        kClass = new NamespacedKey("raskolgear", "gear_class");
        kRarity = new NamespacedKey("raskolgear", "gear_rarity");
    }

    /* -------------------------------- API -------------------------------- */

    public List<ActiveSet> getActiveSets(UUID uuid) {
        List<ActiveSet> cached = activeSets.get(uuid);
        return cached != null ? cached : List.of();
    }

    public boolean hasFullSet(UUID uuid) {
        for (ActiveSet set : getActiveSets(uuid)) {
            if (set.full()) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------- логика ------------------------------- */

    private void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> setCount = new HashMap<>();

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            PersistentDataContainer pdc = armorPdc(armor);
            if (pdc == null) {
                continue;
            }
            String cls = pdc.get(kClass, PersistentDataType.STRING);
            String rar = pdc.get(kRarity, PersistentDataType.STRING);
            if (cls != null && rar != null) {
                setCount.merge(cls + ":" + rar, 1, Integer::sum);
            }
        }

        List<ActiveSet> sets = new ArrayList<>();
        for (Map.Entry<String, Integer> e : setCount.entrySet()) {
            String[] parts = e.getKey().split(":");
            String cls = parts[0];
            String rar = parts[1];
            int count = e.getValue();
            boolean full = count >= 4;
            sets.add(new ActiveSet(cls, rar, count, full));

            String source = "set-bonus-" + cls + "-" + rar;
            plugin.getAttributes().removeModifiersBySource(uuid, source);
            plugin.getResists().removeModifiersBySource(uuid, source);

            if (full) {
                applySetBonus(player, cls, rar, source);
            }
        }
        activeSets.put(uuid, sets);
    }

    private void applySetBonus(Player player, String className, String rarity, String source) {
        UUID uuid = player.getUniqueId();
        GearHook gearHook = plugin.getGearHook();
        if (gearHook == null || !gearHook.isAvailable()) {
            return;
        }
        // Читаем сет-бонусы из конфига RaskolGear
        var cfg = plugin.getServer().getPluginManager().getPlugin("RaskolGear");
        if (cfg == null) {
            return;
        }
        double physBonus = cfg.getConfig().getDouble(
                "armor." + className + "." + rarity + ".set-bonus.phys-resist", 0.0);
        double magicBonus = cfg.getConfig().getDouble(
                "armor." + className + "." + rarity + ".set-bonus.magic-resist", 0.0);

        if (physBonus > 0.0 || magicBonus > 0.0) {
            plugin.getResists().addPermanentModifier(uuid, source, physBonus, magicBonus);
        }
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
}
