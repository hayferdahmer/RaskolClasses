// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.pet;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.14.6: ЕДИНЫЙ сервис боевых петов (wolf / demon / shadowfiend).
 * Заменяет ad-hoc static-карты китов (PET_WOLF, DEMON_BY_OWNER, Vex-инлайн).
 *
 * Жизненный цикл:
 *  - summon: 1 пет на владельца; спавн рядом, имя видно (A3), PDC-метки
 *    rc_pet_owner / rc_pet_id для атрибуции; статы через PetMath (A2);
 *    wolf — приручён и не сидит; ttl-петы (demon/shadowfiend) истекают в tick.
 *  - tick (20 т): истечение ttl → дезспавн с пуфом; follow > 12 блоков →
 *    телепорт в безопасную точку; окончание баффа → возврат статов;
 *    мёртвая/удалённая сущность → чистка handle.
 *  - смерть пета (A1): handle чистится, владельцу сообщение; ресаммон —
 *    после КД способности (КД живёт в ките, здесь не дублируется).
 *  - выход владельца: дезспавн без сообщения.
 *
 * Боевые гейты (A4):
 *  - урон пета по игроку отменяется, если combat.canHit(owner, victim) = false;
 *  - EntityTargetEvent на игроков-союзников отменяется;
 *  - ретаргет: владелец ударил цель → пет получает её, если canHit пройден.
 *
 * Баффы: buff(owner, dmgMult, speedMult, glow, seconds) — beast_ferocity /
 * bestial_wrath; consume(owner, defId) — demon_soul (поглощение демона).
 *
 * 1.14.6-fix: импорт Attribute — org.bukkit.attribute.Attribute (Paper 1.21.4);
 * спавн через World.spawnEntity(Location, EntityType) + явный cast к LivingEntity
 * (World.spawn с Class<T> не подходит из-за wildcard в getEntityClass()).
 */
public final class PetService implements Listener {

    /** Результат попытки призыва. */
    public enum SummonResult { OK, ALREADY, UNKNOWN, NO_WORLD }

    private static final double FOLLOW_BLOCKS = 12.0;

    private final RaskolClasses plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey idKey;
    private final Map<UUID, PetState> pets = new ConcurrentHashMap<>();

    private static final class PetState {
        UUID petUuid;
        String defId;
        long expiresAt;      // 0 = постоянный
        long buffUntil;      // 0 = без баффа
        double dmgMult = 1.0;
        double speedMult = 1.0;
        boolean glow;
        double baseSpeed;    // базовая movement_speed сущности на спавне
    }

    public PetService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "rc_pet_owner");
        this.idKey = new NamespacedKey(plugin, "rc_pet_id");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* ------------------------------ реестр атрибутов ------------------------------ */

    private static Attribute attr(String minecraftId) {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ATTRIBUTE)
                .get(NamespacedKey.minecraft(minecraftId));
    }

    private static final Attribute MAX_HEALTH = attr("max_health");
    private static final Attribute ATTACK_DAMAGE = attr("attack_damage");
    private static final Attribute MOVEMENT_SPEED = attr("movement_speed");

    /* ------------------------------ публичный API ------------------------------ */

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

    /** Призыв пета. initialTarget = null для компаньона (wolf). */
    public SummonResult summon(Player owner, String defId, LivingEntity initialTarget) {
        PetDef def = PetDef.byId(defId);
        if (def == null) {
            return SummonResult.UNKNOWN;
        }
        if (owner.getWorld() == null) {
            return SummonResult.NO_WORLD;
        }
        UUID ouuid = owner.getUniqueId();
        PetState existing = pets.get(ouuid);
        if (existing != null) {
            Entity old = plugin.getServer().getEntity(existing.petUuid);
            if (old != null && !old.isDead()) {
                owner.sendMessage(Component.text("Питомец уже призван: «"
                        + PetDef.byId(existing.defId).displayName(owner.getName()) + "».",
                        NamedTextColor.GRAY));
                return SummonResult.ALREADY;
            }
            pets.remove(ouuid);
        }

        UUID uuid = ouuid;
        double hp = PetMath.hp(def.baseHp(), attrValue(owner, def),
                plugin.getSpec2Service().petHpPercent(uuid));
        double dmg = PetMath.damage(def.baseDmg(), powerValue(owner, def),
                plugin.getSpec2Service().petDmgPercent(uuid), 1.0);

        Location spawn = owner.getLocation().add(1.0, 0.0, 1.0);
        Entity spawned = owner.getWorld().spawnEntity(spawn, def.entityType());
        if (!(spawned instanceof LivingEntity pet)) {
            spawned.remove();
            return SummonResult.UNKNOWN;
        }

        pet.setCustomName(def.displayName(owner.getName()));
        pet.setCustomNameVisible(true); // A3
        pet.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, uuid.toString());
        pet.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        if (pet instanceof Wolf w) {
            w.setTamed(true);
            w.setOwner(owner);
            w.setSitting(false);
        }
        setMaxHealth(pet, hp);
        setAttackDamage(pet, dmg);
        if (pet.getHealth() < hp) {
            pet.setHealth(hp);
        }
        if (pet instanceof Mob m && initialTarget != null
                && plugin.getCombat().canHit(owner, initialTarget)) {
            m.setTarget(initialTarget);
        }

        PetState st = new PetState();
        st.petUuid = pet.getUniqueId();
        st.defId = def.id();
        st.expiresAt = def.temporary()
                ? System.currentTimeMillis() + def.ttlSeconds() * 1000L
                : 0L;
        AttributeInstance ms = pet.getAttribute(MOVEMENT_SPEED);
        st.baseSpeed = ms != null ? ms.getBaseValue() : 0.0;
        pets.put(ouuid, st);

        pet.getWorld().spawnParticle(Particle.POOF,
                pet.getLocation().add(0.0, 0.8, 0.0), 12, 0.4, 0.5, 0.4, 0.02);
        return SummonResult.OK;
    }

    /** beast_ferocity / bestial_wrath: множители урона/скорости + подсветка на seconds. */
    public boolean buff(Player owner, double dmgMult, double speedMult, boolean glow, int seconds) {
        PetState st = pets.get(owner.getUniqueId());
        LivingEntity pet = petOf(owner);
        if (st == null || pet == null) {
            owner.sendMessage(Component.text("Бафф не применим: питомца нет рядом.",
                    NamedTextColor.GRAY));
            return false;
        }
        PetDef def = PetDef.byId(st.defId);
        st.dmgMult = dmgMult;
        st.speedMult = speedMult;
        st.glow = glow;
        st.buffUntil = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        double dmg = PetMath.damage(def.baseDmg(), powerValue(owner, def),
                plugin.getSpec2Service().petDmgPercent(owner.getUniqueId()), dmgMult);
        setAttackDamage(pet, dmg);
        setSpeed(pet, st.baseSpeed * speedMult);
        pet.setGlowing(glow);
        return true;
    }

    /** demon_soul: поглотить пета (дезспавн); true если пет был жив. */
    public boolean consume(Player owner, String defId) {
        PetState st = pets.get(owner.getUniqueId());
        if (st == null || (defId != null && !defId.equals(st.defId))) {
            return false;
        }
        LivingEntity pet = petOf(owner);
        pets.remove(owner.getUniqueId());
        if (pet != null) {
            pet.getWorld().spawnParticle(Particle.SCULK_SOUL,
                    pet.getLocation().add(0.0, 0.8, 0.0), 16, 0.4, 0.5, 0.4, 0.02);
            pet.remove();
        }
        return true;
    }

    /* ------------------------------ статы ------------------------------ */

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

    /* ------------------------------ tick ------------------------------ */

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PetState> entry : pets.entrySet()) {
            UUID ownerUuid = entry.getKey();
            PetState st = entry.getValue();
            Entity entity = plugin.getServer().getEntity(st.petUuid);
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
            // ttl (demon / shadowfiend)
            if (PetMath.expired(st.expiresAt, now)) {
                owner.sendMessage(Component.text("«" + PetDef.byId(st.defId).displayName(owner.getName())
                        + "» рассеивается.", NamedTextColor.GRAY));
                despawn(pet);
                pets.remove(ownerUuid);
                continue;
            }
            // окончание баффа
            if (st.buffUntil > 0L && now > st.buffUntil) {
                st.buffUntil = 0L;
                st.dmgMult = 1.0;
                st.speedMult = 1.0;
                PetDef def = PetDef.byId(st.defId);
                setAttackDamage(pet, PetMath.damage(def.baseDmg(), powerValue(owner, def),
                        plugin.getSpec2Service().petDmgPercent(ownerUuid), 1.0));
                setSpeed(pet, st.baseSpeed);
                if (st.glow) {
                    pet.setGlowing(false);
                    st.glow = false;
                }
            }
            // follow: телепорт к владельцу, если отстал
            if (pet.getWorld().equals(owner.getWorld())
                    && PetMath.needsTeleport(
                            pet.getLocation().distanceSquared(owner.getLocation()), FOLLOW_BLOCKS)) {
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
        return null;
    }

    private void despawn(LivingEntity pet) {
        pet.getWorld().spawnParticle(Particle.POOF,
                pet.getLocation().add(0.0, 0.8, 0.0), 10, 0.4, 0.5, 0.4, 0.02);
        pet.remove();
    }

    /* ------------------------------ события ------------------------------ */

    /** A1: смерть пета — чистка handle + сообщение владельцу. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetDeath(EntityDeathEvent event) {
        LivingEntity pet = event.getEntity();
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
            owner.sendMessage(Component.text("«" + def.displayName(owner.getName())
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

    /** A4: пет не бьёт союзников/владельца; ретаргет пета на цель владельца. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        String ownerRaw = damager.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerRaw != null) {
            // урон ОТ пета: гейт союзников
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
        // ретаргет: владелец ударил цель → пет переключается
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
        if (pet instanceof Mob m && (m.getTarget() == null || m.getTarget().isDead())) {
            m.setTarget(target);
        }
    }

    /** A4: пет не таргетит владельца и союзников. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Entity pet = event.getEntity();
        String ownerRaw = pet.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
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
