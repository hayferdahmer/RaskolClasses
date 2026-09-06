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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Фреймворк инсталляций (1.5.0, Пакет 2):
 *  - постановка: лимит 2 на игрока, TTL 60 с, анлок 50 (админ-байпас),
 *    запрет у спавна (радиус из конфига) и в клеймах Towny (рефлексия);
 *  - MINE: свип каждые 10 тиков, триггер по врагу/мобу в 1.2 блока → эффект → расход;
 *  - ZONE: тик раз в секунду до TTL (ауры для союзников);
 *  - визуал: ItemDisplay + партиклы + звук; союзность — через FactionHook.
 * FIX 1.5.0.2: ITEM_ARMOR_STAND_PLACE нет в Paper 1.21.4 — заменён на
 * BLOCK_WOOD_PLACE (нейтральный звук установки).
 */
public final class InstallationService {

    private final RaskolClasses plugin;
    private final List<Installation> active = new CopyOnWriteArrayList<>();

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

        // лимит активных
        int max = plugin.getConfig().getInt("installations.max-per-player", 2);
        if (countOf(uuid) >= max) {
            player.sendMessage(Component.text("Лимит активных инсталляций: " + max,
                    NamedTextColor.RED));
            return false;
        }

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
        Installation inst = new Installation(uuid, type, loc, System.currentTimeMillis() + ttl);
        spawnDisplay(inst);
        active.add(inst);

        // FIX 1.5.0.2: BLOCK_WOOD_PLACE вместо несуществующего ITEM_ARMOR_STAND_PLACE
        plugin.getFx().playSound(loc, Sound.BLOCK_WOOD_PLACE, 0.6f, 1.0f);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.CLOUD,
                    loc.clone().add(0.5, 0.4, 0.5), 10, 0.4, 0.3, 0.4, 0.0);
        }
        player.sendMessage(Component.text("Инсталляция установлена: ", NamedTextColor.GREEN)
                .append(Component.text(type.displayName(), pc.getColor()))
                .append(Component.text(" · живёт "
                        + (ttl / 1000L) + " с", NamedTextColor.GRAY)));
        return true;
    }

    /** Свип: сроки, зоны, триггеры мин. */
    public BukkitTask startSweepTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 10L, 10L);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        for (Installation inst : active) {
            if (now >= inst.getExpiresAt()) {
                despawn(inst);
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
                    despawn(inst);
                    active.remove(inst);
                }
            }
        }
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

    private void despawn(Installation inst) {
        if (inst.getDisplay() != null && !inst.getDisplay().isDead()) {
            Location loc = inst.getDisplay().getLocation();
            inst.getDisplay().remove();
            if (loc != null && loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.CLOUD,
                        loc.clone().add(0.0, 0.3, 0.0), 6, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    /** Рестарт/выключение: убрать все дисплеи. */
    public void shutdown() {
        for (Installation inst : active) {
            despawn(inst);
        }
        active.clear();
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
