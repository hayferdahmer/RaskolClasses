// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.compat.AuthGate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P2): ТОНКИЙ реестр инсталляций: гейты, лимиты, TTL, кулдауны, sweep.
 * Вся типовая логика — в InstallationHandler-файлах (WarBannerHandler…PentagramHandler).
 * 1.11.4-fix: кулдаун ставится ТОЛЬКО после успешной постановки (отказ руны не съедает КД).
 */
public final class InstallationService {

    private final RaskolClasses plugin;
    private final Map<InstallationType, InstallationHandler> handlers =
            new EnumMap<>(InstallationType.class);
    private final Map<UUID, ActiveInstallation> installations = new ConcurrentHashMap<>();
    private final Map<String, Long> placeCooldowns = new ConcurrentHashMap<>();
    private BukkitTask sweepTask;

    public InstallationService(RaskolClasses plugin) {
        this.plugin = plugin;
        for (InstallationHandler h : List.of(
                new WarBannerHandler(plugin),
                new BearTrapHandler(plugin),
                new LightWardHandler(plugin),
                new FrostRuneHandler(plugin),
                new SmokeBombHandler(plugin),
                new PentagramHandler(plugin))) {
            handlers.put(h.type(), h);
        }
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private int cfgI(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    /* ------------------------------ гейты и постановка ------------------------------ */

    public boolean tryPlace(Player p) {
        if (!AuthGate.canAct(plugin, p)) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "gate.blocked.install", "Инсталляции недоступны в этом режиме или до входа в аккаунт."),
                    NamedTextColor.RED));
            return false;
        }
        if (p.getGameMode() == GameMode.CREATIVE
                && plugin.getConfig().getBoolean("compat.block-casts-in-creative", true)) {
            p.sendMessage(Component.text("В творческом режиме инсталляции запрещены.", NamedTextColor.RED));
            return false;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(p);
        if (pc == null) {
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-class", "Класс не выбран — посетите герольда"), NamedTextColor.GRAY));
            return false;
        }
        InstallationType type = InstallationType.forClass(pc);
        if (type == null) {
            return false;
        }
        UUID uuid = p.getUniqueId();

        int unlock = cfgI("installations.unlock-level", 50);
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        if (charLevel < unlock) {
            p.sendMessage(Component.text("Инсталляции откроются на уровне персонажа "
                    + unlock + " (у вас " + charLevel + ")", NamedTextColor.RED));
            return false;
        }
        String cdKey = uuid + ":" + type.name();
        long now = System.currentTimeMillis();
        Long next = placeCooldowns.get(cdKey);
        if (next != null && next > now) {
            p.sendMessage(Component.text(type.displayName() + ": готовность через "
                    + ((next - now) / 1000L + 1) + " с", NamedTextColor.GRAY));
            return false;
        }
        if (countOf(uuid) >= cfgI("installations.max-per-player", 2)) {
            p.sendMessage(Component.text("Достигнут лимит активных инсталляций на игрока (2).",
                    NamedTextColor.RED));
            return false;
        }
        if (countGlobal() >= cfgI("installations.max-global", 200)) {
            p.sendMessage(Component.text("Серверный лимит инсталляций достигнут.", NamedTextColor.RED));
            return false;
        }
        Location loc;
        RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(),
                p.getLocation().getDirection(), 6.0, FluidCollisionMode.NEVER);
        if (hit != null) {
            loc = hit.getHitPosition().toLocation(p.getWorld());
        } else {
            loc = p.getLocation();
        }
        loc = loc.getBlock().getLocation().add(0.5, 0.1, 0.5);
        if (!p.getWorld().getWorldBorder().isInside(loc)) {
            p.sendMessage(Component.text("За границей мира ставить нельзя.", NamedTextColor.RED));
            return false;
        }
        double spawnDeny = cfgD("installations.deny-radius-spawn", 100.0);
        if (spawnDeny > 0 && loc.distanceSquared(p.getWorld().getSpawnLocation()) < spawnDeny * spawnDeny) {
            p.sendMessage(Component.text("Слишком близко к спавну: постановка запрещена.", NamedTextColor.RED));
            return false;
        }

        InstallationHandler h = handlers.get(type);
        if (h == null) {
            return false;
        }
        UUID id = UUID.randomUUID();
        if (!h.place(p, loc, id)) {
            return false;
        }
        long placedAt = System.currentTimeMillis();
        installations.put(id, new ActiveInstallation(id, type, uuid, loc,
                placedAt + h.durationSeconds() * 1000L));
        placeCooldowns.put(cdKey, placedAt + h.cooldownSeconds() * 1000L);
        return true;
    }

    public long placeCooldownRemaining(UUID uuid, InstallationType type) {
        Long next = placeCooldowns.get(uuid + ":" + type.name());
        if (next == null) {
            return 0L;
        }
        return Math.max(0L, next - System.currentTimeMillis());
    }

    public long placeCooldownTotalMillis(InstallationType type) {
        InstallationHandler h = handlers.get(type);
        return (h != null ? h.cooldownSeconds() : 60) * 1000L;
    }

    /* ------------------------------ sweep и жизненный цикл ------------------------------ */

    public BukkitTask startSweepTask() {
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (ActiveInstallation inst : List.copyOf(installations.values())) {
                InstallationHandler h = handlers.get(inst.type());
                if (h == null) {
                    installations.remove(inst.id());
                    continue;
                }
                if (now > inst.expiresAt()) {
                    installations.remove(inst.id());
                    h.expire(inst);
                    continue;
                }
                if (!h.tick(inst)) {
                    installations.remove(inst.id());
                    h.expire(inst);
                }
            }
        }, 20L, 20L);
        return sweepTask;
    }

    public void purgeStale() {
        long now = System.currentTimeMillis();
        installations.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expiresAt() < now;
            if (expired) {
                InstallationHandler h = handlers.get(e.getValue().type());
                if (h != null) {
                    h.expire(e.getValue());
                }
            }
            return expired;
        });
        placeCooldowns.entrySet().removeIf(e -> e.getValue() < now);
    }

    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        for (ActiveInstallation inst : List.copyOf(installations.values())) {
            InstallationHandler h = handlers.get(inst.type());
            if (h != null) {
                h.expire(inst);
            }
        }
        installations.clear();
        placeCooldowns.clear();
    }

    public void removeAllOf(UUID uuid) {
        for (ActiveInstallation inst : List.copyOf(installations.values())) {
            if (inst.owner().equals(uuid)) {
                installations.remove(inst.id());
                InstallationHandler h = handlers.get(inst.type());
                if (h != null) {
                    h.expire(inst);
                }
            }
        }
        placeCooldowns.keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }

    /* ------------------------------ счётчики ------------------------------ */

    public int countOf(UUID uuid) {
        int c = 0;
        for (ActiveInstallation i : installations.values()) {
            if (i.owner().equals(uuid)) c++;
        }
        return c;
    }

    public int countGlobal() {
        return installations.size();
    }

    public int runeCountOf(UUID uuid) {
        int c = 0;
        for (ActiveInstallation i : installations.values()) {
            if (i.owner().equals(uuid) && i.type() == InstallationType.FROST_RUNE) c++;
        }
        return c;
    }

    public List<ActiveInstallation> snapshot() {
        return new ArrayList<>(installations.values());
    }
}
