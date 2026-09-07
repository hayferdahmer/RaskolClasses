// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.spec.TrapVisual;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Фреймворк инсталляций (1.5.0 + 1.5.2 + 1.5.7).
 * 1.5.7: адаптивный свип — если активных инсталляций нет, таск не делает
 * ничего (раньше каждый тик 10 крутил пустой цикл и чистил lastPlace);
 * чистка lastPlace переехала в purgeStale() (общий purge-таск, 1200 тиков).
 */
public final class InstallationService {

    private final RaskolClasses plugin;
    private final List<Installation> active = new CopyOnWriteArrayList<>();
    /** анти-спам постановки: uuid -> timestamp последней постановки. */
    private final Map<UUID, Long> lastPlace = new ConcurrentHashMap<>();

    public InstallationService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public int countOf(UUID owner) {
        int count = 0;
        for (Installation inst : active) {
            if (inst.getOwner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** 1.5.2: сколько активных инсталляций на всём сервере. */
    public int countGlobal() {
        return active.size();
    }

    /** 1.5.2: копия списка для /rc debug. */
    public List<Installation> snapshot() {
        return new ArrayList<>(active);
    }

    /** Попытка поставить инсталляцию своего класса. Все проверки и сообщения здесь. */
    public boolean tryPlace(Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(
                    "Класс не выбран — посетите герольда", NamedTextColor.GRAY));
            return false;
        }
        InstallationType type = InstallationType.forClass(pc);
        if (type == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();

        // анлок 50 (админ-байпас; без AuraSkills не блокируем)
        if (!player.hasPermission("raskolclasses.admin")) {
            int unlock = plugin.getConfig().getInt("installations.unlock-level", 50);
            int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
            if (level != SkillLevelProvider.NO_SKILL_SYSTEM && level < unlock) {
                player.sendMessage(Component.text("Инсталляции откроются на уровне "
                        + unlock + " (" + pc.profileSkillName() + ")", NamedTextColor.RED));
                return false;
            }
        }

        // лимит на игрока
        int max = plugin.getConfig().getInt("installations.max-per-player", 2);
        if (countOf(uuid) >= max) {
            player.sendMessage(Component.text("Лимит активных инсталляций: " + max,
                    NamedTextColor.RED));
            return false;
        }

        // 1.5.2: глобальный кап
        int maxGlobal = plugin.getConfig().getInt("installations.max-global", 200);
        if (active.size() >= maxGlobal) {
            player.sendMessage(Component.text(
                    "Земля насыщена инсталляциями: глобальный лимит " + maxGlobal
                            + ". Подожди, пока истечёт чужой TTL.",
                    NamedTextColor.RED));
            return false;
        }

        // 1.5.2: анти-спам постановки
        long now = System.currentTimeMillis();
        int window = plugin.getConfig().getInt("installations.place-anti-spam-ms", 1000);
        Long prev = lastPlace.get(uuid);
        if (prev != null && now - prev < window) {
            player.sendMessage(Component.text("Слишком часто: пауза между постановками "
                    + (window / 1000L) + " с", NamedTextColor.GRAY));
            return false;
        }
        lastPlace.put(uuid, now);

        Location loc = player.getLocation();

        // защита спавна
        int deny = plugin.getConfig().getInt("installations.deny-radius-spawn", 100);
        if (loc.getWorld() != null
                && loc.getWorld().getSpawnLocation().distanceSquared(loc) < (long) deny * deny) {
            player.sendMessage(Component.text("Нельзя ставить инсталляции рядом со спавном.",
                    NamedTextColor.RED));
            return false;
        }

        // защита клеймов (Towny через рефлексию)
        if (plugin.getConfig().getBoolean("installations.deny-in-claims", true)
                && isInClaim(loc)) {
            player.sendMessage(Component.text("Нельзя ставить инсталляции на заклэймленной земле.",
                    NamedTextColor.RED));
            return false;
        }

        long ttl = plugin.getConfig().getInt("installations.ttl-seconds", 60) * 1000L;
        Installation inst = new Installation(uuid, type, loc, now + ttl);
        spawnDisplay(inst);
        active.add(inst);

        plugin.getFx().playSound(loc, placeSound(type), 0.6f, 1.0f);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.CLOUD,
                    loc.clone().add(0.5, 0.4, 0.5), 10, 0.4, 0.3, 0.4, 0.0);
        }
        player.sendMessage(Component.text("Инсталляция установлена: ", NamedTextColor.GREEN)
                .append(Component.text(type.displayName(), pc.getColor()))
                .append(Component.text(" · живёт " + (ttl / 1000L) + " с",
                        NamedTextColor.GRAY)));
        return true;
    }

    /** Свип: сроки, зоны, триггеры мин. 1.5.7: адаптивный (пусто = ноль работы). */
    public BukkitTask startSweepTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 10L, 10L);
    }

    private void sweep() {
        if (active.isEmpty()) {
            return; // 1.5.7: нет инсталляций — нет работы
        }
        long now = System.currentTimeMillis();
        for (Installation inst : active) {
            if (now >= inst.getExpiresAt()) {
                notifyOwner(inst, "⚙ " + inst.getType().displayName() + ": истекла");
                despawn(inst, true);
                active.remove(inst);
                continue;
            }
            if (inst.getType().mode() == InstallationType.Mode.ZONE) {
                if (now - inst.getLastTick() >= 1000L) {
                    inst.setLastTick(now);
                    zoneTick(inst);
                }
            } else {
                Entity trigger = findTrigger(inst);
                if (trigger != null) {
                    triggerMine(inst, trigger);
                    despawn(inst, false);
                    active.remove(inst);
                }
            }
        }
    }

    /** 1.5.7: чистка анти-спам карты общим purge-таском. */
    public void purgeStale() {
        long now = System.currentTimeMillis();
        lastPlace.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
    }

    /** Враг-игрок (не владелец и не союзник) или любой моб. */
    private Entity findTrigger(Installation inst) {
        Location loc = inst.getLocation();
        for (Entity entity : loc.getNearbyEntities(1.2, 1.2, 1.2)) {
            if (entity instanceof Player p) {
                if (!p.getUniqueId().equals(inst.getOwner())
                        && !isAlly(inst.getOwner(), p.getUniqueId())) {
                    return entity;
                }
            } else if (entity instanceof Mob) {
                return entity;
            }
        }
        return null;
    }

    private void triggerMine(Installation inst, Entity trigger) {
        Location loc = inst.getLocation();
        World world = loc.getWorld();
        switch (inst.getType()) {
            case BEAR_TRAP -> {
                if (trigger instanceof LivingEntity living) {
                    living.damage(num(inst, "damage", 3.0));
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            (int) num(inst, "slow-duration", 2.0) * 20, 5));
                }
                TrapVisual.show(plugin, loc, 40);
                if (world != null) {
                    world.playSound(loc, Sound.BLOCK_TRIPWIRE_ATTACH, 0.8f, 1.2f);
                }
            }
            case FROST_RUNE -> {
                double radius = num(inst, "radius", 3.0);
                for (Entity e : loc.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof LivingEntity living && isEnemyOf(inst, e)) {
                        living.damage(num(inst, "damage", 4.0));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                                (int) num(inst, "slow-duration", 3.0) * 20, 1));
                    }
                }
                if (world != null) {
                    world.spawnParticle(Particle.SNOWFLAKE,
                            loc.clone().add(0.5, 0.4, 0.5), 24, radius * 0.6, 0.3, radius * 0.6, 0.0);
                    world.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.2f);
                }
            }
            case SMOKE_BOMB -> {
                double radius = num(inst, "radius", 3.0);
                for (Entity e : loc.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof LivingEntity living && isEnemyOf(inst, e)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                                (int) num(inst, "blind-duration", 2.0) * 20, 0));
                    }
                }
                Player owner = plugin.getServer().getPlayer(inst.getOwner());
                if (owner != null) {
                    owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                            (int) num(inst, "speed-duration", 3.0) * 20, 0));
                }
                if (world != null) {
                    world.spawnParticle(Particle.SMOKE,
                            loc.clone().add(0.5, 0.5, 0.5), 30, 0.6, 0.4, 0.6, 0.0);
                    world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
                }
            }
            default -> { }
        }
        notifyOwner(inst, "⚙ " + inst.getType().displayName() + ": сработала на "
                + trigger.getName());
    }

    /** ZONE-тики: ауры для союзников. */
    private void zoneTick(Installation inst) {
        Location loc = inst.getLocation();
        World world = loc.getWorld();
        switch (inst.getType()) {
            case WAR_BANNER -> {
                double radius = num(inst, "radius", 6.0);
                int duration = (int) num(inst, "duration", 8.0);
                for (Entity e : loc.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof Player p
                            && (p.getUniqueId().equals(inst.getOwner())
                            || isAlly(inst.getOwner(), p.getUniqueId()))) {
                        p.addPotionEffect(new PotionEffect(
                                PotionEffectType.RESISTANCE, duration * 20, 0));
                    }
                }
                if (world != null) {
                    world.spawnParticle(Particle.FLAME,
                            loc.clone().add(0.5, 0.6, 0.5), 3, 0.2, 0.3, 0.2, 0.0);
                }
            }
            case LIGHT_WARD -> {
                double radius = num(inst, "radius", 4.0);
                double heal = num(inst, "heal", 2.0);
                for (Entity e : loc.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof Player p
                            && (p.getUniqueId().equals(inst.getOwner())
                            || isAlly(inst.getOwner(), p.getUniqueId()))) {
                        p.heal(heal);
                    }
                }
                if (world != null) {
                    world.spawnParticle(Particle.HEART,
                            loc.clone().add(0.5, 0.6, 0.5), 3, radius * 0.4, 0.3, radius * 0.4, 0.0);
                }
            }
            default -> { }
        }
    }

    /** Враг для зоны/мин: не владелец и не союзник (игрок); мобы — враги. */
    private boolean isEnemyOf(Installation inst, Entity entity) {
        if (entity instanceof Player p) {
            return !p.getUniqueId().equals(inst.getOwner())
                    && !isAlly(inst.getOwner(), p.getUniqueId());
        }
        return entity instanceof Mob;
    }

    /** Союзность через короны (FactionHook): одна фракция = союзники. */
    private boolean isAlly(UUID a, UUID b) {
        if (a.equals(b)) {
            return true;
        }
        String fa = plugin.getFactionHook().factionOf(a);
        String fb = plugin.getFactionHook().factionOf(b);
        return !fa.isEmpty() && fa.equals(fb);
    }

    /** 1.5.2: actionbar владельцу (гейт installations.notify-owner). */
    private void notifyOwner(Installation inst, String text) {
        if (!plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(inst.getOwner());
        if (owner != null) {
            owner.sendActionBar(Component.text(text, NamedTextColor.YELLOW));
        }
    }

    private void spawnDisplay(Installation inst) {
        World world = inst.getLocation().getWorld();
        if (world == null) {
            return;
        }
        ItemDisplay display = (ItemDisplay) world.spawnEntity(
                inst.getLocation().clone().add(0.5, 0.25, 0.5), EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(inst.getType().displayItem()));
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(),
                new Vector3f(1.6f, 1.6f, 1.6f),
                new AxisAngle4f()));
        display.setViewRange(24f);
        display.setPersistent(false);
        display.setInvulnerable(true);
        inst.setDisplay(display);
    }

    private void despawn(Installation inst, boolean expired) {
        if (inst.getDisplay() != null && !inst.getDisplay().isDead()) {
            Location loc = inst.getDisplay().getLocation();
            inst.getDisplay().remove();
            if (loc != null && loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.CLOUD,
                        loc.clone().add(0.0, 0.3, 0.0), 6, 0.3, 0.2, 0.3, 0.0);
                if (expired) {
                    loc.getWorld().playSound(loc, expireSound(inst.getType()), 0.5f, 1.0f);
                }
            }
        }
    }

    /** Рестарт/выключение: убрать все дисплеи. */
    public void shutdown() {
        for (Installation inst : active) {
            despawn(inst, false);
        }
        active.clear();
    }

    /** 1.5.2: звук постановки по типу. */
    private Sound placeSound(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> Sound.BLOCK_BELL_USE;
            case BEAR_TRAP -> Sound.BLOCK_TRIPWIRE_ATTACH;
            case LIGHT_WARD -> Sound.BLOCK_BEACON_ACTIVATE;
            case FROST_RUNE -> Sound.ENTITY_PLAYER_HURT_FREEZE;
            case SMOKE_BOMB -> Sound.BLOCK_FIRE_EXTINGUISH;
        };
    }

    /** 1.5.2: звук растворения по типу. */
    private Sound expireSound(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> Sound.BLOCK_WOOD_BREAK;
            case BEAR_TRAP -> Sound.BLOCK_TRIPWIRE_DETACH;
            case LIGHT_WARD -> Sound.BLOCK_BEACON_DEACTIVATE;
            case FROST_RUNE -> Sound.BLOCK_GLASS_BREAK;
            case SMOKE_BOMB -> Sound.ENTITY_GENERIC_EXTINGUISH_FIRE;
        };
    }

    /** Towny-клейм через рефлексию; без Towny или при сбое — false (разрешаем). */
    private boolean isInClaim(Location loc) {
        try {
            Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object townBlock = api.getMethod("getTownBlock", Location.class)
                    .invoke(instance, loc);
            return townBlock != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private double num(Installation inst, String key, double def) {
        return plugin.getConfig().getDouble(
                "installations." + inst.getType().id() + "." + key, def);
    }
}
