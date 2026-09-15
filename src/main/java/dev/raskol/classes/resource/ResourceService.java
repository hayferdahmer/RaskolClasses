// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.passive.PassiveListener;
import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
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
 * Реген-правила по классам (тик 1 раз/с): воин −5/с вне боя; охотник +5/с вне боя;
 * жрец +2/с всегда; маг тиры 1.0/1.5/2.0/2.5 по порогам 25/50/75; разбойник +10/с.
 * Боевые прибавки: воин +10 за нанесённый/полученный урон (кап 1 раз/с),
 * жрец +5 за событие лечения (кап 1 раз/с).
 *
 * 1.9.1: markCombat() на нанёсшем и получившем урон — боевое окно работает.
 * 1.9.2 (эксплойт-свип): ФАРМ-ГЕЙТЫ ресурса:
 *  - урон по СЕБЕ не даёт прибавок (on-deal и on-take);
 *  - урон по СОЮЗНИКУ (friendly-fire выкл / одна фракция) не даёт прибавок обеим
 *    сторонам — спарринг-фарм ярости/концентрации закрыт;
 *  - урон по мобам/врагам-игрокам даёт прибавки как раньше (фарм на мобах — дизайн).
 */
public final class ResourceService implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final double MAX_VALUE = 100.0;

    private final RaskolClasses plugin;
    private final RaskolConfig config;
    private final ClassProvider classProvider;
    private final Map<UUID, ResourceState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGainMs = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration store;

    public ResourceService(RaskolClasses plugin, RaskolConfig config, ClassProvider classProvider) {
        this.plugin = plugin;
        this.config = config;
        this.classProvider = classProvider;
        this.file = new File(plugin.getDataFolder(), "resources.yml");
        this.store = SafeStorage.loadWithFallback(file, LOGGER);
    }

    /* -------------------------------- доступ -------------------------------- */

    public ResourceState stateOf(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new ResourceState());
    }

    public double getValue(UUID uuid) {
        return stateOf(uuid).getValue();
    }

    /** Единственная точка списания — ResourceState.consume (регресс заперт чеком 30). */
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
        lastGainMs.remove(uuid);
    }

    /* -------------------------------- персист -------------------------------- */

    public void saveAll() {
        states.forEach((uuid, st) -> store.set(uuid.toString(), st.getValue()));
        SafeStorage.saveAtomic(store, file, LOGGER);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String key = uuid.toString();
        if (store.isSet(key)) {
            double v = Math.max(0.0, Math.min(MAX_VALUE, store.getDouble(key, 0.0)));
            stateOf(uuid).setValue(v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        store.set(uuid.toString(), stateOf(uuid).getValue());
        SafeStorage.saveAtomic(store, file, LOGGER);
        clear(uuid);
    }

    /* -------------------------------- реген-тик -------------------------------- */

    public BukkitTask startTickTask(RaskolClasses plugin) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerClass pc = classProvider.getClassOf(player);
            if (pc == null) {
                continue;
            }
            ResourceState st = stateOf(uuid);
            long windowMs = config.combatWindowSeconds(pc) * 1000L;
            boolean inCombat = st.isInCombat(windowMs);

            double rate;
            switch (pc) {
                case WARRIOR -> rate = inCombat ? 0.0 : config.resourceRegen(pc);
                case HUNTER -> rate = inCombat ? 0.0 : config.resourceRegen(pc);
                case MAGE -> {
                    double v = st.getValue();
                    rate = v < 25 ? config.mageRegenTier1()
                            : v < 50 ? config.mageRegenTier2()
                            : v < 75 ? config.mageRegenTier3()
                            : config.mageRegenTier4();
                }
                default -> rate = config.resourceRegen(pc); // PRIEST, ROGUE
            }
            rate += plugin.getTalentService().regenBonus(uuid);

            if (rate != 0.0) {
                st.add(rate);
            }
        }
    }

    /* ---------------------------- боевые прибавки ---------------------------- */

    private boolean gainAllowed(UUID uuid) {
        long now = System.currentTimeMillis();
        Long prev = lastGainMs.get(uuid);
        if (prev != null && now - prev < 1000L) {
            return false;
        }
        lastGainMs.put(uuid, now);
        return true;
    }

    /**
     * 1.9.2: true, если пара damager→victim является фарм-парой (себя/союзник)
     * и прибавки ресурса за этот hit давать нельзя.
     */
    private boolean isFarmPair(Player damager, Entity victim) {
        if (!(victim instanceof Player victimPlayer)) {
            return false; // мобы/средства — легитимный фарм/бой
        }
        if (victimPlayer.getUniqueId().equals(damager.getUniqueId())) {
            return true; // самоурон
        }
        return !plugin.getCombat().canHit(damager, victimPlayer); // союзник
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player damager = resolvePlayer(event.getDamager());
        if (damager != null) {
            boolean farm = isFarmPair(damager, event.getEntity());
            if (!farm) {
                stateOf(damager.getUniqueId()).markCombat();
                PlayerClass pc = classProvider.getClassOf(damager);
                if (pc != null) {
                    double onDeal = config.resourceOnDeal(pc);
                    if (onDeal != 0.0 && gainAllowed(damager.getUniqueId())) {
                        stateOf(damager.getUniqueId()).add(onDeal);
                    }
                }
            }
        }
        if (event.getEntity() instanceof Player victim) {
            boolean farm = damager != null && isFarmPair(damager, victim);
            if (!farm) {
                stateOf(victim.getUniqueId()).markCombat();
                PlayerClass pc = classProvider.getClassOf(victim);
                if (pc != null) {
                    double onTake = config.resourceOnTake(pc);
                    if (onTake != 0.0 && gainAllowed(victim.getUniqueId())) {
                        stateOf(victim.getUniqueId()).add(onTake);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        UUID healer = PassiveListener.pollHealerMark();
        if (healer == null) {
            return;
        }
        Player healerPlayer = plugin.getServer().getPlayer(healer);
        if (healerPlayer == null) {
            return;
        }
        PlayerClass pc = classProvider.getClassOf(healerPlayer);
        if (pc == null) {
            return;
        }
        double onHeal = config.resourceOnHeal(pc);
        if (onHeal != 0.0 && gainAllowed(healer)) {
            stateOf(healer).add(onHeal);
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
