// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Ресурсы классов (Ярость/Концентрация/Свет/Мана/Энергия), 0–100.
 * Реген-правила по классам (тик 1 раз в секунду):
 *  - WARRIOR: −5/с вне боя; +10 за урон (нанёс/получил, кап 1 событие/с);
 *  - HUNTER: +5/с вне боя, 0 в бою;
 *  - PRIEST: +2/с всегда; +5 за событие лечения (RegainHealth);
 *  - MAGE: тиры 3/4/5/6 в секунду по порогам значения 25/50/75;
 *  - ROGUE: +10/с.
 *
 * 1.7.4.1 фикс 3: ПЕРСИСТ РЕСУРСА — resources.yml через SafeStorage:
 *  - на quit сохраняем значение, на join восстанавливаем;
 *  - saveAll() вызывается автосейв-таском RaskolClasses вместе с кулдаунами;
 *  - без записи на join новый игрок стартует с 0 (легаси-поведение).
 */
public final class ResourceService implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    private final RaskolClasses plugin;
    private final RaskolConfig config;
    private final ClassProvider classProvider;
    private final Map<UUID, ResourceState> states = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration store;

    public ResourceService(RaskolClasses plugin, RaskolConfig config, ClassProvider classProvider) {
        this.plugin = plugin;
        this.config = config;
        this.classProvider = classProvider;
        this.file = new File(plugin.getDataFolder(), "resources.yml");
        this.store = SafeStorage.loadWithFallback(file, LOGGER);
    }

    /* ------------------------------ доступ к статам ------------------------------ */

    public ResourceState stateOf(UUID uuid) {
        return states.computeIfAbsent(uuid, id -> new ResourceState());
    }

    public double getValue(UUID uuid) {
        return stateOf(uuid).getValue();
    }

    public boolean consume(UUID uuid, double amount) {
        return stateOf(uuid).consume(amount);
    }

    public void refund(UUID uuid, double amount) {
        stateOf(uuid).add(amount);
    }

    public void add(UUID uuid, double amount) {
        stateOf(uuid).add(amount);
    }

    public void clear(UUID uuid) {
        states.remove(uuid);
    }

    /* ------------------------- персист (1.7.4.1 фикс 3) ------------------------- */

    /** На join: восстанавливаем сохранённое значение ресурса (если запись есть). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String key = uuid.toString();
        if (store.isSet(key)) {
            stateOf(uuid).setValue(store.getDouble(key, 0.0));
        }
    }

    /** На quit: сохраняем значение и убираем состояние из памяти. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        store.set(uuid.toString(), stateOf(uuid).getValue());
        SafeStorage.saveAtomic(store, file, LOGGER);
        states.remove(uuid);
    }

    /** Автосейв (вызывается автосейв-таском RaskolClasses). */
    public void saveAll() {
        states.forEach((uuid, st) -> store.set(uuid.toString(), st.getValue()));
        SafeStorage.saveAtomic(store, file, LOGGER);
    }

    /* -------------------------------- реген-тик -------------------------------- */

    public BukkitTask startTickTask(RaskolClasses plugin) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRegen, 20L, 20L);
    }

    private void tickRegen() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerClass pc = classProvider.getCachedClass(p.getUniqueId());
            if (pc == null) {
                pc = classProvider.getClassOf(p);
            }
            if (pc == null) {
                continue;
            }
            UUID uuid = p.getUniqueId();
            ResourceState st = stateOf(uuid);
            boolean inCombat = st.isInCombat(config.combatWindowSeconds(pc) * 1000L);
            double delta = 0.0;
            switch (pc) {
                case WARRIOR, HUNTER -> {
                    if (!inCombat) {
                        delta = config.resourceRegen(pc);
                    }
                }
                case PRIEST, ROGUE -> delta = config.resourceRegen(pc);
                case MAGE -> delta = mageTier(st.getValue());
            }
            if (delta != 0.0) {
                st.add(delta);
            }
        }
    }

    /** Тиры маны: 3/4/5/6 в секунду по порогам значения 25/50/75. */
    private double mageTier(double value) {
        if (value < 25.0) {
            return config.mageRegenTier1();
        }
        if (value < 50.0) {
            return config.mageRegenTier2();
        }
        if (value < 75.0) {
            return config.mageRegenTier3();
        }
        return config.mageRegenTier4();
    }

    /* ------------------------------ боевые события ------------------------------ */

    /** Урон: метка «в бою» + ярость воина за нанесение/получение (кап 1 событие/с). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player damager = resolvePlayer(event.getDamager());
        if (damager != null) {
            UUID uuid = damager.getUniqueId();
            ResourceState st = stateOf(uuid);
            st.markCombat();
            PlayerClass pc = classProvider.getClassOf(damager);
            if (pc != null) {
                double onDeal = config.resourceOnDeal(pc);
                if (onDeal != 0.0 && st.allowGainEvent()) {
                    st.add(onDeal);
                }
            }
        }
        if (event.getEntity() instanceof Player victim) {
            UUID uuid = victim.getUniqueId();
            ResourceState st = stateOf(uuid);
            st.markCombat();
            PlayerClass pc = classProvider.getClassOf(victim);
            if (pc != null) {
                double onTake = config.resourceOnTake(pc);
                if (onTake != 0.0 && st.allowGainEvent()) {
                    st.add(onTake);
                }
            }
        }
    }

    /** Лечение: +resource-on-heal классу вылеченного (легаси: жрец за любое своё лечение). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        PlayerClass pc = classProvider.getClassOf(p);
        if (pc == null) {
            return;
        }
        double onHeal = config.resourceOnHeal(pc);
        if (onHeal != 0.0) {
            stateOf(p.getUniqueId()).add(onHeal);
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
