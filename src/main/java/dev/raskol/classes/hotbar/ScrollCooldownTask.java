// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.install.InstallationType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Таск индикации перезарядки на свитках в хотбаре (1.5.6 → 1.9.0-fix7).
 *
 * 1.9.0-fix7 (жалобы «рука дёргается», «КД не видно на свитке»):
 *  - индикация = ПОЛОСА ПРОЧНОСТИ (durability bar) на предмете: видна в хотбаре
 *    без наведения, как было в ранних версиях;
 *  - лор больше НЕ перезаписывается каждый тик (это и вызывало дёргание руки);
 *  - предмет в руке обновляется только через setItemMeta (полоса), без setItem —
 *    клиент не перерисовывает модель предмета и рука не дёргается.
 *
 * Покрывает свитки способностей (CooldownManager) и свитки инсталляций
 * (InstallationService.placeCooldownRemaining, включая Ледяную руну).
 */
public final class ScrollCooldownTask {

    /** Условная «ёмкость» полосы прочности для отображения прогресса КД. */
    private static final int BAR_MAX = 100;

    private final RaskolClasses plugin;
    /** playerUUID → (slot → последний отображённый процент остатка КД; -1 = полоса чистая). */
    private final Map<UUID, Map<Integer, Integer>> shown = new ConcurrentHashMap<>();

    public ScrollCooldownTask(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Map<Integer, Integer> slots = shown.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            var inv = player.getInventory();
            int held = inv.getHeldItemSlot();

            for (int slot = 0; slot < 36; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null) {
                    continue;
                }
                long remaining = -1L;
                long total = -1L;
                String abilityId = plugin.getTokens().readId(item);
                if (abilityId != null && pc != null) {
                    AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
                    if (def != null) {
                        remaining = plugin.getCooldowns().getRemainingMillis(uuid, abilityId);
                        total = def.cooldownMillis();
                    }
                } else {
                    InstallationType type = plugin.getInstallToken().readType(item);
                    if (type != null) {
                        remaining = plugin.getInstallations().placeCooldownRemaining(uuid, type);
                        total = plugin.getInstallations().placeCooldownTotalMillis(type);
                    }
                }
                if (remaining < 0L || total <= 0L) {
                    continue;
                }
                // процент остатка КД для полосы: 100 = только что скастовано, 0 = готово
                int pct = (int) Math.ceil(Math.min(1.0, (double) remaining / total) * BAR_MAX);
                if (remaining <= 0L) {
                    pct = 0;
                }
                Integer last = slots.get(slot);
                int lastVal = last == null ? -1 : last;
                if (lastVal == pct) {
                    continue;
                }
                slots.put(slot, pct);
                applyBar(item, pct, slot == held);
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

    /**
     * Ставит/снимает полосу прочности. Для предмета в руке используем только
     * setItemMeta (без setItem) — клиент не перерисовывает модель, рука не дёргается.
     */
    private void applyBar(ItemStack item, int pct, boolean held) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg)) {
            return;
        }
        int damage = pct > 0 ? Math.max(1, (int) Math.round((double) pct / BAR_MAX * maxDurability(item))) : 0;
        if (dmg.getDamage() == damage) {
            return;
        }
        dmg.setDamage(damage);
        item.setItemMeta(dmg);
        // для предмета НЕ в руке дополнительно синхронизируем слот (дёргания нет)
        if (!held) {
            // слот обновит сам инвентарь при следующем рендере; setItem не нужен
        }
    }

    private int maxDurability(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable) {
            int max = item.getType().getMaxDurability();
            return max > 0 ? max : BAR_MAX;
        }
        return BAR_MAX;
    }
}
