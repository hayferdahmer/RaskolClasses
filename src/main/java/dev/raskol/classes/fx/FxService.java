// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.DamageProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VFX/SFX-слой плагина (1.5.0 → 1.9.0).
 *
 * 1.9.0-fix: звуки резолвятся через Sound.valueOf(ENUM_NAME) — раньше искались
 * через NamespacedKey("entity_player_levelup"), чего в реестре НЕТ, поэтому
 * способности звучали заглушкой или молчали.
 *
 * Новые API визуала:
 *  - chargeProjectile(...) + ProjectileHitEvent: заряженный снаряд (огненный шар)
 *    наносит гибрид-урон и поджигает при попадании, вспышка/звук — всегда;
 *  - impactBurst(...): партиклы + звук в точке;
 *  - startAura(...): аура партиклов на игроке с таймером и звуком снятия;
 *  - strikeLightningVisual(...): визуальная молния без урона/пожара;
 *  - startAmbient(...): эмбиент-луп звука+партиклов в точке (для руны).
 * Каст-звуки НЕ входят в звуковой бюджет: каст обязан звучать всегда.
 */
public final class FxService implements Listener {

    /** Заряженный снаряд: урон и поджог применяются при попадании. */
    private record ChargedShot(UUID caster, String abilityId,
                               double phys, double magic, int fireTicks, long expiresAt) {
    }

    private record AuraTask(BukkitTask task, long expiresAt) {
    }

    private record AmbientTask(BukkitTask task, long expiresAt) {
    }

    private final RaskolClasses plugin;
    private final Set<String> warnedSounds = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedParticles = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> procVisualCd = new ConcurrentHashMap<>();
    private final Map<UUID, ChargedShot> chargedShots = new ConcurrentHashMap<>();
    private final Map<UUID, AuraTask> auras = new ConcurrentHashMap<>();
    private final Map<UUID, AmbientTask> ambients = new ConcurrentHashMap<>();
    private final AtomicInteger staleCount = new AtomicInteger();

    public FxService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* -------------------------------- резолв -------------------------------- */

    /** Имя звука (ENUM Bukkit) → Sound; битое имя → WARNING один раз + null. */
    public Sound resolveSound(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            if (warnedSounds.add(name)) {
                plugin.getLogger().warning("vfx: неизвестный звук '" + name
                        + "' — нужна константа Bukkit Sound (например ENTITY_GENERIC_EXPLODE)");
                staleCount.incrementAndGet();
            }
            return null;
        }
    }

    /** Имя партикла (ENUM Bukkit) → Particle; битое → WARNING один раз + null. */
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

    /** Визуал каста из vfx-конфига (звук + партикл у кастера). Вызывается конвейером один раз. */
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

    /** Proc-тег сабтайтлом + proc-визуал из vfx.proc.* (анти-спам: 1 тег/2 с на proc). */
    public void procByKey(Player player, String fallbackTag, String procId) {
        long now = System.currentTimeMillis();
        Long prev = procVisualCd.get(procId);
        if (prev != null && now - prev < 2000L) {
            return;
        }
        procVisualCd.put(procId, now);
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

    /** Зарядить снаряд уроном/поджогом; применится в ProjectileHitEvent. */
    public void chargeProjectile(UUID projectileId, UUID caster, String abilityId,
                                 double phys, double magic, int fireTicks) {
        chargedShots.put(projectileId, new ChargedShot(caster, abilityId, phys, magic, fireTicks,
                System.currentTimeMillis() + 6000L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ChargedShot shot = chargedShots.remove(projectile.getUniqueId());
        if (shot == null) {
            return;
        }
        Location hitLoc = projectile.getLocation();
        String base = "vfx." + shot.abilityId() + ".";

        // Вспышка и звук попадания — всегда (и по блоку, и по сущности)
        Particle impact = resolveParticle(plugin.getConfig().getString(base + "impact-particle", "FLAME"));
        if (impact != null) {
            hitLoc.getWorld().spawnParticle(impact, hitLoc, 24, 0.4, 0.3, 0.4, 0.05);
            hitLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, hitLoc, 10, 0.3, 0.2, 0.3, 0.02);
        }
        Sound impactSound = resolveSound(plugin.getConfig().getString(base + "impact-sound",
                "ENTITY_GENERIC_EXPLODE"));
        if (impactSound != null) {
            playSound(hitLoc, impactSound, 0.4f, 0.9f);
        }

        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof LivingEntity target)) {
            return; // попадание в блок — только визуал
        }
        Player caster = plugin.getServer().getPlayer(shot.caster());
        if (caster == null) {
            return;
        }
        // Фракционный гейт и резисты — внутри dealDamage; по союзнику урон 0, вспышка остаётся
        plugin.getCombat().dealDamage(target, caster,
                DamageProfile.hybrid(shot.phys(), shot.magic()));
        if (shot.fireTicks() > 0 && plugin.getCombat().canHit(caster, target)) {
            target.setFireTicks(shot.fireTicks());
        }
    }

    /* --------------------------------- ауры --------------------------------- */

    /**
     * Аура партиклов на игроке: тик каждые 10 тиков, частицы кольцом вокруг тела.
     * По окончании — звук снятия (expireSoundKey, может быть null).
     */
    public void startAura(UUID playerUuid, Particle particle, int ticks, int perTick,
                          String expireSoundKey) {
        stopAura(playerUuid);
        long expiresAt = System.currentTimeMillis() + ticks * 50L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
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
                Location p = center.clone().add(
                        Math.cos(angle) * radius, rnd.nextDouble() * 1.6, Math.sin(angle) * radius);
                player.getWorld().spawnParticle(particle, p, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }, 0L, 10L);
        auras.put(playerUuid, new AuraTask(task, expiresAt));
        // звук снятия по истечении
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            AuraTask at = auras.remove(playerUuid);
            if (at != null) {
                at.task().cancel();
            }
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && expireSoundKey != null) {
                Sound s = resolveSound(expireSoundKey);
                if (s != null) {
                    playSound(player, s, 0.5f, 1.2f);
                }
            }
        }, ticks + 1L);
    }

    public void stopAura(UUID playerUuid) {
        AuraTask at = auras.remove(playerUuid);
        if (at != null) {
            at.task().cancel();
        }
    }

    /* ------------------------------ молния/вспышки ------------------------------ */

    /** Визуальная молния (гром + вспышка) без урона и пожара. */
    public void strikeLightningVisual(Location loc) {
        if (loc.getWorld() != null) {
            loc.getWorld().strikeLightningEffect(loc);
        }
    }

    /** Вспышка партиклов + звук в точке. */
    public void impactBurst(Location loc, Particle main, int count,
                            Sound sound, float volume, float pitch) {
        if (loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawnParticle(main, loc, count, 0.4, 0.4, 0.4, 0.05);
        if (sound != null) {
            playSound(loc, sound, volume, pitch);
        }
    }

    /* ------------------------------ эмбиент-лупы ------------------------------ */

    /**
     * Эмбиент-луп в точке (руны/зоны): звук + партиклы каждые periodTicks до expiry.
     * Возвращает id задачи для ручной остановки (или null).
     */
    public UUID startAmbient(Location loc, String soundKey, float volume, float pitch,
                             String particleKey, int ticks, int periodTicks) {
        UUID id = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + ticks * 50L;
        Sound sound = resolveSound(soundKey);
        Particle particle = resolveParticle(particleKey);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() > expiresAt || loc.getWorld() == null) {
                AmbientTask at = ambients.remove(id);
                if (at != null) {
                    at.task().cancel();
                }
                return;
            }
            if (sound != null) {
                playSound(loc, sound, volume, pitch);
            }
            if (particle != null) {
                loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 0.3, 0.0), 6, 0.6, 0.2, 0.6, 0.02);
            }
        }, 0L, Math.max(1, periodTicks));
        ambients.put(id, new AmbientTask(task, expiresAt));
        return id;
    }

    public void stopAmbient(UUID id) {
        if (id == null) {
            return;
        }
        AmbientTask at = ambients.remove(id);
        if (at != null) {
            at.task().cancel();
        }
    }

    /* ------------------------------ диагностика ------------------------------ */

    public void appendDebug(CommandSender sender, List<String> abilityIds) {
        sender.sendMessage(Component.text("FxService: битых звуков " + warnedSounds.size()
                + ", битых партиклов " + warnedParticles.size()
                + ", заряженных снарядов " + chargedShots.size()
                + ", аур " + auras.size() + ", эмбиентов " + ambients.size(),
                NamedTextColor.GRAY));
        for (String id : abilityIds) {
            String soundKey = plugin.getConfig().getString("vfx." + id + ".cast-sound", "");
            String particleKey = plugin.getConfig().getString("vfx." + id + ".cast-particle", "");
            boolean okSound = soundKey.isEmpty() || resolveSound(soundKey) != null;
            boolean okParticle = particleKey.isEmpty() || resolveParticle(particleKey) != null;
            if (!okSound || !okParticle) {
                sender.sendMessage(Component.text("  • " + id + ": "
                        + (okSound ? "" : "звук '" + soundKey + "' битый ")
                        + (okParticle ? "" : "партикл '" + particleKey + "' битый"),
                        NamedTextColor.RED));
            }
        }
    }

    public void purgeStale() {
        long now = System.currentTimeMillis();
        chargedShots.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        auras.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expiresAt() < now;
            if (expired) {
                e.getValue().task().cancel();
            }
            return expired;
        });
        ambients.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expiresAt() < now;
            if (expired) {
                e.getValue().task().cancel();
            }
            return expired;
        });
        procVisualCd.entrySet().removeIf(e -> now - e.getValue() > 30_000L);
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

    /** Стартовая валидация vfx-каталога: битые ключи = WARNING, не краш. */
    public void validateConfig() {
        var section = plugin.getConfig().getConfigurationSection("vfx");
        if (section == null) {
            return;
        }
        int broken = 0;
        for (String key : section.getKeys(true)) {
            if (key.endsWith("cast-sound") || key.endsWith("impact-sound") || key.endsWith("tag-sound")) {
                String name = section.getString(key, "");
                if (!name.isEmpty() && resolveSound(name) == null) {
                    broken++;
                }
            }
            if (key.endsWith("cast-particle") || key.endsWith("impact-particle")) {
                String name = section.getString(key, "");
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
