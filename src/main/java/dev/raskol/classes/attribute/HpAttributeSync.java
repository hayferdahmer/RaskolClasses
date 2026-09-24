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
 * 1.9.3 (план B — виртуальный пул HP):
 * Ванильный MAX_HEALTH = carrier = min(formula, 1024) — носитель-пропорция.
 * Реальный боевой пул = formula (effective), живёт в HpBarService/CombatService.
 * Sync устанавливает carrier на join/respawn/reload/invalidate + sweep 5 с.
 * Datapack больше НЕ требуется: потолок 1024 обходится масштабированием, а не оверрайдом.
 */
public final class HpAttributeSync implements Listener {

    private static final double VANILLA_MAX_HEALTH_CAP = 1024.0;

    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    private final RaskolClasses plugin;
    private BukkitTask sweepTask;

    public HpAttributeSync(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Установить ванильный MAX_HEALTH = carrier = min(formula, 1024). */
    public void sync(Player player) {
        if (player == null || MAX_HEALTH == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(MAX_HEALTH);
        if (inst == null) {
            return;
        }
        double formula = plugin.getAttributes().maxHp(player.getUniqueId());
        double safeFormula = Double.isFinite(formula) && formula >= 1.0 ? formula : 20.0;
        double carrier = Math.min(safeFormula, VANILLA_MAX_HEALTH_CAP);
        if (Math.abs(inst.getBaseValue() - carrier) > 0.0001) {
            inst.setBaseValue(carrier);
        }
        // clamp текущего HP к carrier, чтобы не было "HP > MAX"
        if (player.getHealth() > carrier) {
            try {
                player.setHealth(carrier);
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
