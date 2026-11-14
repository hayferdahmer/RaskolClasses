// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.fx;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitTask;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.DamageProfile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * VFX/SFX-слой (1.5.0 → 1.9.3).
 * 1.9.3: алиасы старых звуков (ENTITY_WOLF_HOWL → ENTITY_WOLF_AMBIENT,
 *        BLOCK_SNOW_BLOCK_BREAK → BLOCK_SNOW_BREAK) — фикс битых ключей в логе.
 * 1.14.12 (Sprint 8, Волна 12C, P0-3-05): ОТКАТАНО — Paper 1.21.4 не предоставляет
 *         RegistryKey.SOUNDS (звуковой реестр не экспонируется через RegistryKey,
 *         в отличие от DAMAGE_TYPE/ATTRIBUTE/ENCHANTMENT). Возврат к Sound.valueOf
 *         с try-catch и одноразовым warning — корректное поведение для Paper 1.21.4.
 *         Обоснование: Sound.valueOf бросает IllegalArgumentException на неизвестных
 *         ключах, но мы ловим его и логируем один раз через warnedSounds — hot-путь
 *         не спамит, нагрузка минимальна.
 * 1.15.6 (Волна 5, аудит 2.6-102): procByKey составляет ключ "uuid:procId" — визуал
 *         проков per-player: два игрока с одним proc_id (например, execute_passive)
 *         видят свой визуал независимо, а не один раз на двоих в течение 2 с.
 *         Глобального ключа "procId" в карте больше не появляется.
 * 1.15.6 (Волна 5, аудит 2.6-103): startAura сохраняет AuraTask локально как
 *         scheduledTask и в runTaskLater сравнивает current == scheduledTask по
 *         identity. Раньше отложенная задача старой ауры снимала ТЕКУЩУЮ запись
 *         из auras — и это была уже новая аура, которая перезатёрла старую.
 *         Теперь: если новая аура уже перезатёрла старую, отложенная задача
 *         старой видит current != scheduledTask и ничего не делает.
 */
public final class FxService implements Listener {

    private record ChargedShot(
            UUID caster, String abilityId, double phys, double magic, int fireTicks, long expiresAt) {}

    private record AuraTask(BukkitTask task, BukkitTask expirationTask, long expiresAt) {}

    private record AmbientTask(BukkitTask task, long expiresAt) {}

    private final RaskolClasses plugin;
    private final Set<String> warnedSounds = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedParticles = ConcurrentHashMap.newKeySet();
    /** 1.15.6 (2.6-102): ключ = "uuid:procId" (per-player кулдаун). */
    private final Map<String, Long> procVisualCd = new ConcurrentHashMap<>();

    private final Map<UUID, ChargedShot> chargedShots = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> trailTasks = new ConcurrentHashMap<>();
    private final Map<UUID, AuraTask> auras = new ConcurrentHashMap<>();
    private final Map<UUID, AmbientTask> ambients = new ConcurrentHashMap<>();
    private final AtomicInteger staleCount = new AtomicInteger();
    private final FxTaskRegistry taskRegistry = new FxTaskRegistry();

    public FxService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* -------------------------------- резолв -------------------------------- */

    /**
     * 1.9.3: алиасы старых звуков (Paper 1.21+ переименовал некоторые ключи).
     * 1.14.12 (Волна 12C, 3-05): Sound.valueOf с try-catch — Paper 1.21.4 не
     * предоставляет RegistryKey.SOUNDS, поэтому registry-based резолв невозможен.
     */
    public Sound resolveSound(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String mapped = switch (name.toUpperCase(Locale.ROOT)) {
            case "ENTITY_WOLF_HOWL" -> "ENTITY_WOLF_AMBIENT";
            case "BLOCK_SNOW_BLOCK_BREAK" -> "BLOCK_SNOW_BREAK";
            default -> name;
        };
        try {
            return Sound.valueOf(mapped.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            if (warnedSounds.add(name)) {
                plugin.getLogger()
                        .warning("vfx: неизвестный звук '" + name + "'"
                                + (mapped.equals(name) ? "" : " (mapped: " + mapped + ")"));
                staleCount.incrementAndGet();
            }
            return null;
        }
    }

    public Particle resolveParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            if (warnedParticles.add(name)) {
                plugin.getLogger().warning("vfx: неизвестный партикл '" + name + "'");
                staleCount.incrementAndGet();
            }
            return null;
        }
    }

    public void playSound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, volume, pitch);
        }
    }

    public void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /* ------------------------------ каст-визуал ------------------------------ */

    public void onAttempt(Player caster, String abilityId, long cooldownMillis) {
        String base = "vfx." + abilityId + ".";
        Sound sound = resolveSound(plugin.getConfig().getString(base + "cast-sound", ""));
        if (sound != null) {
            playSound(caster, sound, 0.7f, 1.0f);
        }
        Particle particle = resolveParticle(plugin.getConfig().getString(base + "cast-particle", ""));
        if (particle != null) {
            caster.spawnParticle(particle, caster.getLocation().add(0.0, 1.2, 0.0), 12, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /**
     * 1.15.6 (2.6-102): ключ кулдауна — "uuid:procId".
     * Раньше ключ был только procId, и визуал одного прока не показывался
     * второму игроку в течение 2 с (оба видели один раз на двоих).
     */
    public void procByKey(Player player, String fallbackTag, String procId) {
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + procId;
        Long prev = procVisualCd.get(key);
        if (prev != null && now - prev < 2000L) {
            return;
        }
        procVisualCd.put(key, now);
        String tag = plugin.getConfig().getString("vfx.proc." + procId + ".tag", fallbackTag);
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text(tag, NamedTextColor.YELLOW),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(40),
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofMillis(150))));
        Sound sound = resolveSound(plugin.getConfig().getString("vfx.proc." + procId + ".cast-sound", ""));
        if (sound != null) {
            playSound(player, sound, 0.5f, 1.1f);
        }
        Particle particle = resolveParticle(plugin.getConfig().getString("vfx.proc." + procId + ".cast-particle", ""));
        if (particle != null) {
            player.spawnParticle(particle, player.getLocation().add(0.0, 1.2, 0.0), 8, 0.3, 0.4, 0.3, 0.01);
        }
    }

    /* --------------------------- заряженные снаряды --------------------------- */

    public void chargeProjectile(
            UUID projectileId, UUID caster, String abilityId, double phys, double magic, int fireTicks) {
        ensureTaskSchedulingAllowed();
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            Entity e = plugin.getServer().getEntity(projectileId);
                            if (e == null || e.isDead() || !chargedShots.containsKey(projectileId)) {
                                trailTasks.remove(projectileId, holder[0]);
                                if (holder[0] != null) {
                                    taskRegistry.cancel(holder[0]);
                                }
                                return;
                            }
                            e.getWorld().spawnParticle(Particle.FLAME, e.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
                            e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation(), 1, 0.02, 0.02, 0.02, 0.005);
                        },
                        0L,
                        2L);
        taskRegistry.register(holder[0]);
        chargedShots.put(
                projectileId,
                new ChargedShot(caster, abilityId, phys, magic, fireTicks, System.currentTimeMillis() + 6000L));
        trailTasks.put(projectileId, holder[0]);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ChargedShot shot = chargedShots.remove(projectile.getUniqueId());
        if (shot == null) {
            return;
        }
        BukkitTask trail = trailTasks.remove(projectile.getUniqueId());
        if (trail != null) {
            taskRegistry.cancel(trail);
        }
        Location hitLoc = projectile.getLocation();
        String base = "vfx." + shot.abilityId() + ".";

        Particle impact = resolveParticle(plugin.getConfig().getString(base + "impact-particle", "FLAME"));
        if (impact != null && hitLoc.getWorld() != null) {
            hitLoc.getWorld().spawnParticle(impact, hitLoc, 24, 0.4, 0.3, 0.4, 0.05);
            hitLoc.getWorld().spawnParticle(Particle.SMOKE, hitLoc, 10, 0.3, 0.2, 0.3, 0.03);
        }
        Sound impactSound = resolveSound(plugin.getConfig().getString(base + "impact-sound", "ENTITY_BLAZE_SHOOT"));
        if (impactSound != null) {
            playSound(hitLoc, impactSound, 0.5f, 0.9f);
        }

        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof LivingEntity target)) {
            return;
        }
        Player caster = plugin.getServer().getPlayer(shot.caster());
        if (caster == null) {
            return;
        }
        plugin.getCombat().dealDamage(target, caster, DamageProfile.hybrid(shot.phys(), shot.magic()));
        if (shot.fireTicks() > 0 && plugin.getCombat().canHit(caster, target)) {
            target.setFireTicks(shot.fireTicks());
        }
    }

    /* --------------------------------- ауры --------------------------------- */

    /**
     * 1.15.6 (2.6-103): Identity-check в отложенной задаче.
     * scheduledTask сохраняется в локальную переменную и сравнивается по identity
     * с текущей записью auras в runTaskLater. Если новая аура уже перезатёрла
     * старую (current != scheduledTask) — отложенная задача ничего не делает.
     * Раньше auras.remove(playerUuid) убирал ТЕКУЩУЮ запись, то есть новую ауру,
     * и cancel() прерывал её — новая аура умирала раньше срока.
     */
    public void startAura(UUID playerUuid, Particle particle, int ticks, int perTick, String expireSoundKey) {
        ensureTaskSchedulingAllowed();
        stopAura(playerUuid);
        long expiresAt = System.currentTimeMillis() + ticks * 50L;
        BukkitTask task = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            Player player = plugin.getServer().getPlayer(playerUuid);
                            if (player == null || System.currentTimeMillis() > expiresAt) {
                                stopAura(playerUuid);
                                return;
                            }
                            Location center = player.getLocation().add(0.0, 0.2, 0.0);
                            ThreadLocalRandom rnd = ThreadLocalRandom.current();
                            for (int i = 0; i < perTick; i++) {
                                double angle = rnd.nextDouble() * Math.PI * 2;
                                double radius = 0.7 + rnd.nextDouble() * 0.3;
                                Location p = center.clone()
                                        .add(
                                                Math.cos(angle) * radius,
                                                rnd.nextDouble() * 1.6,
                                                Math.sin(angle) * radius);
                                player.getWorld().spawnParticle(particle, p, 1, 0.0, 0.0, 0.0, 0.0);
                            }
                        },
                        0L,
                        10L);
        taskRegistry.register(task);

        BukkitTask[] expirationHolder = new BukkitTask[1];
        try {
            expirationHolder[0] = plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                taskRegistry.release(expirationHolder[0]);
                                AuraTask current = auras.get(playerUuid);
                                if (current == null || current.task() != task) {
                                    return;
                                }
                                auras.remove(playerUuid, current);
                                taskRegistry.cancel(task);
                                Player player = plugin.getServer().getPlayer(playerUuid);
                                if (player != null && expireSoundKey != null) {
                                    Sound s = resolveSound(expireSoundKey);
                                    if (s != null) {
                                        playSound(player, s, 0.5f, 1.2f);
                                    }
                                }
                            },
                            ticks + 1L);
            taskRegistry.register(expirationHolder[0]);
        } catch (RuntimeException failure) {
            try {
                taskRegistry.cancel(task);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

        auras.put(playerUuid, new AuraTask(task, expirationHolder[0], expiresAt));
    }

    public void stopAura(UUID playerUuid) {
        AuraTask at = auras.get(playerUuid);
        if (at == null) {
            return;
        }
        RuntimeException failure = null;
        try {
            taskRegistry.cancel(at.task());
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            taskRegistry.cancel(at.expirationTask());
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = new IllegalStateException("Не удалось полностью остановить ауру");
            }
            failure.addSuppressed(exception);
        }
        if (!taskRegistry.isTracked(at.task()) && !taskRegistry.isTracked(at.expirationTask())) {
            auras.remove(playerUuid, at);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /* ------------------------------ молния/вспышки ------------------------------ */

    public void strikeLightningVisual(Location loc) {
        if (loc.getWorld() != null) {
            loc.getWorld().strikeLightningEffect(loc);
        }
    }

    public void impactBurst(Location loc, Particle main, int count, Sound sound, float volume, float pitch) {
        if (loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawnParticle(main, loc, count, 0.4, 0.4, 0.4, 0.05);
        if (sound != null) {
            playSound(loc, sound, volume, pitch);
        }
    }

    /* ------------------------------ эмбиент-лупы ------------------------------ */

    public UUID startAmbient(
            Location loc, String soundKey, float volume, float pitch, String particleKey, int ticks, int periodTicks) {
        ensureTaskSchedulingAllowed();
        UUID id = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + ticks * 50L;
        Sound sound = resolveSound(soundKey);
        Particle particle = resolveParticle(particleKey);
        BukkitTask task = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (System.currentTimeMillis() > expiresAt || loc.getWorld() == null) {
                                AmbientTask at = ambients.remove(id);
                                if (at != null) {
                                    taskRegistry.cancel(at.task());
                                }
                                return;
                            }
                            if (sound != null) {
                                playSound(loc, sound, volume, pitch);
                            }
                            if (particle != null) {
                                loc.getWorld()
                                        .spawnParticle(
                                                particle, loc.clone().add(0.0, 0.3, 0.0), 6, 0.6, 0.2, 0.6, 0.02);
                            }
                        },
                        0L,
                        Math.max(1, periodTicks));
        taskRegistry.register(task);
        ambients.put(id, new AmbientTask(task, expiresAt));
        return id;
    }

    public void stopAmbient(UUID id) {
        if (id == null) {
            return;
        }
        AmbientTask at = ambients.remove(id);
        if (at != null) {
            taskRegistry.cancel(at.task());
        }
    }

    /* ------------------------------ диагностика ------------------------------ */

    public void appendDebug(CommandSender sender, List<String> abilityIds) {
        sender.sendMessage(Component.text(
                "FxService: битых звуков " + warnedSounds.size()
                        + ", битых партиклов " + warnedParticles.size()
                        + ", снарядов " + chargedShots.size()
                        + ", аур " + auras.size() + ", эмбиентов " + ambients.size(),
                NamedTextColor.GRAY));
        for (String id : abilityIds) {
            String soundKey =
                    java.util.Objects.requireNonNullElse(plugin.getConfig().getString("vfx." + id + ".cast-sound"), "");
            String particleKey = java.util.Objects.requireNonNullElse(
                    plugin.getConfig().getString("vfx." + id + ".cast-particle"), "");
            boolean okSound = soundKey.isEmpty() || resolveSound(soundKey) != null;
            boolean okParticle = particleKey.isEmpty() || resolveParticle(particleKey) != null;
            if (!okSound || !okParticle) {
                sender.sendMessage(Component.text(
                        "  • " + id + ": "
                                + (okSound ? "" : "звук '" + soundKey + "' битый ")
                                + (okParticle ? "" : "партикл '" + particleKey + "' битый"),
                        NamedTextColor.RED));
            }
        }
    }

    public void purgeStale() {
        long now = System.currentTimeMillis();
        chargedShots.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        for (Map.Entry<UUID, BukkitTask> entry : List.copyOf(trailTasks.entrySet())) {
            if (!chargedShots.containsKey(entry.getKey())) {
                taskRegistry.cancel(entry.getValue());
                trailTasks.remove(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<UUID, AuraTask> entry : List.copyOf(auras.entrySet())) {
            if (entry.getValue().expiresAt() < now) {
                stopAura(entry.getKey());
            }
        }
        for (Map.Entry<UUID, AmbientTask> entry : List.copyOf(ambients.entrySet())) {
            if (entry.getValue().expiresAt() < now) {
                stopAmbient(entry.getKey());
            }
        }
        // 1.15.6 (2.6-102): ключи "uuid:procId" чистятся теми же правилами
        procVisualCd.entrySet().removeIf(e -> now - e.getValue() > 30_000L);
    }

    /**
     * Останавливает все задачи, принадлежащие FX-сервису.
     * Неуспешно отменённые handles остаются в реестре для повторного shutdown().
     */
    public void shutdown() {
        RuntimeException failure = null;
        try {
            taskRegistry.close();
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            chargedShots.clear();
            trailTasks.clear();
            auras.clear();
            ambients.clear();
            procVisualCd.clear();
            warnedSounds.clear();
            warnedParticles.clear();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void ensureTaskSchedulingAllowed() {
        if (taskRegistry.isClosed()) {
            throw new IllegalStateException("FxService уже остановлен");
        }
    }

    public int staleCount() {
        return staleCount.get();
    }

    public int activeCount() {
        return chargedShots.size() + auras.size() + ambients.size();
    }

    public int brokenSoundCount() {
        return warnedSounds.size() + warnedParticles.size();
    }

    public void validateConfig() {
        var section = plugin.getConfig().getConfigurationSection("vfx");
        if (section == null) {
            return;
        }
        int broken = 0;
        for (String key : section.getKeys(true)) {
            if (key.endsWith("cast-sound") || key.endsWith("impact-sound") || key.endsWith("tag-sound")) {
                String name = java.util.Objects.requireNonNullElse(section.getString(key), "");
                if (!name.isEmpty() && resolveSound(name) == null) {
                    broken++;
                }
            }
            if (key.endsWith("cast-particle") || key.endsWith("impact-particle")) {
                String name = java.util.Objects.requireNonNullElse(section.getString(key), "");
                if (!name.isEmpty() && resolveParticle(name) == null) {
                    broken++;
                }
            }
        }
        if (broken > 0) {
            plugin.getLogger().warning("vfx: битых ключей " + broken + " — см. WARNING выше");
        }
    }
}
