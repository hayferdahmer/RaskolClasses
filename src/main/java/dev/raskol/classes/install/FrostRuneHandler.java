// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.combat.DamageProfile;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P2): Ледяная руна — зона с нарастающим уроном/замедлением,
 * бафф мага внутри (+мана/с, ИНТ×2 таймерным модификатором).
 * Одна руна на владельца; эмбиент и кольцо-партиклы — собственные задачи.
 */
public final class FrostRuneHandler extends BaseInstallationHandler {

    public static final String RUNE_INT_MOD = "frost_rune_int";

    private static final class RuneState {
        final UUID owner;
        final Location loc;
        UUID ambientId;
        BukkitTask ringTask;
        final Map<UUID, Integer> stay = new ConcurrentHashMap<>();
        double ownerIntBase = -1;

        RuneState(UUID owner, Location loc) {
            this.owner = owner;
            this.loc = loc;
        }
    }

    private final Map<UUID, RuneState> states = new ConcurrentHashMap<>();

    public FrostRuneHandler(RaskolClasses plugin) {
        super(plugin);
    }

    @Override
    public InstallationType type() {
        return InstallationType.FROST_RUNE;
    }

    @Override
    public double radius() {
        return cfgD("installations.frost_rune.radius", 8.0);
    }

    @Override
    public int cooldownSeconds() {
        return cfgI("installations.frost_rune.cooldown", 60);
    }

    @Override
    public int durationSeconds() {
        return cfgI("installations.frost_rune.duration", 30);
    }

    @Override
    public boolean place(Player p, Location loc, UUID instId) {
        for (RuneState s : states.values()) {
            if (s.owner.equals(p.getUniqueId())) {
                p.sendMessage(net.kyori.adventure.text.Component.text(
                        "У вас уже есть активная Ледяная руна.",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
                return false;
            }
        }
        RuneState st = new RuneState(p.getUniqueId(), loc);
        int duration = durationSeconds();
        st.ambientId = plugin.getFx().startAmbient(loc,
                plugin.getConfig().getString("vfx.frost_rune.ambient-sound", "ENTITY_ENDERMAN_TELEPORT"),
                (float) cfgD("vfx.frost_rune.ambient-volume", 0.35),
                (float) cfgD("vfx.frost_rune.ambient-pitch", 0.5),
                plugin.getConfig().getString("vfx.frost_rune.ambient-particle", "REVERSE_PORTAL"),
                duration * 20, 40);
        double radius = radius();
        st.ringTask = plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!states.containsKey(instId) || loc.getWorld() == null) {
                task.cancel();
                return;
            }
            for (int i = 0; i < 32; i++) {
                double angle = (Math.PI * 2 * i) / 32;
                Location ringLoc = loc.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                for (int y = 0; y < 4; y++) {
                    loc.getWorld().spawnParticle(Particle.PORTAL,
                            ringLoc.clone().add(0.0, y * 0.5, 0.0), 2, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }, 0L, 20L);
        states.put(instId, st);

        ringParticles(loc, radius, 48);
        plugin.getFx().impactBurst(loc, Particle.PORTAL, 24, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 0.7f);
        notifyOwner(p.getUniqueId(), "Ледяная руна начертана: действует " + duration
                + " с, следующая через " + cooldownSeconds() + " с");
        return true;
    }

    @Override
    public boolean tick(ActiveInstallation inst) {
        RuneState rune = states.get(inst.id());
        if (rune == null) {
            return false;
        }
        double radius = radius();
        double base = cfgD("installations.frost_rune.damage-base", 4.0);
        double ramp = cfgD("installations.frost_rune.damage-ramp", 1.0);
        double cap = cfgD("installations.frost_rune.damage-cap", 12.0);
        int rampEvery = Math.max(1, cfgI("installations.frost_rune.slow-ramp-every", 5));
        int maxTier = cfgI("installations.frost_rune.slow-max-tier", 3);
        double manaPerSec = cfgD("installations.frost_rune.mage-mana-per-sec", 3.0);
        double intMult = cfgD("installations.frost_rune.mage-int-mult", 2.0);

        Player owner = plugin.getServer().getPlayer(rune.owner);
        Set<UUID> inside = new HashSet<>();

        for (Entity e : rune.loc.getWorld().getNearbyEntities(rune.loc, radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || t.getUniqueId().equals(rune.owner)) {
                continue;
            }
            boolean enemy;
            if (owner != null) {
                enemy = plugin.getCombat().canHit(owner, t);
            } else {
                enemy = !(t instanceof Player);
            }
            if (!enemy) {
                continue;
            }
            int stay = rune.stay.merge(t.getUniqueId(), 1, Integer::sum);
            inside.add(t.getUniqueId());
            double dmg = Math.min(cap, base + ramp * Math.max(0, stay - 1));
            int tier = Math.min(maxTier, Math.max(0, stay - 1) / rampEvery);
            if (owner != null) {
                plugin.getCombat().dealDamage(t, owner, DamageProfile.magic(dmg));
            }
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, tier));
            t.getWorld().spawnParticle(Particle.SNOWFLAKE, t.getLocation().add(0.0, 0.5, 0.0), 6, 0.3, 0.4, 0.3, 0.02);
        }
        rune.stay.keySet().removeIf(id -> !inside.contains(id));

        if (owner != null) {
            boolean ownerInside = owner.getWorld().equals(rune.loc.getWorld())
                    && owner.getLocation().distanceSquared(rune.loc) <= radius * radius;
            if (ownerInside) {
                plugin.getResources().add(rune.owner, manaPerSec);
                if (rune.ownerIntBase < 0) {
                    rune.ownerIntBase = plugin.getAttributes().value(rune.owner, AttributeType.INT);
                }
                double add = rune.ownerIntBase * (intMult - 1.0);
                plugin.getAttributes().addTimedModifier(rune.owner, RUNE_INT_MOD,
                        0.0, 0.0, add, 2000L);
            }
        }
        return true;
    }

    @Override
    public void expire(ActiveInstallation inst) {
        RuneState rune = states.remove(inst.id());
        if (rune == null) {
            return;
        }
        plugin.getFx().stopAmbient(rune.ambientId);
        if (rune.ringTask != null) {
            rune.ringTask.cancel();
        }
        plugin.getAttributes().removeModifiersBySource(rune.owner, RUNE_INT_MOD);
        plugin.getFx().impactBurst(rune.loc, Particle.CLOUD, 16,
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.6f);
        notifyOwner(rune.owner, "Ледяная руна растаяла.");
    }

    private void ringParticles(Location center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.PORTAL, p, 2, 0.0, 0.2, 0.0, 0.0);
        }
    }
}
