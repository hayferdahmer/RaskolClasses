// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.install.InstallationType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Таск отображения перезарядки на свитках в хотбаре (1.5.6 → 1.9.0-fix6).
 *
 * 1.9.0-fix6 (баг «КД не виден на свитке»): после правки меты предмет явно
 * возвращается в инвентарь через inv.setItem(slot, item) — в Paper 1.21
 * Inventory#getItem может отдавать зеркало, и правка меты без setItem терялась.
 *
 * Покрывает оба типа свитков: способности (CooldownManager) и инсталляции
 * (InstallationService.placeCooldownRemaining, включая Ледяную руну).
 * Строка лоры «⏳ Перезарядка: N с» обновляется только при смене секунд.
 */
public final class ScrollCooldownTask {

    private static final String CD_PREFIX = "⏳";

    private final RaskolClasses plugin;
    /** playerUUID → (slot → последние отображённые секунды; -1 = строки нет). */
    private final Map<UUID, Map<Integer, Long>> shown = new ConcurrentHashMap<>();

    public ScrollCooldownTask(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Map<Integer, Long> slots = shown.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            var inv = player.getInventory();

            for (int slot = 0; slot < 36; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null) {
                    continue;
                }
                long remaining = -1L;
                String abilityId = plugin.getTokens().readId(item);
                if (abilityId != null && pc != null) {
                    AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
                    if (def != null) {
                        remaining = plugin.getCooldowns().getRemainingMillis(uuid, abilityId);
                    }
                } else {
                    InstallationType type = plugin.getInstallToken().readType(item);
                    if (type != null) {
                        remaining = plugin.getInstallations().placeCooldownRemaining(uuid, type);
                    }
                }
                if (remaining < 0L) {
                    continue;
                }
                long seconds = remaining > 0L ? (remaining / 1000L) + 1L : 0L;
                Long last = slots.get(slot);
                long lastVal = last == null ? -1L : last;
                if (lastVal == seconds) {
                    continue;
                }
                slots.put(slot, seconds);
                if (applyLore(item, seconds)) {
                    // 1.9.0-fix6: явно кладём предмет обратно — правка меты не теряется
                    inv.setItem(slot, item);
                }
            }

            slots.keySet().removeIf(slot -> {
                ItemStack it = inv.getItem(slot);
                if (it == null) {
                    return true;
                }
                return plugin.getTokens().readId(it) == null
                        && plugin.getInstallToken().readType(it) == null;
            });
        }
        shown.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    /** true, если мета реально изменена (нужен setItem). */
    private boolean applyLore(ItemStack item, long seconds) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        boolean had = lore.removeIf(ScrollCooldownTask::isCooldownLine);
        if (seconds > 0) {
            lore.add(Component.text(CD_PREFIX + " Перезарядка: " + seconds + " с", NamedTextColor.RED));
            meta.lore(lore);
            item.setItemMeta(meta);
            return true;
        }
        if (had) {
            meta.lore(lore);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    private static boolean isCooldownLine(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        return plain.startsWith(CD_PREFIX);
    }
}
