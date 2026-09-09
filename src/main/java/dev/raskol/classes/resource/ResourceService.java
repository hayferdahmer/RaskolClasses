// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ресурсы 0–100: реген и декей в асинхронном таске раз в секунду,
 * набор — на событиях урона и лечения. Только сессия, без БД.
 * Пакет 5b: тиры регена мага (3/4/5/6 по порогам 25/50/75).
 *
 * 1.6.11: явный кламп [0..100] на всех публичных входах (add/refund/consume)
 * + санитаризация NaN/Infinity: битое число из конфига или от хука не может
 * сломать ресурс или унести его за границы. Второй уровень защиты:
 * ResourceState.add() внутри уже делает clamp, но явный guard здесь
 * закрывает утечки через публичные методы и делает контракт явным.
 */
public final class ResourceService implements Listener {

    private final Map<UUID, ResourceState> states = new ConcurrentHashMap<>();
    private final RaskolConfig config;
    private final ClassProvider classProvider;

    public ResourceService(RaskolConfig config, ClassProvider classProvider) {
        this.config = config;
        this.classProvider = classProvider;
    }

    public ResourceState stateOf(UUID playerId) {
        return states.computeIfAbsent(playerId, id -> new ResourceState());
    }

    public double getValue(UUID playerId) {
        ResourceState state = states.get(playerId);
        return state == null ? 0.0 : state.getValue();
    }

    /**
     * 1.6.11: consume с санитаризацией. NaN/Infinity/отрицательные — отказ.
     * Кламп сверху: нельзя запросить списание больше максимума.
     */
    public boolean consume(UUID playerId, double amount) {
        double safe = sanitize(amount);
        if (safe <= 0.0 || safe > ResourceState.MAX_VALUE) {
            return false;
        }
        return stateOf(playerId).consume(safe);
    }

    /** 1.6.11: refund с санитаризацией и явным клампом 0..100. */
    public void refund(UUID playerId, double amount) {
        double safe = sanitize(amount);
        if (safe > 0.0) {
            stateOf(playerId).add(safe);
        }
    }

    public void reset(UUID playerId) {
        states.remove(playerId);
    }

    /** 1.6.11: бонус жреца за событие лечения — тоже через санитаризацию. */
    public void addHealBonus(UUID playerId) {
        double gain = sanitize(config.resourceOnHeal(PlayerClass.PRIEST));
        if (gain > 0.0) {
            stateOf(playerId).add(gain);
        }
    }

    public BukkitTask startTickTask(Plugin plugin) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            ResourceState state = states.computeIfAbsent(id, k -> new ResourceState());
            PlayerClass pc = classProvider.getCachedClass(id);
            if (pc == null) {
                pc = classProvider.getClassOf(player);
                if (pc == null) {
                    continue;
                }
            }
            tickFor(pc, state);
        }
    }

    private void tickFor(PlayerClass pc, ResourceState state) {
        double regenPerSecond = sanitize(config.resourceRegen(pc));
        long windowMillis = config.combatWindowSeconds(pc) * 1000L;

        switch (pc) {
            case HUNTER -> {
                // Концентрация: +5/с только вне боя
                if (regenPerSecond > 0 && !state.isInCombat(windowMillis)) {
                    state.add(regenPerSecond);
                }
            }
            case WARRIOR -> {
                // Ярость: −5/с вне боя, в бою не тикает
                if (regenPerSecond < 0 && !state.isInCombat(windowMillis)) {
                    state.add(regenPerSecond);
                }
            }
            case MAGE -> {
                // Пакет 5b: тиры регена мага по текущему значению маны
                double v = state.getValue();
                double rate;
                if (v < 25) rate = sanitize(config.mageRegenTier1());
                else if (v < 50) rate = sanitize(config.mageRegenTier2());
                else if (v < 75) rate = sanitize(config.mageRegenTier3());
                else rate = sanitize(config.mageRegenTier4());
                state.add(rate);
            }
            default -> {
                // Свет жреца, энергия разбойника — линейный реген всегда
                if (regenPerSecond > 0) {
                    state.add(regenPerSecond);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        states.computeIfAbsent(id, k -> new ResourceState());
        classProvider.getClassOf(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ResourceState state = stateOf(player.getUniqueId());
        state.markCombat();
        PlayerClass pc = classProvider.getClassOf(player);
        double gain = sanitize(pc == null ? 0.0 : config.resourceOnDeal(pc));
        if (gain > 0 && state.allowGainEvent()) {
            state.add(gain);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ResourceState state = stateOf(player.getUniqueId());
        state.markCombat();
        PlayerClass pc = classProvider.getClassOf(player);
        double gain = sanitize(pc == null ? 0.0 : config.resourceOnTake(pc));
        if (gain > 0 && state.allowGainEvent()) {
            state.add(gain);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerClass pc = classProvider.getClassOf(player);
        if (pc != PlayerClass.PRIEST) {
            return;
        }
        double gain = sanitize(config.resourceOnHeal(PlayerClass.PRIEST));
        if (gain > 0) {
            stateOf(player.getUniqueId()).add(gain);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 1.6.11: санитаризация чисел, приходящих из конфига и хуков.
     * NaN/Infinity → 0.0; конечные значения возвращаются как есть
     * (кламп в [0..100] делает ResourceState.add, но потребителям безопаснее
     * иметь чистое значение уже на входе).
     */
    private static double sanitize(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return value;
    }
}
