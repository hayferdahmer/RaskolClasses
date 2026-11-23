// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.pet;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 1.14.6: ЕДИНЫЙ сервис боевых петов (wolf / demon / shadowfiend).
 *
 * 1.14.6-fix (Спринт 1, P0-8f): атрибуты реестра резолвятся в static-инициализаторе;
 *         при null — warning в конструкторе + attributesReady() для selftest.
 * 1.14.7 (Спринт 2, P0-8b): getEntity()==null (чанк выгружен) НЕ удаляет handle —
 *         запись ждёт загрузки чанка; чистка только через EntityDeathEvent/дезспавн.
 *         Startup-скан и ChunkLoadEvent-скан удаляют ОСИРОТЕВШИХ петов (метка
 *         rc_pet_owner есть, хозяина в карте нет) — страховка после краша/релоада.
 *         ttl-петы получают setLimitedLifetime(ttl+30с) как бэкстоп: если handle
 *         потерян, ванильный таймер всё равно погасит Vex.
 * 1.14.7 (Спринт 2, P0-8c): смена мира — пет телепортируется к владельцу в tick
 *         (раньше follow работал только внутри одного мира, волк застревал в аду,
 *         а новый призыв блокировался ALREADY); summon при живом handle в другом
 *         мире переносит пета, а не отказывает.
 * 1.14.12 (Sprint 8, Волна 12C, P0-5-06): PDC-чтение только после проверки типа
 *         сущности (LivingEntity) — не все Entity имеют PersistentDataContainer
 *         (например, Item, ExperienceOrb), чтение на них бросало NPE/ClassCast.
 * 1.14.13 (Sprint 9, Волна 13B, P0-10-03/04): конструктор без побочных эффектов —
 *         registerEvents и обе задачи (tick, orphan-scan) вынесены в публичные
 *         registerListeners()/startTickTask()/scheduleOrphanScan(); вызывает
 *         RaskolClasses.onEnable, ручки задач складываются в activeTasks
 *         и отменяются в onDisable явно.
 *
 * 1.15.7 (Волна 7d/8, аудит 2.6-68): handle чистится при ТИХОМ удалении пета.
 *         Раньше getEntity()==null не различал «чанк выгружен» и «сущность удалена
 *         без EntityDeathEvent» (vex-TTL, /kill, remove другим плагином) — владелец
 *         видел «Питомец где-то далеко» до перезахода. Теперь PetState хранит
 *         последнюю известную позицию (worldUuid/lastX/lastZ): если чанк этой
 *         позиции ЗАГРУЖЕН, а сущности нет — пет действительно исчез, handle
 *         снимается и владелец получает сообщение «призыв снова доступен».
 * 1.15.7 (Волна 7d/8, аудит 2.6-67): скан осиротевших петов переведён с
 *         ChunkLoadEvent на EntitiesLoadEvent — список сущностей приходит в событии,
 *         без повторного chunk.getEntities() (меньше аллокаций на каждую загрузку).
 * 1.15.7 (Волна 7d/8, аудит 2.6-9): despawnAll() для onDisable — петы не остаются
 *         в мире после выгрузки плагина.
 * 1.15.7 (Волна 7c, spotbugs): мёртвые поля PetState.dmgMult/speedMult УДАЛЕНЫ
 *         (писались, не читались: урон ставится из параметра buff(), сброс в tick()
 *         использовал литерал 1.0). Исключение URF_UNREAD_FIELD в spotbugs-exclude
 *         снято синхронно.
 */
public final class PetService implements Listener {

    private static final ThreadLocal<Boolean> SUPPRESS_PET_TALENTS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> SCHEDULED_PET_ATTACK = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Logger LOGGER = Logger.getLogger("RaskolClasses");

    public enum SummonResult {
        OK,
        ALREADY,
        UNKNOWN,
        NO_WORLD
    }

    private static final double FOLLOW_BLOCKS = 12.0;
    /** 1.14.7 (P0-8b): запас сверх ttl для ванильного limited-lifetime бэкстопа. */
    private static final int TTL_BACKSTOP_SECONDS = 30;

    private final RaskolClasses plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey idKey;
    private final Map<UUID, PetState> pets = new ConcurrentHashMap<>();

    private static final class PetState {
        UUID petUuid;
        String defId;
        long expiresAt;
        long buffUntil;
        long nextAttackTick;
        long frenzyUntilTick;
        long frenzyIcdUntilTick;
        boolean cadenceDisabled;
        boolean glow;
        double baseSpeed;
        /** 1.15.7 (2.6-68): последняя известная позиция пета для различения
         *  «чанк выгружен» / «пет тихо удалён». */
        UUID worldUuid;

        int lastX;
        int lastZ;
    }

    private static Attribute attr(String minecraftId) {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ATTRIBUTE)
                .get(NamespacedKey.minecraft(minecraftId));
    }

    private static final Attribute MAX_HEALTH = attr("max_health");
    private static final Attribute ATTACK_DAMAGE = attr("attack_damage");
    private static final Attribute MOVEMENT_SPEED = attr("movement_speed");

    /**
     * Конструктор без побочных эффектов (1.14.13, Волна 13B): только инициализация
     * полей и проверка резолва атрибутов. Регистрацию слушателей и запуск задач
     * выполняет владелец сервиса (RaskolClasses.onEnable).
     */
    public PetService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "rc_pet_owner");
        this.idKey = new NamespacedKey(plugin, "rc_pet_id");
        if (MAX_HEALTH == null || ATTACK_DAMAGE == null || MOVEMENT_SPEED == null) {
            LOGGER.warning("PetService: атрибуты реестра не резолвятся (max_health="
                    + (MAX_HEALTH != null) + ", attack_damage=" + (ATTACK_DAMAGE != null)
                    + ", movement_speed=" + (MOVEMENT_SPEED != null)
                    + ") — петы будут спавниться с ванильными статами");
        }
    }

    /** 1.14.13 (Волна 13B): регистрация слушателей — вызов из RaskolClasses.onEnable. */
    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 1.14.13 (Волна 13B): тик-задача жизненного цикла петов; ручка в activeTasks владельца. */
    public BukkitTask startTickTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Планирует частый cadence tick; lifecycle tick остаётся раз в секунду. */
    public BukkitTask startAttackTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPetAttacks, 1L, 1L);
    }

    /**
     * 1.14.13 (Волна 13B): отложенный startup-скан осиротевших петов (после загрузки
     * миров, +100 тиков); ручка в activeTasks владельца (одноразовая задача).
     */
    public BukkitTask scheduleOrphanScan() {
        return plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> scanOrphans(plugin.getServer().getWorlds().stream()
                                .flatMap(w -> java.util.Arrays.stream(w.getLoadedChunks()))
                                .toArray(Chunk[]::new)),
                        100L);
    }

    /** 1.14.6-fix (P0-8f): для selftest-чека 112. */
    public boolean attributesReady() {
        return MAX_HEALTH != null && ATTACK_DAMAGE != null && MOVEMENT_SPEED != null;
    }

    public PetDef defOf(String id) {
        return PetDef.byId(id);
    }

    public boolean hasPet(Player owner, String defId) {
        PetState st = pets.get(owner.getUniqueId());
        if (st == null) {
            return false;
        }
        Entity e = plugin.getServer().getEntity(st.petUuid);
        return e != null && !e.isDead() && (defId == null || defId.equals(st.defId));
    }

    public LivingEntity petOf(Player owner) {
        PetState st = pets.get(owner.getUniqueId());
        if (st == null) {
            return null;
        }
        Entity e = plugin.getServer().getEntity(st.petUuid);
        return e instanceof LivingEntity le && !le.isDead() ? le : null;
    }

    public SummonResult summon(Player owner, String defId, LivingEntity initialTarget) {
        PetDef def = PetDef.byId(defId);
        if (def == null) {
            return SummonResult.UNKNOWN;
        }
        UUID ouuid = owner.getUniqueId();
        PetState existing = pets.get(ouuid);
        if (existing != null) {
            Entity old = plugin.getServer().getEntity(existing.petUuid);
            if (old != null && !old.isDead()) {
                // 1.14.7 (P0-8c): пет жив, но в другом мире — переносим, а не отказываем
                if (!old.getWorld().equals(owner.getWorld())) {
                    Location dest = safeSpot(owner);
                    if (dest != null) {
                        old.teleport(dest);
                        owner.sendMessage(Component.text(
                                "«"
                                        + PetDef.byId(existing.defId).displayName(owner.getName())
                                        + "» призван к тебе из другого мира.",
                                NamedTextColor.GRAY));
                        return SummonResult.OK;
                    }
                }
                owner.sendMessage(Component.text(
                        "Питомец уже призван: «" + PetDef.byId(existing.defId).displayName(owner.getName()) + "».",
                        NamedTextColor.GRAY));
                return SummonResult.ALREADY;
            }
            // 1.15.7 (2.6-68): сущность недоступна. Если чанк последней известной
            // позиции загружен — пет удалён без события смерти: чистим handle сразу
            // и позволяем перепризыв (tick-гард ниже страхует параллельные гонки).
            if (old == null) {
                World lastWorld =
                        existing.worldUuid != null ? plugin.getServer().getWorld(existing.worldUuid) : null;
                boolean chunkLoaded =
                        lastWorld != null && lastWorld.isChunkLoaded(existing.lastX >> 4, existing.lastZ >> 4);
                if (chunkLoaded) {
                    pets.remove(ouuid);
                } else {
                    owner.sendMessage(Component.text(
                            "Питомец где-то далеко (чанк выгружен): "
                                    + "дождись загрузки или дозови его повторным призывом в том же мире.",
                            NamedTextColor.GRAY));
                    return SummonResult.ALREADY;
                }
            } else {
                pets.remove(ouuid);
            }
        }

        double hp = PetMath.hp(
                def.baseHp(), attrValue(owner, def), plugin.getSpec2Service().petHpPercent(ouuid));
        double dmg = PetTalentMath.runtimeBaseDamage(def.baseDmg(), powerValue(owner, def), 1.0);

        Location spawn = owner.getLocation().add(1.0, 0.0, 1.0);
        Entity spawned = owner.getWorld().spawnEntity(spawn, def.entityType());
        if (!(spawned instanceof LivingEntity pet)) {
            spawned.remove();
            return SummonResult.UNKNOWN;
        }

        pet.setCustomName(def.displayName(owner.getName()));
        pet.setCustomNameVisible(true);
        pet.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ouuid.toString());
        pet.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        if (pet instanceof Wolf w) {
            w.setTamed(true);
            w.setOwner(owner);
            w.setSitting(false);
        }
        // 1.14.7 (P0-8b): ванильный бэкстоп ttl — если handle потеряется (краш),
        // Vex всё равно погаснет сам вместо вечной жизни
        if (def.temporary() && spawned instanceof org.bukkit.entity.Vex vex) {
            vex.setLimitedLifetime(true);
            vex.setLimitedLifetimeTicks((def.ttlSeconds() + TTL_BACKSTOP_SECONDS) * 20);
        }
        setMaxHealth(pet, hp);
        setAttackDamage(pet, dmg);
        if (pet.getHealth() < hp) {
            pet.setHealth(hp);
        }
        if (pet instanceof Mob m && initialTarget != null && plugin.getCombat().canHit(owner, initialTarget)) {
            m.setTarget(initialTarget);
        }

        PetState st = new PetState();
        st.petUuid = pet.getUniqueId();
        st.defId = def.id();
        st.expiresAt = def.temporary() ? System.currentTimeMillis() + def.ttlSeconds() * 1000L : 0L;
        AttributeInstance ms = pet.getAttribute(MOVEMENT_SPEED);
        st.baseSpeed = ms != null ? ms.getBaseValue() : 0.0;
        // 1.15.7 (2.6-68): старт последней известной позиции
        st.worldUuid = pet.getWorld().getUID();
        st.lastX = pet.getLocation().getBlockX();
        st.lastZ = pet.getLocation().getBlockZ();
        pets.put(ouuid, st);

        pet.getWorld().spawnParticle(Particle.POOF, pet.getLocation().add(0.0, 0.8, 0.0), 12, 0.4, 0.5, 0.4, 0.02);
        return SummonResult.OK;
    }

    public boolean buff(Player owner, double dmgMult, double speedMult, boolean glow, int seconds) {
        PetState st = pets.get(owner.getUniqueId());
        LivingEntity pet = petOf(owner);
        if (st == null || pet == null) {
            owner.sendMessage(Component.text("Бафф не применим: питомца нет рядом.", NamedTextColor.GRAY));
            return false;
        }
        PetDef def = PetDef.byId(st.defId);
        // 1.15.7 (spotbugs URF): статы ставятся СРАЗУ из параметров; мёртвые поля
        // dmgMult/speedMult удалены из PetState.
        st.glow = glow;
        st.buffUntil = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        double dmg = PetTalentMath.runtimeBaseDamage(def.baseDmg(), powerValue(owner, def), dmgMult);
        setAttackDamage(pet, dmg);
        setSpeed(pet, st.baseSpeed * speedMult);
        pet.setGlowing(glow);
        return true;
    }

    public boolean consume(Player owner, String defId) {
        PetState st = pets.get(owner.getUniqueId());
        if (st == null || (defId != null && !defId.equals(st.defId))) {
            return false;
        }
        LivingEntity pet = petOf(owner);
        pets.remove(owner.getUniqueId());
        if (pet != null) {
            pet.getWorld()
                    .spawnParticle(Particle.SCULK_SOUL, pet.getLocation().add(0.0, 0.8, 0.0), 16, 0.4, 0.5, 0.4, 0.02);
            pet.remove();
        }
        return true;
    }

    /**
     * 1.15.7 (Волна 7d, аудит 2.6-9): дезспавн всех петов при выгрузке плагина.
     * Вызывается из RaskolClasses.onDisable ДО отмены задач.
     */
    public void despawnAll() {
        for (Map.Entry<UUID, PetState> entry : pets.entrySet()) {
            Entity e = plugin.getServer().getEntity(entry.getValue().petUuid);
            if (e instanceof LivingEntity le) {
                despawn(le);
            }
        }
        pets.clear();
    }

    private double attrValue(Player owner, PetDef def) {
        AttributeType type = "int".equals(def.hpAttr()) ? AttributeType.INT : AttributeType.STR;
        return plugin.getAttributes().value(owner.getUniqueId(), type);
    }

    private double powerValue(Player owner, PetDef def) {
        return plugin.getCombat().powers().powerFor(owner.getUniqueId(), def.powerTag());
    }

    private void setMaxHealth(LivingEntity pet, double hp) {
        if (MAX_HEALTH == null) {
            return;
        }
        AttributeInstance inst = pet.getAttribute(MAX_HEALTH);
        if (inst != null) {
            inst.setBaseValue(Math.max(1.0, hp));
        }
    }

    private void setAttackDamage(LivingEntity pet, double dmg) {
        if (ATTACK_DAMAGE == null) {
            return;
        }
        AttributeInstance inst = pet.getAttribute(ATTACK_DAMAGE);
        if (inst != null) {
            inst.setBaseValue(Math.max(0.5, dmg));
        }
    }

    private void setSpeed(LivingEntity pet, double speed) {
        if (MOVEMENT_SPEED == null) {
            return;
        }
        AttributeInstance inst = pet.getAttribute(MOVEMENT_SPEED);
        if (inst != null) {
            inst.setBaseValue(Math.max(0.0, speed));
        }
    }

    private void tickPetAttacks() {
        long currentTick = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, PetState> entry : pets.entrySet()) {
            UUID ownerUuid = entry.getKey();
            PetState state = entry.getValue();
            if (state.cadenceDisabled) {
                continue;
            }
            Entity entity = plugin.getServer().getEntity(state.petUuid);
            if (!(entity instanceof Mob pet) || pet.isDead()) {
                continue;
            }
            Player owner = plugin.getServer().getPlayer(ownerUuid);
            if (owner == null || !owner.isOnline()) {
                continue;
            }
            double disciplinePct =
                    PetTalentMath.speedPercent(plugin.getSpec2Service().petAttackSpeedPercent(ownerUuid));
            boolean frenzyActive = currentTick < state.frenzyUntilTick;
            if (disciplinePct <= 0.0 && !frenzyActive) {
                continue;
            }
            LivingEntity target = pet.getTarget();
            if (target == null
                    || !target.isValid()
                    || target.isDead()
                    || !pet.getWorld().equals(target.getWorld())
                    || pet.getLocation().distanceSquared(target.getLocation()) > 4.0
                    || !pet.hasLineOfSight(target)
                    || !plugin.getCombat().canHit(owner, target)
                    || currentTick < state.nextAttackTick) {
                continue;
            }
            PetDef def = PetDef.byId(state.defId);
            if (def == null) {
                continue;
            }
            double frenzyPct = frenzyActive ? 30.0 : 0.0;
            int interval = PetTalentMath.attackIntervalTicks(def.baseAttackIntervalTicks(), disciplinePct, frenzyPct);
            state.nextAttackTick = currentTick + interval;
            SCHEDULED_PET_ATTACK.set(Boolean.TRUE);
            try {
                pet.attack(target);
            } catch (RuntimeException exception) {
                state.cadenceDisabled = true;
                LOGGER.log(
                        java.util.logging.Level.WARNING,
                        "Управляемый cadence отключён для питомца " + state.defId + "; восстановлена ванильная атака",
                        exception);
            } finally {
                SCHEDULED_PET_ATTACK.remove();
            }
        }
    }

    private void triggerPetFrenzy(UUID ownerId, PetState state, long currentTick) {
        if (state == null
                || currentTick < state.frenzyIcdUntilTick
                || !rollPercent(talentChancePct(ownerId, "pet_frenzy"))) {
            return;
        }
        state.frenzyUntilTick = Math.max(state.frenzyUntilTick, currentTick + 160L);
        state.frenzyIcdUntilTick = currentTick + 20L;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PetState> entry : pets.entrySet()) {
            UUID ownerUuid = entry.getKey();
            PetState st = entry.getValue();
            Entity entity = plugin.getServer().getEntity(st.petUuid);
            // 1.15.7 (2.6-68): getEntity()==null больше не означает «ждём чанк» вслепую:
            // если чанк последней известной позиции загружен, а сущности нет —
            // пет удалён без события смерти (vex-TTL, /kill, чужой remove) → чистим handle.
            if (entity == null) {
                World lastWorld = st.worldUuid != null ? plugin.getServer().getWorld(st.worldUuid) : null;
                if (lastWorld != null && lastWorld.isChunkLoaded(st.lastX >> 4, st.lastZ >> 4)) {
                    pets.remove(ownerUuid);
                    Player ownerLost = plugin.getServer().getPlayer(ownerUuid);
                    if (ownerLost != null) {
                        ownerLost.sendMessage(Component.text(
                                "«"
                                        + PetDef.byId(st.defId).displayName(ownerLost.getName())
                                        + "» исчез бесследно. Призыв снова доступен.",
                                NamedTextColor.GRAY));
                    }
                }
                continue;
            }
            if (!(entity instanceof LivingEntity pet) || pet.isDead()) {
                pets.remove(ownerUuid);
                continue;
            }
            Player owner = plugin.getServer().getPlayer(ownerUuid);
            if (owner == null || !owner.isOnline()) {
                despawn(pet);
                pets.remove(ownerUuid);
                continue;
            }
            if (PetMath.expired(st.expiresAt, now)) {
                owner.sendMessage(Component.text(
                        "«" + PetDef.byId(st.defId).displayName(owner.getName()) + "» рассеивается.",
                        NamedTextColor.GRAY));
                despawn(pet);
                pets.remove(ownerUuid);
                continue;
            }
            if (st.buffUntil > 0L && now > st.buffUntil) {
                st.buffUntil = 0L;
                PetDef def = PetDef.byId(st.defId);
                setAttackDamage(pet, PetTalentMath.runtimeBaseDamage(def.baseDmg(), powerValue(owner, def), 1.0));
                setSpeed(pet, st.baseSpeed);
                if (st.glow) {
                    pet.setGlowing(false);
                    st.glow = false;
                }
            }
            // 1.15.7 (2.6-68): обновляем последнюю известную позицию
            st.worldUuid = pet.getWorld().getUID();
            st.lastX = pet.getLocation().getBlockX();
            st.lastZ = pet.getLocation().getBlockZ();
            // 1.14.7 (P0-8c): follow работает и МЕЖДУ мирами — телепорт к владельцу
            if (!pet.getWorld().equals(owner.getWorld())
                    || PetMath.needsTeleport(pet.getLocation().distanceSquared(owner.getLocation()), FOLLOW_BLOCKS)) {
                Location dest = safeSpot(owner);
                if (dest != null) {
                    pet.teleport(dest);
                }
            }
        }
    }

    private Location safeSpot(Player owner) {
        Location base = owner.getLocation();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}};
        for (int[] o : offsets) {
            Location cand = base.clone().add(o[0], 0.0, o[1]);
            if (cand.getBlock().isPassable()) {
                return cand;
            }
        }
        return base;
    }

    private void despawn(LivingEntity pet) {
        pet.getWorld().spawnParticle(Particle.POOF, pet.getLocation().add(0.0, 0.8, 0.0), 10, 0.4, 0.5, 0.4, 0.02);
        pet.remove();
    }

    /* ------------------------------ скан осиротевших петов ------------------------------ */

    /** Удаляет петов с меткой rc_pet_owner, чьего хозяина нет в карте handle. */
    private void scanOrphans(Chunk[] chunks) {
        for (Chunk chunk : chunks) {
            scanOrphans(List.of(chunk.getEntities()));
        }
    }

    /** 1.15.7 (2.6-67): ядро скана по списку сущностей (из EntitiesLoadEvent или чанка). */
    private void scanOrphans(List<Entity> entities) {
        int removed = 0;
        for (Entity e : entities) {
            // 1.14.12 (Волна 12C, 5-06): PDC только у LivingEntity
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            String ownerRaw = le.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (ownerRaw == null) {
                continue;
            }
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            PetState st = pets.get(ownerUuid);
            boolean owned = st != null && st.petUuid.equals(e.getUniqueId());
            if (!owned) {
                e.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("PetService: удалено осиротевших петов после скана — " + removed);
        }
    }

    /**
     * 1.15.7 (2.6-67): EntitiesLoadEvent вместо ChunkLoadEvent — список сущностей
     * приходит в самом событии, без повторного chunk.getEntities() и без риска
     * сканировать чанк до прикрепления сущностей.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        scanOrphans(event.getEntities());
    }

    /* ------------------------------ события ------------------------------ */

    /** A1: смерть пета — чистка handle + сообщение владельцу. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetDeath(EntityDeathEvent event) {
        LivingEntity pet = event.getEntity();
        // 1.14.12 (Волна 12C, 5-06): PDC только у LivingEntity (EntityDeathEvent и так LivingEntity)
        String ownerRaw = pet.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerRaw == null) {
            return;
        }
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }
        PetState st = pets.remove(ownerUuid);
        Player owner = plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && st != null) {
            PetDef def = PetDef.byId(st.defId);
            owner.sendMessage(Component.text(
                    "«" + def.displayName(owner.getName())
                            + "» пал. Призыв снова доступен после перезарядки способности.",
                    NamedTextColor.RED));
        }
    }

    /** Выход владельца — дезспавн (A1: без сохранения между сессиями). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PetState st = pets.remove(uuid);
        if (st == null) {
            return;
        }
        Entity pet = plugin.getServer().getEntity(st.petUuid);
        if (pet instanceof LivingEntity le) {
            despawn(le);
        }
    }

    /** Боевые таланты питомца: защита, крит и физическое усиление. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (SUPPRESS_PET_TALENTS.get() || !(event.getDamager() instanceof LivingEntity pet)) {
            return;
        }
        UUID ownerId = petOwnerId(pet);
        if (ownerId == null) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null || !(event.getEntity() instanceof LivingEntity target)) {
            event.setCancelled(true);
            return;
        }
        PetState state = pets.get(ownerId);
        long currentTick = Bukkit.getCurrentTick();
        boolean cadenceManaged = PetTalentMath.cadenceManaged(
                plugin.getSpec2Service().petAttackSpeedPercent(ownerId),
                state != null && currentTick < state.frenzyUntilTick,
                state == null || state.cadenceDisabled);
        if (cadenceManaged && !SCHEDULED_PET_ATTACK.get()) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.getCombat().canHit(owner, target)) {
            event.setCancelled(true);
            return;
        }

        double physicalBonus = plugin.getSpec2Service().petDmgPercent(ownerId) + talentPct(ownerId, "pet_phys_dmg_pct");
        boolean critical = rollPercent(talentPct(ownerId, "pet_crit_pct"));
        if (critical) {
            triggerPetFrenzy(ownerId, state, currentTick);
        }
        boolean empoweredCritical = critical && rollPercent(talentChancePct(ownerId, "pet_crit_bonus"));
        if (Double.isFinite(event.getDamage()) && event.getDamage() > 0.0) {
            event.setDamage(
                    PetTalentMath.outgoingDamage(event.getDamage(), physicalBonus, critical, empoweredCritical));
        }
    }

    /** Уклонение питомца проверяется на входящем уроне любого типа. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetIncomingDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity pet)) {
            return;
        }
        UUID ownerId = petOwnerId(pet);
        if (ownerId == null || plugin.getServer().getPlayer(ownerId) == null) {
            return;
        }
        if (rollPercent(talentPct(ownerId, "pet_dodge_pct"))) {
            event.setCancelled(true);
        }
    }

    /** Дикая связь: питомец повторяет часть уже рассчитанного урона хозяина. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerDamageCopy(EntityDamageByEntityEvent event) {
        if (SUPPRESS_PET_TALENTS.get()
                || !(event.getDamager() instanceof Player owner)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LivingEntity pet = petOf(owner);
        if (pet == null
                || !plugin.getCombat().canHit(owner, target)
                || !rollPercent(talentChancePct(owner.getUniqueId(), "pet_copy"))) {
            return;
        }
        double copiedDamage = event.getFinalDamage() * 0.10;
        if (!Double.isFinite(copiedDamage) || copiedDamage <= 0.0) {
            return;
        }
        SUPPRESS_PET_TALENTS.set(Boolean.TRUE);
        try {
            target.damage(copiedDamage, pet);
        } finally {
            SUPPRESS_PET_TALENTS.remove();
        }
    }

    /** Духовная связь: лечение хозяина с шансом лечит его питомца на половину объёма. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player owner)) {
            return;
        }
        LivingEntity pet = petOf(owner);
        if (pet == null || !rollPercent(talentChancePct(owner.getUniqueId(), "pet_heal"))) {
            return;
        }
        AttributeInstance maxHealth = pet.getAttribute(MAX_HEALTH);
        if (maxHealth == null || !Double.isFinite(event.getAmount()) || event.getAmount() <= 0.0) {
            return;
        }
        double heal = event.getAmount() * 0.50;
        pet.setHealth(Math.min(maxHealth.getValue(), pet.getHealth() + heal));
    }

    public UUID ownerIdOf(LivingEntity pet) {
        return pet == null ? null : petOwnerId(pet);
    }

    private UUID petOwnerId(LivingEntity pet) {
        String raw = pet.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private double talentPct(UUID ownerId, String effectId) {
        double value = plugin.getSpec2Service().procBonus(ownerId, effectId);
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }

    private double talentChancePct(UUID ownerId, String effectId) {
        return PetTalentMath.chancePercentFromFraction(plugin.getSpec2Service().procBonus(ownerId, effectId));
    }

    private static boolean rollPercent(double chance) {
        double bounded = PetTalentMath.chance(chance);
        return bounded > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < bounded;
    }

    /** A4: пет не бьёт союзников/владельца; ретаргет пета на цель владельца. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (SUPPRESS_PET_TALENTS.get()) {
            return;
        }
        Entity damager = event.getDamager();
        // 1.14.12 (Волна 12C, 5-06): PDC только у LivingEntity
        if (!(damager instanceof LivingEntity damagerLiving)) {
            return;
        }
        String ownerRaw = damagerLiving.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerRaw != null) {
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ex) {
                return;
            }
            Player owner = plugin.getServer().getPlayer(ownerUuid);
            if (owner == null) {
                event.setCancelled(true);
                return;
            }
            if (event.getEntity().getUniqueId().equals(ownerUuid)
                    || (event.getEntity() instanceof Player victim
                            && !plugin.getCombat().canHit(owner, victim))) {
                event.setCancelled(true);
            }
            return;
        }
        if (!(damager instanceof Player owner)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LivingEntity pet = petOf(owner);
        if (pet == null || !plugin.getCombat().canHit(owner, target)) {
            return;
        }
        if (pet instanceof Mob m) {
            Entity currentTarget = m.getTarget();
            if (currentTarget == null || currentTarget.isDead()) {
                m.setTarget(target);
            }
        }
    }

    /** A4: пет не таргетит владельца и союзников. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Entity pet = event.getEntity();
        // 1.14.12 (Волна 12C, 5-06): PDC только у LivingEntity
        if (!(pet instanceof LivingEntity petLiving)) {
            return;
        }
        String ownerRaw = petLiving.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerRaw == null || !(event.getTarget() instanceof Player target)) {
            return;
        }
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (target.getUniqueId().equals(ownerUuid)) {
            event.setCancelled(true);
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && !plugin.getCombat().canHit(owner, target)) {
            event.setCancelled(true);
        }
    }
}
