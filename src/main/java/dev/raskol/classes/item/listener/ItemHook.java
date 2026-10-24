// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.listener;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.ItemStatSnapshot;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemStatAggregator;
import dev.raskol.classes.item.service.ItemSystem;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Equipment event bridge and precomputed immutable snapshots.
 * 1.15.1-D: декомпозиция и фиксы аудита:
 *  - UidRegistry вынесен в instance-класс (глобальная static-карта удалена);
 *  - equipped(player) кэшируется ОДИН раз на reconcile (ранее 4+ вызова, O(n²) на слоты);
 *  - SetBonusService.apply() возвращает новую map — itemOnly остаётся честной копией
 *    предметных статов для расчёта set-diff резистов/стихий.
 * Запреты экипировки (класс/спек/уровень) — в EquipGuard, этот класс только reconcile.
 */
public final class ItemHook implements Listener {
    private final RaskolClasses plugin;
    private final ItemSystem items;
    private final UidRegistry uidRegistry = new UidRegistry();
    private final NamespacedKey attackSpeedKey, movementSpeedKey, starterKitClassKey;
    private final Map<UUID, Long> hashes = new ConcurrentHashMap<>();
    private BukkitTask reconcileTask;

    public ItemHook(RaskolClasses plugin, ItemSystem items) {
        this.plugin = plugin;
        this.items = items;
        attackSpeedKey = new NamespacedKey(plugin, "item_attack_speed");
        movementSpeedKey = new NamespacedKey(plugin, "item_movement_speed");
        starterKitClassKey = new NamespacedKey(plugin, "starter_kit_class");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long period = Math.max(1, plugin.getConfig().getLong("item.reconcile-ticks", 20));
        reconcileTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(this::reconcileIfChanged), period, period);
    }

    public void stop() {
        if (reconcileTask != null) reconcileTask.cancel();
    }

    public void schedule(Player p) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) reconcile(p);
        });
    }

    public void reconcile(Player player) {
        UUID uuid = player.getUniqueId();
        long hash = equipmentHash(player);
        hashes.put(uuid, hash);
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        clearItemModifiers(uuid);
        if (pc == null) {
            uidRegistry.retain(uuid, Set.of());
            items.snapshot(uuid, ItemStatSnapshot.EMPTY);
            items.setBonuses().clear(uuid);
            plugin.getAttributes().invalidate(uuid);
            return;
        }
        deliverStarterKit(player, pc);
        int level = plugin.getCharacterLevels().characterLevel(uuid);
        String spec = plugin.getSpec2Service().mainSpec(uuid);

        // 1.15.1-D: единственный снимок экипировки на reconcile
        Map<ItemSlot, ItemStack> eq = equipped(player);
        Set<String> currentUids = new HashSet<>();
        for (ItemStack stack : eq.values()) {
            if (items.pdc().isItem(stack) && items.pdc().hasValidUid(stack)) {
                currentUids.add(items.pdc().uid(stack));
            }
        }
        uidRegistry.retain(uuid, currentUids);

        List<ItemDefinition> defs = new ArrayList<>();
        List<Map<ItemStat, Double>> rolled = new ArrayList<>();
        Set<String> unmet = new HashSet<>();
        ItemStack main = eq.get(ItemSlot.MAIN_HAND);
        for (Map.Entry<ItemSlot, ItemStack> entry : eq.entrySet()) {
            ItemStack stack = entry.getValue();
            ItemDefinition def = items.definitionOf(stack);
            if (def == null) continue;
            if (def.slot() != entry.getKey()) continue;
            String uid = items.pdc().uid(stack);
            if (uid != null) {
                UUID owner = uidRegistry.claim(uid, uuid);
                if (owner != null && !owner.equals(uuid)) {
                    plugin.getLogger().warning("Дубликат item_uid в экипировке игрока " + player.getName());
                    continue;
                }
            }
            if (!ItemStatAggregator.eligible(def, pc, spec, level)) {
                unmet.add(def.id());
                continue;
            }
            defs.add(def);
            rolled.add(items.pdc().affixes(stack, def));
        }

        Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
        for (int i = 0; i < defs.size(); i++) {
            boolean weaponSlot = defs.get(i).slot() == ItemSlot.MAIN_HAND;
            defs.get(i).baseStats().forEach((s, v) -> {
                if (s != ItemStat.ATTACK_DAMAGE || weaponSlot) stats.merge(s, v, Double::sum);
            });
            rolled.get(i).forEach((s, v) -> {
                if (s != ItemStat.ATTACK_DAMAGE || weaponSlot) stats.merge(s, v, Double::sum);
            });
        }
        Map<ItemStat, Double> itemOnly = new EnumMap<>(stats);
        // 1.15.1-D: apply возвращает новую map (item + set), вход не мутирует
        stats = items.setBonuses().apply(uuid, defs, stats);
        stats.replaceAll((s, v) -> Double.isFinite(v) ? v : 0.0);

        applySpeed(player, Attribute.ATTACK_SPEED, attackSpeedKey, stats.getOrDefault(ItemStat.ATTACK_SPEED, 0.0));
        applySpeed(player, Attribute.MOVEMENT_SPEED, movementSpeedKey, stats.getOrDefault(ItemStat.MOVEMENT_SPEED, 0.0));

        ItemDefinition weapon = items.definitionOf(main);
        String type = weapon == null ? "PHYSICAL"
                : (weapon.weaponType() == WeaponType.STAFF || weapon.weaponType() == WeaponType.WAND ? "MAGIC" : "PHYSICAL");
        items.snapshot(uuid, new ItemStatSnapshot(stats, unmet, weapon == null ? null : weapon.id(), type, hash));

        for (ItemSlot slot : ItemSlot.values()) {
            String source = "item:" + slot.name();
            ItemDefinition def = items.definitionOf(eq.get(slot));
            if (def == null || !ItemStatAggregator.eligible(def, pc, spec, level)) continue;
            Map<ItemStat, Double> values = new EnumMap<>(ItemStat.class);
            values.putAll(def.baseStats());
            values.putAll(items.pdc().affixes(eq.get(slot), def));
            double phys = values.getOrDefault(ItemStat.PHYS_RESIST, 0.0);
            double magic = values.getOrDefault(ItemStat.MAGIC_RESIST, 0.0);
            if (phys > 0 || magic > 0) plugin.getResists().addPermanentModifier(uuid, source, phys, magic);
            for (var school : dev.raskol.classes.combat.school.School.values()) {
                ItemStat stat = ItemStat.fromId("resist-" + school.id());
                double value = stat == null ? 0 : values.getOrDefault(stat, 0.0);
                if (value > 0) plugin.getCombat().elemental().addPermanent(uuid, source + ":" + school.id(), school, value);
            }
        }

        String setSource = "item:set-bonus";
        double setPhys = stats.getOrDefault(ItemStat.PHYS_RESIST, 0.0) - itemOnly.getOrDefault(ItemStat.PHYS_RESIST, 0.0);
        double setMagic = stats.getOrDefault(ItemStat.MAGIC_RESIST, 0.0) - itemOnly.getOrDefault(ItemStat.MAGIC_RESIST, 0.0);
        if (setPhys > 0 || setMagic > 0) plugin.getResists().addPermanentModifier(uuid, setSource, setPhys, setMagic);
        for (var school : dev.raskol.classes.combat.school.School.values()) {
            ItemStat stat = ItemStat.fromId("resist-" + school.id());
            if (stat == null) continue;
            double extra = stats.getOrDefault(stat, 0.0) - itemOnly.getOrDefault(stat, 0.0);
            if (extra > 0) plugin.getCombat().elemental().addPermanent(uuid, setSource + ":" + school.id(), school, extra);
        }

        if (plugin.getAttributes() != null) plugin.getAttributes().invalidate(uuid);
        if (plugin.getResists() != null) plugin.getResists().invalidate(uuid);
        if (plugin.getResources() != null) plugin.getResources().stateOf(uuid);
    }

    private void deliverStarterKit(Player player, PlayerClass playerClass) {
        if (!plugin.getConfig().getBoolean("item.starter-kit.enabled", false)) return;
        var pdc = player.getPersistentDataContainer();
        String delivered = pdc.get(starterKitClassKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (playerClass.name().equals(delivered)) return;
        String prefix = playerClass.name().toLowerCase(java.util.Locale.ROOT) + "_";
        for (ItemDefinition definition : items.registry().all()) {
            if (definition.id().startsWith(prefix)
                    && definition.rarity() == ItemRarity.COMMON && definition.itemLevel() == 20) {
                ItemStack starter = items.create(definition.id(), 1);
                player.getInventory().addItem(starter).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
        pdc.set(starterKitClassKey, org.bukkit.persistence.PersistentDataType.STRING, playerClass.name());
    }

    private static void applySpeed(Player player, Attribute attribute, NamespacedKey key, double percent) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.getModifiers().stream().filter(mod -> key.equals(mod.getKey())).toList()
                .forEach(instance::removeModifier);
        if (Double.isFinite(percent) && percent > 0) {
            instance.addModifier(new AttributeModifier(key, percent / 100.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    private void reconcileIfChanged(Player p) {
        long current = equipmentHash(p);
        if (!Long.valueOf(current).equals(hashes.get(p.getUniqueId()))) reconcile(p);
    }

    private static Map<ItemSlot, ItemStack> equipped(Player player) {
        Map<ItemSlot, ItemStack> map = new EnumMap<>(ItemSlot.class);
        map.put(ItemSlot.MAIN_HAND, player.getInventory().getItemInMainHand());
        map.put(ItemSlot.OFF_HAND, player.getInventory().getItemInOffHand());
        map.put(ItemSlot.HEAD, player.getInventory().getHelmet());
        map.put(ItemSlot.CHEST, player.getInventory().getChestplate());
        map.put(ItemSlot.LEGS, player.getInventory().getLeggings());
        map.put(ItemSlot.FEET, player.getInventory().getBoots());
        return map;
    }

    private long equipmentHash(Player player) {
        long h = 1;
        for (ItemStack item : equipped(player).values()) {
            h = 31 * h + (item == null ? 0 : item.hashCode());
            if (item != null && item.hasItemMeta()) {
                h = 31 * h + java.util.Objects.hashCode(items.pdc().itemId(item));
                h = 31 * h + java.util.Objects.hashCode(items.pdc().uid(item));
            }
            h = 31 * h + (item == null ? 0 : item.getAmount());
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        h = 31 * h + (pc == null ? 0 : pc.hashCode());
        h = 31 * h + plugin.getCharacterLevels().characterLevel(player.getUniqueId());
        h = 31 * h + java.util.Objects.hashCode(plugin.getSpec2Service().mainSpec(player.getUniqueId()));
        return h;
    }

    private void clearItemModifiers(UUID uuid) {
        for (ItemSlot slot : ItemSlot.values()) {
            String source = "item:" + slot.name();
            plugin.getResists().removeModifiersBySource(uuid, source);
            for (var school : dev.raskol.classes.combat.school.School.values()) {
                plugin.getCombat().elemental().removeBySource(uuid, source + ":" + school.id());
            }
        }
        plugin.getResists().removeModifiersBySource(uuid, "item:set-bonus");
        for (var school : dev.raskol.classes.combat.school.School.values()) {
            plugin.getCombat().elemental().removeBySource(uuid, "item:set-bonus:" + school.id());
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            for (Attribute attribute : List.of(Attribute.ATTACK_SPEED, Attribute.MOVEMENT_SPEED)) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.getModifiers().stream()
                            .filter(m -> attackSpeedKey.equals(m.getKey()) || movementSpeedKey.equals(m.getKey()))
                            .toList().forEach(instance::removeModifier);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent e) { schedule(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e) { schedule(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        reconcile(p);
        ItemStatSnapshot snapshot = items.snapshot(p.getUniqueId());
        double damage = snapshot.value(ItemStat.ATTACK_DAMAGE);
        if (damage > 0 && e.getProjectile() instanceof Projectile projectile) {
            items.pdc().writeShot(projectile, damage, snapshot.autoAttackType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void held(PlayerItemHeldEvent e) { schedule(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void swap(PlayerSwapHandItemsEvent e) { schedule(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void click(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p) schedule(p); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void drag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p) schedule(p); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void drop(PlayerDropItemEvent e) { schedule(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void broken(PlayerItemBreakEvent e) { schedule(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        hashes.remove(id);
        items.clear(id);
        items.setBonuses().clear(id);
        uidRegistry.release(id);
        clearItemModifiers(id);
    }
}
