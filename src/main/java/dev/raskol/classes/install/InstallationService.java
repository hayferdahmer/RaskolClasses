// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.install;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.compat.AuthGate;
import dev.raskol.classes.config.ConfigReads;
import dev.raskol.classes.hook.TownyHook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 1.11.4 (P2): ТОНКИЙ реестр инсталляций: гейты, лимиты, TTL, кулдауны, sweep.
 * Вся типовая логика — в InstallationHandler-файлах (WarBannerHandler…PentagramHandler).
 * 1.11.4-fix: кулдаун ставится ТОЛЬКО после успешной постановки (отказ руны не съедает КД).
 *
 * 1.14.10 (Sprint 7, Волна 7.3b, P0-1-01): cfgD/cfgI делегируют в ConfigReads
 * (finiteDouble / finiteInt) — единая NaN-проверка (аудит 1-01).
 *
 * 1.15.7 (Волна 7c, аудит 2.7-9): проверка canBuild через TownyHook перед постановкой.
 * Раньше инсталляции ставились в любых регионах (Towny-клеймы без проверки).
 * Теперь TownyHook.canBuild(player, location) — если Towny недоступен, не ограничиваем;
 * если доступен и игрок не резидент — отказ с сообщением.
 */
public final class InstallationService {

    private final RaskolClasses plugin;
    private final Map<InstallationType, InstallationHandler> handlers = new EnumMap<>(InstallationType.class);
    private final Map<UUID, ActiveInstallation> installations = new ConcurrentHashMap<>();
    private final Map<String, Long> placeCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> cleanupPending = ConcurrentHashMap.newKeySet();
    private final InstallationCleanupCoordinator cleanupCoordinator;

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
        this.cleanupCoordinator = new InstallationCleanupCoordinator(
                installations,
                handlers::get,
                (operation, installation, exception) -> {
                    cleanupPending.add(installation.id());
                    plugin.getLogger().log(
                                java.util.logging.Level.SEVERE,
                                "Installation cleanup failed; entry retained for retry. operation="
                                        + operation
                                        + ", id="
                                        + installation.id()
                                        + ", type="
                                        + installation.type()
                                        + ", owner="
                                        + installation.owner(),
                                exception);
                });
    }

    /** 1.14.10 (Волна 7.3b): делегат в ConfigReads.finiteDouble (аудит 1-01). */
    private double cfgD(String path, double def) {
        return ConfigReads.finiteDouble(plugin.getConfig(), path, def);
    }

    /** 1.14.10 (Волна 7.3b): делегат в ConfigReads.finiteInt (аудит 1-01). */
    private int cfgI(String path, int def) {
        return ConfigReads.finiteInt(plugin.getConfig(), path, def);
    }

    /**
     * 1.15.7 (2.7-9): проверка canBuild через TownyHook.
     * Делегация в статический TownyHook.canBuild (рефлексия к TownyAPI.testBuild);
     * fail closed если Towny сломан, fail open если Towny не установлен.
     */
    private boolean canBuild(Player player, Location loc) {
        return TownyHook.canBuild(plugin, player, loc);
    }

    /* ------------------------------ гейты и постановка ------------------------------ */

    public boolean tryPlace(Player p) {
        if (!AuthGate.canAct(plugin, p)) {
            p.sendMessage(Component.text(
                    plugin.getRaskolConfig()
                            .message(
                                    "gate.blocked.install",
                                    "Инсталляции недоступны в этом режиме или до входа в аккаунт."),
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
            p.sendMessage(Component.text(
                    plugin.getRaskolConfig().message("no-class", "Класс не выбран — посетите герольда"),
                    NamedTextColor.GRAY));
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
            p.sendMessage(Component.text(
                    "Инсталляции откроются на уровне персонажа " + unlock + " (у вас " + charLevel + ")",
                    NamedTextColor.RED));
            return false;
        }
        String cdKey = uuid + ":" + type.name();
        long now = System.currentTimeMillis();
        Long next = placeCooldowns.get(cdKey);
        if (next != null && next > now) {
            p.sendMessage(Component.text(
                    type.displayName() + ": готовность через " + ((next - now) / 1000L + 1) + " с",
                    NamedTextColor.GRAY));
            return false;
        }
        if (countOf(uuid) >= cfgI("installations.max-per-player", 2)) {
            p.sendMessage(Component.text("Достигнут лимит активных инсталляций на игрока (2).", NamedTextColor.RED));
            return false;
        }
        if (countGlobal() >= cfgI("installations.max-global", 200)) {
            p.sendMessage(Component.text("Серверный лимит инсталляций достигнут.", NamedTextColor.RED));
            return false;
        }
        Location loc;
        RayTraceResult hit = p.getWorld()
                .rayTraceBlocks(p.getEyeLocation(), p.getLocation().getDirection(), 6.0, FluidCollisionMode.NEVER);
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

        // 1.15.7 (2.7-9): проверка canBuild через TownyHook
        if (!canBuild(p, loc)) {
            p.sendMessage(Component.text(
                    plugin.getRaskolConfig().message("install.region-denied", "Постановка запрещена в этом регионе."),
                    NamedTextColor.RED));
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
        installations.put(id, new ActiveInstallation(id, type, uuid, loc, placedAt + h.durationSeconds() * 1000L));
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

    /** Выполняет один цикл проверки срока жизни активных инсталляций. */
    public void sweep() {
        long now = System.currentTimeMillis();
        for (ActiveInstallation inst : List.copyOf(installations.values())) {
            if (cleanupPending.contains(inst.id())) {
                cleanupCoordinator.cleanup(candidate -> candidate.id().equals(inst.id()), "sweep-retry");
                if (!installations.containsKey(inst.id())) {
                    cleanupPending.remove(inst.id());
                }
                continue;
            }
            InstallationHandler handler = handlers.get(inst.type());
            if (handler == null) {
                cleanupCoordinator.cleanup(candidate -> candidate.id().equals(inst.id()), "sweep-missing-handler");
                continue;
            }
            if (now > inst.expiresAt()) {
                cleanupCoordinator.cleanup(candidate -> candidate.id().equals(inst.id()), "sweep-expired");
                continue;
            }
            if (!handler.tick(inst)) {
                cleanupCoordinator.cleanup(candidate -> candidate.id().equals(inst.id()), "sweep-triggered");
            }
        }
    }

    public void purgeStale() {
        long now = System.currentTimeMillis();
        cleanupCoordinator.cleanup(installation -> installation.expiresAt() < now, "purge-stale");
        clearCompletedCleanupMarkers();
        placeCooldowns.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public void shutdown() {
        cleanupCoordinator.cleanup(installation -> true, "shutdown");
        clearCompletedCleanupMarkers();
        placeCooldowns.clear();
    }

    public void removeAllOf(UUID uuid) {
        cleanupCoordinator.cleanup(installation -> installation.owner().equals(uuid), "remove-player");
        clearCompletedCleanupMarkers();
        placeCooldowns.keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }

    private void clearCompletedCleanupMarkers() {
        cleanupPending.removeIf(id -> !installations.containsKey(id));
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
