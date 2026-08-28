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
 * O10: тик без аллокаций и без LP-вызовов, пока кэш прогрет;
 * для холодных записей — один LP-вызов на игрока (фолбэк).
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

    public boolean consume(UUID playerId, double amount) {
        return stateOf(playerId).consume(amount);
    }

    /** Возврат ресурса при отмене каста (нет цели, нет безопасной точки). */
    public void refund(UUID playerId, double amount) {
        stateOf(playerId).add(amount);
    }

    /**
     * Сброс ресурса игрока при смене класса: «ярость воина» не должна
     * перетекать в «ману мага». Вызывается из LuckPermsBackend на NodeMutateEvent.
     */
    public void reset(UUID playerId) {
        states.remove(playerId);
    }

    /** Бонус жреца за событие лечения. */
    public void addHealBonus(UUID playerId) {
        stateOf(playerId).add(config.resourceOnHeal(PlayerClass.PRIEST));
    }

    /** Таск 20 тиков: реген вне боя, декей ярости, кап ресурсов. */
    public BukkitTask startTickTask(Plugin plugin) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            ResourceState state = states.computeIfAbsent(id, k -> new ResourceState());
            PlayerClass pc = classProvider.getCachedClass(id);
            if (pc == null) {
                // Холодный кэш: один LP-вызов прогревает запись.
                pc = classProvider.getClassOf(player);
                if (pc == null) {
                    continue;
                }
            }
            tickFor(pc, state);
        }
    }

    private void tickFor(PlayerClass pc, ResourceState state) {
        double regenPerSecond = config.resourceRegen(pc);
        long windowMillis = config.combatWindowSeconds(pc) * 1000L;

        switch (pc) {
            case HUNTER -> {
                // Концентрация: +5/с только вне боя, в бою — 0
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
            default -> {
                // Свет, мана, энергия — линейный реген всегда
                if (regenPerSecond > 0) {
                    state.add(regenPerSecond);
                }
                // Пропитанный маной: при мане ≥ порога реген +1/с
                if (pc == PlayerClass.MAGE && config.passiveEnabled(pc, "mana_soaked")
                        && state.getValue() >= config.passiveDouble(pc, "mana_soaked", "threshold", 50.0)) {
                    state.add(config.passiveDouble(pc, "mana_soaked", "regen-bonus", 1.0));
                }
            }
        }
    }

    /* ------------------------- события ------------------------- */

    /**
     * Join: создаём ResourceState и прогреваем кэш класса, чтобы реген
     * пошёл уже со второго тика (через 1 с), а не после первого каста.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        states.computeIfAbsent(id, k -> new ResourceState());
        classProvider.getClassOf(player);
    }

    /** Воин: +10 за нанесённый урон (кап 1 событие/с), окно боя обновляется. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ResourceState state = stateOf(player.getUniqueId());
        state.markCombat();
        PlayerClass pc = classProvider.getClassOf(player);
        double gain = pc == null ? 0.0 : config.resourceOnDeal(pc);
        if (gain > 0 && state.allowGainEvent()) {
            state.add(gain);
        }
    }

    /** Воин: +10 за полученный урон. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ResourceState state = stateOf(player.getUniqueId());
        state.markCombat();
        PlayerClass pc = classProvider.getClassOf(player);
        double gain = pc == null ? 0.0 : config.resourceOnTake(pc);
        if (gain > 0 && state.allowGainEvent()) {
            state.add(gain);
        }
    }

    /** Жрец: +5 света за событие лечения. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerClass pc = classProvider.getClassOf(player);
        if (pc != PlayerClass.PRIEST) {
            return;
        }
        stateOf(player.getUniqueId()).add(config.resourceOnHeal(PlayerClass.PRIEST));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
