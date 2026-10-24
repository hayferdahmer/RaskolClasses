// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.resource;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.attribute.Hp;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.event.CustomHealEvent;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.service.Spec2Service;
import dev.raskol.classes.storage.SafeStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Ресурсы классов (Ярость/Концентрация/Свет/Мана/Энергия/Скверна), по умолчанию 0–100.
 * 1.9.3.2: знаковый tickDelta. 1.10.x: Скверна событийная. 1.11.1: декэй Демонологии.
 * 1.14.0 (Б7): legacy-слой отключён — regen-бонусы только из Spec2Service.regenBonus
 *         (узлы regen деревьев + роли); ARCANE-прибавка маны теперь рангами дерева arcane.
 * 1.14.4 (Волна 4, 3B): stateOf применяет расширяемый потолок (100 + resourceMaxBonus);
 *         положительный rate регенерации умножается на (1 + resourceRegenPercent/100).
 * 1.14.7 (Спринт 2, P1-1): ресурс-он-хил (classes.<CLASS>.resource-on-heal) переведён
 *         с ванильного EntityRegainHealthEvent + stale-маркера хилера на CustomHealEvent:
 *         начисление детерминированно происходит на каждое кит-лечение с атрибуцией
 *         целителя, а не случайно на ванильных регенах с устаревшей меткой.
 *         onRegainHealth-обработчик удалён; PassiveListener.pollHealerMark больше не зовётся.
 * 1.14.8 (Sprint 6, Волна 6.3b, P0-5-01): выход игрока больше не пишет resources.yml
 *         синхронно на главном потоке. Секция игрока фиксируется в in-memory store
 *         (store.set), атомарный persist — в autosave-задаче (saveAll, каждые
 *         storage.autosave-minutes) и в onDisable.
 * 1.14.12 (Sprint 8, Волна 12A, P0-3-02): overflow-тик Скверны читает carrier через
 *         Hp.max (атрибут MAX_HEALTH) вместо deprecated Player#getMaxHealth().
 * 1.14.13 (Sprint 9, Волна 13D, финал, P0-4-05): switch в tick() ключуется по
 *         PlayerClass.RegenModel (COMBAT_PAUSE/PLAIN/TIERED/FLOOR_DECAY), а не по классу:
 *         новый класс = выбор существующей модели без правки switch (OCP, аудит 4-05).
 *         Кламп пола и overflow-тик Скверны тоже ключуются по модели FLOOR_DECAY.
 *         onEntityDeath остаётся с проверкой pc == WARLOCK: onKill-ресурс читает
 *         warlock-именованные конфиг-ключи (warlockResourceOnKill) — привязка к модели
 *         была бы преждевременным обобщением до появления per-class ключей.
 *         Числовые значения регена/окон по-прежнему читаются из конфига
 *         (config.resourceRegen / combatWindowSeconds / mageRegenTier* / warlock*) —
 *         админ тюнит баланс без пересборки.
 * 1.15.1-A: FQN ItemStat перенесены в item.model после реструктуризации item-пакета.
 */
public final class ResourceService implements Listener {

    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");
    private static final double MAX_VALUE = 100.0;
    private static final long DEBUFF_PURGE_TICKS = 600L;

    private final RaskolClasses plugin;
    private final RaskolConfig config;
    private final ClassProvider classProvider;
    private final Map<UUID, ResourceState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGainMs = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration store;

    private long tickCounter = 0L;

    public ResourceService(RaskolClasses plugin, RaskolConfig config, ClassProvider classProvider) {
        this.plugin = plugin;
        this.config = config;
        this.classProvider = classProvider;
        this.file = new File(plugin.getDataFolder(), "resources.yml");
        this.store = SafeStorage.loadWithFallback(file, LOGGER);
    }

    /**
     * 1.14.4 (3B): состояние ресурса с актуальным потолком из spec2-агрегата.
     */
    public ResourceState stateOf(UUID uuid) {
        ResourceState st = states.computeIfAbsent(uuid, k -> new ResourceState());
        Spec2Service svc = plugin.getSpec2Service();
        double maxBonus=svc==null?0.0:svc.resourceMaxBonus(uuid);
        if(!Double.isFinite(maxBonus)||maxBonus<0)maxBonus=0.0;
        Player player=Bukkit.getPlayer(uuid);
        if(player!=null&&plugin.getItems()!=null){
            PlayerClass pc=classProvider.getClassOf(player);
            dev.raskol.classes.item.model.ItemStat maxStat=pc==PlayerClass.WARRIOR?dev.raskol.classes.item.model.ItemStat.MAX_RAGE
                    :pc==PlayerClass.ROGUE||pc==PlayerClass.HUNTER?dev.raskol.classes.item.model.ItemStat.MAX_ENERGY
                    :dev.raskol.classes.item.model.ItemStat.MAX_MANA;
            double itemBonus=plugin.getItems().value(uuid,maxStat);
            if(!Double.isFinite(itemBonus)||itemBonus<0)itemBonus=0.0;
            maxBonus+=itemBonus;
        }
        st.setCeiling(MAX_VALUE+maxBonus);
        return st;
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
        lastGainMs.remove(uuid);
    }

    public void saveAll() {
        states.forEach((uuid, st) -> store.set(uuid.toString(), st.getValue()));
        SafeStorage.saveAtomic(store, file, LOGGER);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String key = uuid.toString();
        ResourceState st = stateOf(uuid);
        if (store.isSet(key)) {
            double v = Math.max(0.0, store.getDouble(key, 0.0));
            st.setValue(v);
        }
    }

    /**
     * 1.14.8-fix (Волна 6.3b, P0-5-01): синхронная запись файла на выходе убрана.
     * store.set фиксирует ресурс игрока в in-memory (он переживёт autosave и попадёт
     * в файл на ближайшем saveAll/onDisable), после чего оперативная карта освобождается.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        store.set(uuid.toString(), stateOf(uuid).getValue());
        clear(uuid);
    }

    public BukkitTask startTickTask(RaskolClasses plugin) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private Spec spec2Spec(UUID uuid) {
        Spec2Service svc = plugin.getSpec2Service();
        if (svc == null) {
            return null;
        }
        String main = svc.mainSpec(uuid);
        return main == null ? null : Spec.fromId(main);
    }

    private void tick() {
        tickCounter++;
        if (tickCounter % DEBUFF_PURGE_TICKS == 0) {
            WarlockAbilities.purgeStaleDebuffs();
        }
        Spec2Service svc = plugin.getSpec2Service();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerClass pc = classProvider.getClassOf(player);
            if (pc == null) {
                continue;
            }
            ResourceState st = stateOf(uuid);
            long windowMs = config.combatWindowSeconds(pc) * 1000L;
            boolean inCombat = st.isInCombat(windowMs);
            Spec spec = spec2Spec(uuid);

            // 1.14.13 (Волна 13D, финал, 4-05): switch по МОДЕЛИ регена, а не по классу.
            // Соответствие модель↔класс сегодня 1:1, поведение идентично pre-13D.
            double rate;
            switch (pc.regenModel()) {
                case COMBAT_PAUSE -> rate = inCombat ? 0.0 : config.resourceRegen(pc);
                case TIERED -> {
                    double v = st.getValue();
                    rate = v < 25 ? config.mageRegenTier1()
                            : v < 50 ? config.mageRegenTier2()
                            : v < 75 ? config.mageRegenTier3()
                            : config.mageRegenTier4();
                }
                case FLOOR_DECAY -> {
                    double v = st.getValue();
                    double floor = config.warlockResourceFloor();
                    if (floor > 0.0 && v < floor) {
                        rate = config.warlockResourceFloorRegen();
                    } else if (v > floor && !inCombat) {
                        rate = (spec == Spec.DEMONOLOGY)
                                ? plugin.getConfig().getDouble(
                                        "classes.WARLOCK.specs.demonology.decay", -2.0)
                                : config.warlockResourceDecay();
                    } else {
                        rate = 0.0;
                    }
                }
                default -> rate = config.resourceRegen(pc); // PLAIN: PRIEST, ROGUE
            }
            if(rate>0&&plugin.getItems()!=null)rate+=plugin.getItems().value(uuid,dev.raskol.classes.item.model.ItemStat.RESOURCE_REGEN);
            // 1.14.0 (Б7): regen-бонусы только из spec2-агрегата (узлы regen)
            if (svc != null) {
                rate += svc.regenBonus(uuid);
            }
            // 1.14.4 (3B): множитель положительного rate из spec2 (узлы resource_regen_pct).
            // Применяется ТОЛЬКО к rate > 0 — декэй WARLOCK не усиливается.
            if (rate > 0.0 && svc != null) {
                double regenPct = svc.resourceRegenPercent(uuid);
                if (Double.isFinite(regenPct) && regenPct > 0.0) {
                    rate *= (1.0 + regenPct / 100.0);
                }
            }

            // 1.14.13 (Волна 13D, финал): кламп пола — по модели FLOOR_DECAY
            if (pc.regenModel() == PlayerClass.RegenModel.FLOOR_DECAY) {
                double floor = config.warlockResourceFloor();
                double v = st.getValue();
                double next = v + rate;
                if (rate < 0.0 && v >= floor && next < floor) {
                    next = floor;
                }
                if (rate > 0.0 && v <= floor && next > floor) {
                    next = floor;
                }
                st.setValue(next);
            } else if (rate != 0.0) {
                st.tickDelta(rate);
            }

            // 1.14.12 (Волна 12A, 3-02): carrier через Hp.max (атрибут, NaN-guard).
            // 1.14.13 (Волна 13D, финал): overflow-тик — по модели FLOOR_DECAY.
            if (pc.regenModel() == PlayerClass.RegenModel.FLOOR_DECAY
                    && st.getValue() >= config.warlockThresholdOverflow()) {
                double carrier = Hp.max(player);
                double tickDmg = carrier * 0.01;
                double newHp = Math.max(1.0, player.getHealth() - tickDmg);
                player.setHealth(newHp);
            }
        }
    }

    private boolean gainAllowed(UUID uuid) {
        long now = System.currentTimeMillis();
        Long prev = lastGainMs.get(uuid);
        if (prev != null && now - prev < 1000L) {
            return false;
        }
        lastGainMs.put(uuid, now);
        return true;
    }

    private boolean isFarmPair(Player damager, Entity victim) {
        if (!(victim instanceof Player victimPlayer)) {
            return false;
        }
        if (victimPlayer.getUniqueId().equals(damager.getUniqueId())) {
            return true;
        }
        return !plugin.getCombat().canHit(damager, victimPlayer);
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

    /**
     * 1.14.7 (Sprint 2, P1-1): ресурс-он-хил через CustomHealEvent.
     * Событие несёт целителя напрямую (атрибуция из HpBarService.heal),
     * stale-маркер PassiveListener больше не участвует. Rate-limit 1 с (gainAllowed)
     * сохраняет прежнее поведение «+5 Света за событие лечения, не за тик».
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCustomHeal(CustomHealEvent event) {
        Player healer = event.getHealer();
        if (healer == null || event.getAmount() <= 0.0) {
            return;
        }
        PlayerClass pc = classProvider.getClassOf(healer);
        if (pc == null) {
            return;
        }
        double onHeal = config.resourceOnHeal(pc);
        if (onHeal != 0.0 && gainAllowed(healer.getUniqueId())) {
            stateOf(healer.getUniqueId()).add(onHeal);
        }
    }

    /**
     * OnKill-ресурс: осознанно привязан к WARLOCK (конфиг-ключ warlockResourceOnKill
     * именован по классу). При появлении второго класса с onKill-механикой сначала
     * заводим per-class конфиг-ключи, затем переводим проверку на модель/ключ.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.getWorld() == null) {
            return;
        }
        org.bukkit.Location deathLoc = entity.getLocation();
        double radiusSq = 10.0 * 10.0;
        for (Player player : entity.getWorld().getPlayers()) {
            PlayerClass pc = classProvider.getClassOf(player);
            if (pc != PlayerClass.WARLOCK) {
                continue;
            }
            if (player.getLocation().distanceSquared(deathLoc) <= radiusSq) {
                double onKill = config.warlockResourceOnKill();
                if (onKill != 0.0 && gainAllowed(player.getUniqueId())) {
                    stateOf(player.getUniqueId()).markCombat();
                    stateOf(player.getUniqueId()).add(onKill);
                }
            }
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
