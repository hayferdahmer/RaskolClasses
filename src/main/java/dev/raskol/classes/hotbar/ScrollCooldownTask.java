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
 * Индикатор кулдауна на свитке = ПОЛОСА ПРОЧНОСТИ (1.5.6 → 1.9.0-fix12, v12).
 *
 * Возвращает поведение «как было»: полоса прочности на аметисте как КД.
 * Работает через Damageable#setMaxDamage/setDamage — с 1.20.5 прочность является
 * компонентом и рисуется клиентом на ЛЮБОМ предмете, включая AMETHYST_SHARD.
 * (Прежнее утверждение «на аметисте невозможно» было ошибочным.)
 *
 * Анти-дёргание: состояние бара обновляется каждую секунду БЕЗ пакета
 * (мутация зеркала ItemStack). Пакет setItem уходит только в три момента:
 *  1) старт каста (маскируется кликом),
 *  2) готовность (одно мигание как сигнал «готово»),
 *  3) переключение на свиток (маскируется переключением).
 * Посекундного дёргания руки нет. При открытом инвентаре клиент сам забирает
 * актуальное состояние окна — бар виден и там.
 *
 * МАРКЕР ДЕПЛОЯ: строка «ScrollCooldownTask v12 (durability-bar) active» в логе старта.
 * Если её нет — на сервере стоит не этот jar, и чинить надо деплой, а не код.
 */
public final class ScrollCooldownTask {

    private final RaskolClasses plugin;
    /** player → slot → последние секунды, записанные в бар. */
    private final Map<UUID, Map<Integer, Long>> barState = new ConcurrentHashMap<>();
    /** player → последний held-слот (ловим переключения). */
    private final Map<UUID, Integer> lastHeld = new ConcurrentHashMap<>();

    public ScrollCooldownTask(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        plugin.getLogger().info("ScrollCooldownTask v12 (durability-bar) active");
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Map<Integer, Long> slots = barState.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            var inv = player.getInventory();
            int held = inv.getHeldItemSlot();
            Integer prevHeld = lastHeld.get(uuid);
            boolean heldChanged = prevHeld == null || prevHeld != held;
            lastHeld.put(uuid, held);

            PlayerClass pc = plugin.getClassProvider().getClassOf(player);

            for (int slot = 0; slot < 36; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null) {
                    slots.remove(slot);
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

                // кулдауна нет (или предмет не свиток): гасим бар, если он был
                if (remaining <= 0L || total <= 0L) {
                    Long had = slots.remove(slot);
                    if (had != null && had > 0L) {
                        writeBar(item, 0L, 1L, true, inv, slot); // одно мигание «готово»
                    }
                    continue;
                }

                long seconds = (remaining + 999L) / 1000L;              // ceil
                long totalSec = Math.max(1L, (total + 999L) / 1000L);
                Long last = slots.get(slot);
                long lastVal = last == null ? -1L : last;
                if (lastVal == seconds) {
                    continue;
                }
                slots.put(slot, seconds);

                boolean packet =
                        (lastVal <= 0L && seconds > 0L)   // старт каста
                        || (slot == held && heldChanged); // переключились на этот свиток
                writeBar(item, seconds, totalSec, packet, inv, slot);
            }

            // переключились на свиток с КД — освежаем бар пакетом (маскируется переключением)
            if (heldChanged) {
                ItemStack heldItem = inv.getItem(held);
                if (heldItem != null) {
                    Long s = slots.get(held);
                    if (s != null && s > 0L) {
                        writeBar(heldItem, s, totalSecOf(uuid, pc, heldItem), true, inv, held);
                    }
                }
            }
        }
        barState.keySet().removeIf(u -> plugin.getServer().getPlayer(u) == null);
        lastHeld.keySet().removeIf(u -> plugin.getServer().getPlayer(u) == null);
    }

    private long totalSecOf(UUID uuid, PlayerClass pc, ItemStack item) {
        String abilityId = plugin.getTokens().readId(item);
        if (abilityId != null && pc != null) {
            AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
            if (def != null) {
                return Math.max(1L, (def.cooldownMillis() + 999L) / 1000L);
            }
        }
        InstallationType type = plugin.getInstallToken().readType(item);
        if (type != null) {
            return Math.max(1L,
                    (plugin.getInstallations().placeCooldownTotalMillis(type) + 999L) / 1000L);
        }
        return 1L;
    }

    /**
     * Пишет бар. packet=true → inv.setItem (обновление клиента);
     * packet=false → мутация на месте без пакета (без дёргания).
     */
    private void writeBar(ItemStack item, long seconds, long totalSec, boolean packet,
                          org.bukkit.inventory.PlayerInventory inv, int slot) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg)) {
            return;
        }
        if (seconds <= 0L) {
            if (dmg.hasMaxDamage() || dmg.hasDamage()) {
                dmg.resetMaxDamage();
                dmg.setDamage(0);
                item.setItemMeta(meta);
            }
        } else {
            int max = (int) Math.max(1L, totalSec);
            int cur = (int) Math.max(1L, Math.min(max, seconds));
            dmg.setMaxDamage(max);
            dmg.setDamage(cur);
            item.setItemMeta(meta);
        }
        if (packet) {
            inv.setItem(slot, item);
        }
    }
}
