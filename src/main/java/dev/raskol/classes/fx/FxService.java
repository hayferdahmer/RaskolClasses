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
 * VFX/SFX-слой (1.5.0 → 1.9.0-fix4).
 * 1.9.0-fix4: трейл заряженного снаряда переписан на Runnable + holder-массив
 * (фикс «void cannot be converted to BukkitTask»); снаряды — Snowball/Egg,
 * взрывов и разрушения блоков нет; попадание = вспышка + звук из vfx-конфига.
 */
public final class FxService implements Listener {

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
    private final Map<String, Long> procVisualCd = new ConcurrentHashMap<>();
    private final Map<UUID, ChargedShot> chargedShots = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> trailTasks = new ConcurrentHashMap<>();
    private final Map<UUID, AuraTask> auras = new ConcurrentHashMap<>();
    private final Map<UUID, AmbientTask> ambients = new ConcurrentHashMap<>();
    private final AtomicInteger staleCount = new AtomicInteger();

    public FxService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* -------------------------------- резолв -------------------------------- */

    public Sound resolveSound(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            if (warnedSounds.add(name)) {
                plugin.getLogger().warning("vfx: неизвестный звук '" + name + "'");
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

    /**
     * Зарядить снаряд уроном/поджогом. Снаряд — ЛЮБОЙ небомбовый (Snowball/Egg):
     * FxService рисует огненный трейл и вспышку попадания; взрыва нет.
     * 1.9.0-fix4: трейл-таск создаётся через Runnable + holder-массив (самоотмена).
     */
    public void chargeProjectile(UUID projectileId, UUID caster, String abilityId,
                                 double phys, double magic, int fireTicks) {
        chargedShots.put(projectileId, new ChargedShot(caster, abilityId, phys, magic, fireTicks,
                System.currentTimeMillis() + 6000L));
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity e = plugin.getServer().getEntity(projectileId);
            if (e == null || e.isDead() || !chargedShots.containsKey(projectileId)) {
                trailTasks.remove(projectileId);
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                return;
            }
            e.getWorld().spawnParticle(Particle.FLAME, e.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
            e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation(), 1, 0.02, 0.02, 0.02, 0.005);
        }, 0L, 2L);
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
            trail.cancel();
        }
        Location hitLoc = projectile.getLocation();
        String base = "vfx." + shot.abilityId() + ".";

        Particle impact = resolveParticle(plugin.getConfig().getString(base + "impact-particle", "FLAME"));
        if (impact != null && hitLoc.getWorld() != null) {
            hitLoc.getWorld().spawnParticle(impact, hitLoc, 24, 0.4, 0.3, 0.4, 0.05);
            hitLoc.getWorld().spawnParticle(Particle.SMOKE, hitLoc, 10, 0.3, 0.2, 0.3, 0.03);
        }
        Sound impactSound = resolveSound(plugin.getConfig().getString(base + "impact-sound",
                "ENTITY_BLAZE_SHOOT"));
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
        plugin.getCombat().dealDamage(target, caster,
                DamageProfile.hybrid(shot.phys(), shot.magic()));
        if (shot.fireTicks() > 0 && plugin.getCombat().canHit(caster, target)) {
            target.setFireTicks(shot.fireTicks());
        }
    }

    /* --------------------------------- ауры --------------------------------- */

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

    public void strikeLightningVisual(Location loc) {
        if (loc.getWorld() != null) {
            loc.getWorld().strikeLightningEffect(loc);
        }
    }

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
                + ", снарядов " + chargedShots.size()
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
        trailTasks.entrySet().removeIf(e -> {
            if (!chargedShots.containsKey(e.getKey())) {
                e.getValue().cancel();
                return true;
            }
            return false;
        });
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
