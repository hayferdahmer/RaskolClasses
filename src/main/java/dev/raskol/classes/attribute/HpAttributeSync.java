// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import dev.raskol.classes.RaskolClasses;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * 1.9.3: СИНХРОНИЗАЦИЯ ванильного MAX_HEALTH с формулой AttributeService.
 *
 * Архитектурный дефект 1.9.2 и ранее:
 *   - AttributeService.maxHp(uuid) считает по формуле (2560 у воина L60/STR84).
 *   - player.getAttribute(MAX_HEALTH).getValue() остаётся на ванильных 20.
 *   - warrior.fenrirBlood/ragnarok, CombatService.applyEnvLethalScale и плагины-
 *     партнёры читали ВАНИЛЬНЫЙ атрибут → хилы лечили только до 20 HP,
 *     execute-пороги считались от 20, падения не масштабировались.
 *
 * Фикс: на join/respawn/class-change/level-up/talents-apply — устанавливать
 * ванильный MAX_HEALTH = AttributeService.maxHp(uuid).
 * Плюс периодический таск (5 с) для страховки на случай, если где-то кэш
 * не инвалидирован.
 */
public final class HpAttributeSync implements Listener {

    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    private final RaskolClasses plugin;
    private BukkitTask sweepTask;

    public HpAttributeSync(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Установить ванильный MAX_HEALTH игрока = формульному значению. */
    public void sync(Player player) {
        if (player == null || MAX_HEALTH == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(MAX_HEALTH);
        if (inst == null) {
            return;
        }
        double formula = plugin.getAttributes().maxHp(player.getUniqueId());
        double safe = Double.isFinite(formula) && formula >= 1.0 ? formula : 20.0;
        if (Math.abs(inst.getBaseValue() - safe) > 0.0001) {
            inst.setBaseValue(safe);
        }
        // clamp текущего HP, чтобы не было "HP > MAX" после уменьшения пула
        if (player.getHealth() > safe) {
            try {
                player.setHealth(safe);
            } catch (IllegalArgumentException ignored) {
                // игрок мёртв/оффлайн — пропускаем
            }
        }
    }

    /** Массовая синхронизация всех онлайн-игроков (для /rc reload и reconcile). */
    public void syncAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sync(p);
        }
    }

    /** Синхронизация по UUID (для вызова из AttributeService.invalidate). */
    public void syncByUuid(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            sync(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // отложенный тик: после респавна атрибуты ещё не восстановлены
        Bukkit.getScheduler().runTaskLater(plugin, () -> sync(event.getPlayer()), 2L);
    }

    /** Запустить периодический sweep (страховка от рассинхрона кэша). */
    public void startSweep(long periodTicks) {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAll,
                periodTicks, periodTicks);
    }

    public void stopSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }
}
