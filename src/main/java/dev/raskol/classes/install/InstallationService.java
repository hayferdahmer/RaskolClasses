// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.compat.AuthGate;
import dev.raskol.classes.fx.FxService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Инсталляции классов (1.5.0 → 1.10.4).
 * 1.10.4: HERESY_CIRCLE переименован в «Пентаграмма»:
 *   - TTL берётся из installations.heresy_circle.duration (25 с), не общий ttl-seconds;
 *   - постановка: звук призыва визора (ENTITY_WARDEN_EMERGE) + отрисовка пентаграммы
 *     (кольцо огней душ + пятилучевая звезда багровых спор);
 *   - тик: видимая пентаграмма + эмбиент ада (ENTITY_BLAZE_AMBIENT) каждую секунду;
 *   - урон/анти-хил/+3 Скверны/с как ранее; в аду урон ×6.
 */
public final class InstallationService {

    public static final String RUNE_INT_MOD = "frost_rune_int";

    private record Installation(UUID id, InstallationType type, UUID owner,
                                Location location, long expiresAt) {
    }

    private static final class FrostRune {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final Location location;
        final long expiresAt;
        UUID ambientId;
        double ownerIntBase = -1;
        final Map<UUID, Integer> staySeconds = new ConcurrentHashMap<>();

        FrostRune(UUID owner, Location location, long expiresAt) {
            this.owner = owner;
            this.location = location;
            this.expiresAt = expiresAt;
        }
    }

    private final RaskolClasses plugin;
    private final Map<UUID, Installation> installations = new ConcurrentHashMap<>();
    private final Map<UUID, FrostRune> runes = new ConcurrentHashMap<>();
    private final Map<String, Long> placeCooldowns = new ConcurrentHashMap<>();
    private BukkitTask sweepTask;

    public InstallationService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private int cfgI(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    /* ------------------------------ removeAllOf ------------------------------ */

    public void removeAllOf(UUID uuid) {
        installations.values().removeIf(i -> i.owner().equals(uuid));
        List<UUID> runeIds = new ArrayList<>();
        runes.forEach((id, r) -> {
            if (r.owner.equals(uuid)) {
                runeIds.add(id);
            }
        });
        for (UUID id : runeIds) {
            FrostRune r = runes.get(id);
            if (r != null) {
                expireRune(r);
            }
        }
        placeCooldowns.keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }

    /* ------------------------------ постановка ------------------------------ */

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

        int cooldown = typeCooldownSeconds(type);
        placeCooldowns.put(cdKey, now + cooldown * 1000L);

        if (type == InstallationType.FROST_RUNE) {
            return placeRune(p, loc, cooldown);
        }
        if (type == InstallationType.HERESY_CIRCLE) {
            return placePentagram(p, loc);
        }
        int ttl = cfgI("installations.ttl-seconds", 60);
        Installation inst = new Installation(UUID.randomUUID(), type, uuid, loc,
                now + ttl * 1000L);
        installations.put(inst.id(), inst);
        FxService fx = plugin.getFx();
        fx.impactBurst(loc, Particle.CLOUD, 10, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.1f);
        if (plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            p.sendMessage(Component.text(type.displayName() + " установлена на "
                    + ttl + " с", NamedTextColor.GREEN));
        }
        return true;
    }

    private int typeCooldownSeconds(InstallationType type) {
        return switch (type) {
            case FROST_RUNE -> cfgI("installations.frost_rune.cooldown", 60);
            case HERESY_CIRCLE -> cfgI("installations.heresy_circle.cooldown", 60);
            default -> cfgI("installations.ttl-seconds", 60);
        };
    }

    public long placeCooldownRemaining(UUID uuid, InstallationType type) {
        Long next = placeCooldowns.get(uuid + ":" + type.name());
        if (next == null) {
            return 0L;
        }
        return Math.max(0L, next - System.currentTimeMillis());
    }

    public long placeCooldownTotalMillis(InstallationType type) {
        return typeCooldownSeconds(type) * 1000L;
    }

    /* ------------------------------ Пентаграмма (1.10.4) ------------------------------ */

    private boolean placePentagram(Player p, Location loc) {
        int duration = cfgI("installations.heresy_circle.duration", 25);
        Installation inst = new Installation(UUID.randomUUID(), InstallationType.HERESY_CIRCLE,
                p.getUniqueId(), loc, System.currentTimeMillis() + duration * 1000L);
        installations.put(inst.id(), inst);

        FxService fx = plugin.getFx();
        // звук призыва визора + отрисовка пентаграммы
        fx.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.8f);
        drawPentagram(loc, cfgD("installations.heresy_circle.radius", 6.0), true);

        if (plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            p.sendMessage(Component.text("Пентаграмма начертана: действует "
                    + duration + " с", NamedTextColor.LIGHT_PURPLE));
        }
        return true;
    }

    /** Кольцо огней душ + пятилучевая звезда багровых спор (видима всем). */
    private void drawPentagram(Location center, double radius, boolean full) {
        World w = center.getWorld();
        if (w == null) {
            return;
        }
        try {
            int ringPts = full ? 48 : 24;
            for (int i = 0; i < ringPts; i++) {
                double angle = (Math.PI * 2 * i) / ringPts;
                Location p = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, p, full ? 2 : 1, 0.0, 0.15, 0.0, 0.005);
            }
            // пятилучевая звезда: вершины k соединяем с k+2
            double[] vx = new double[5];
            double[] vz = new double[5];
            for (int k = 0; k < 5; k++) {
                double a = Math.PI / 2.0 + k * (Math.PI * 2.0 / 5.0);
                vx[k] = Math.cos(a) * radius * 0.85;
                vz[k] = Math.sin(a) * radius * 0.85;
            }
            int samples = full ? 12 : 6;
            for (int k = 0; k < 5; k++) {
                int j = (k + 2) % 5;
                for (int s = 0; s <= samples; s++) {
                    double t = (double) s / samples;
                    double x = vx[k] + (vx[j] - vx[k]) * t;
                    double z = vz[k] + (vz[j] - vz[k]) * t;
                    w.spawnParticle(Particle.CRIMSON_SPORE,
                            center.clone().add(x, 0.25, z), 1, 0.0, 0.1, 0.0, 0.004);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // партикл недоступен на этом билде — молча пропускаем визуал
        }
    }

    /* ------------------------------ руна-зона ------------------------------ */

    private boolean placeRune(Player p, Location loc, int cooldown) {
        UUID uuid = p.getUniqueId();
        for (FrostRune r : runes.values()) {
            if (r.owner.equals(uuid)) {
                p.sendMessage(Component.text("У вас уже есть активная Ледяная руна.", NamedTextColor.RED));
                return false;
            }
        }
        int duration = cfgI("installations.frost_rune.duration", 30);
        FrostRune rune = new FrostRune(uuid, loc, System.currentTimeMillis() + duration * 1000L);
        FxService fx = plugin.getFx();
        rune.ambientId = fx.startAmbient(loc,
                plugin.getConfig().getString("vfx.frost_rune.ambient-sound", "ENTITY_ENDERMAN_TELEPORT"),
                (float) cfgD("vfx.frost_rune.ambient-volume", 0.35),
                (float) cfgD("vfx.frost_rune.ambient-pitch", 0.5),
                plugin.getConfig().getString("vfx.frost_rune.ambient-particle", "REVERSE_PORTAL"),
                duration * 20, 40);
        runes.put(rune.id, rune);

        double radius = cfgD("installations.frost_rune.radius", 8.0);
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!runes.containsKey(rune.id) || loc.getWorld() == null) {
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

        ringParticles(loc, radius, 48);
        fx.impactBurst(loc, Particle.PORTAL, 24, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 0.7f);
        if (plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            p.sendMessage(Component.text("Ледяная руна начертана: действует " + duration
                    + " с, следующая через " + cooldown + " с", NamedTextColor.AQUA));
        }
        return true;
    }

    private void ringParticles(Location center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.PORTAL, p, 2, 0.0, 0.2, 0.0, 0.0);
        }
    }

    private void tickRune(FrostRune rune) {
        long now = System.currentTimeMillis();
        FxService fx = plugin.getFx();
        if (now > rune.expiresAt) {
            expireRune(rune);
            return;
        }
        double radius = cfgD("installations.frost_rune.radius", 8.0);
        double base = cfgD("installations.frost_rune.damage-base", 4.0);
        double ramp = cfgD("installations.frost_rune.damage-ramp", 1.0);
        double cap = cfgD("installations.frost_rune.damage-cap", 12.0);
        int rampEvery = Math.max(1, cfgI("installations.frost_rune.slow-ramp-every", 5));
        int maxTier = cfgI("installations.frost_rune.slow-max-tier", 3);
        double manaPerSec = cfgD("installations.frost_rune.mage-mana-per-sec", 3.0);
        double intMult = cfgD("installations.frost_rune.mage-int-mult", 2.0);

        Player owner = plugin.getServer().getPlayer(rune.owner);
        Set<UUID> inside = new HashSet<>();

        for (Entity e : rune.location.getWorld().getNearbyEntities(
                rune.location, radius, radius, radius)) {
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
            int stay = rune.staySeconds.merge(t.getUniqueId(), 1, Integer::sum);
            inside.add(t.getUniqueId());
            double dmg = Math.min(cap, base + ramp * Math.max(0, stay - 1));
            int tier = Math.min(maxTier, Math.max(0, stay - 1) / rampEvery);
            if (owner != null) {
                plugin.getCombat().dealDamage(t, owner, DamageProfile.magic(dmg));
            }
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, tier));
            t.getWorld().spawnParticle(Particle.SNOWFLAKE, t.getLocation().add(0.0, 0.5, 0.0), 6, 0.3, 0.4, 0.3, 0.02);
        }
        rune.staySeconds.keySet().removeIf(id -> !inside.contains(id));

        if (owner != null) {
            boolean ownerInside = owner.getWorld().equals(rune.location.getWorld())
                    && owner.getLocation().distanceSquared(rune.location) <= radius * radius;
            if (ownerInside) {
                plugin.getResources().add(rune.owner, manaPerSec);
                if (rune.ownerIntBase < 0) {
                    rune.ownerIntBase = plugin.getAttributes().value(rune.owner,
                            dev.raskol.classes.attribute.AttributeType.INT);
                }
                double add = rune.ownerIntBase * (intMult - 1.0);
                plugin.getAttributes().addTimedModifier(rune.owner, RUNE_INT_MOD,
                        0.0, 0.0, add, 2000L);
            }
        }
    }

    private void expireRune(FrostRune rune) {
        runes.remove(rune.id);
        plugin.getFx().stopAmbient(rune.ambientId);
        plugin.getAttributes().removeModifiersBySource(rune.owner, RUNE_INT_MOD);
        plugin.getFx().impactBurst(rune.location, Particle.CLOUD, 16,
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.6f);
        Player owner = plugin.getServer().getPlayer(rune.owner);
        if (owner != null && plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            owner.sendMessage(Component.text("Ледяная руна растаяла.", NamedTextColor.GRAY));
        }
    }

    /* ------------------------------ блочные типы ------------------------------ */

    private void tickInstallation(Installation inst) {
        long now = System.currentTimeMillis();
        if (now > inst.expiresAt()) {
            installations.remove(inst.id());
            if (inst.type() == InstallationType.HERESY_CIRCLE) {
                plugin.getFx().impactBurst(inst.location(), Particle.SMOKE, 14,
                        Sound.ENTITY_BLAZE_DEATH, 0.3f, 0.8f);
            } else {
                plugin.getFx().impactBurst(inst.location(), Particle.CLOUD, 8, null, 0f, 1f);
            }
            return;
        }
        double radius = typeRadius(inst.type());
        Player owner = plugin.getServer().getPlayer(inst.owner());
        switch (inst.type()) {
            case WAR_BANNER -> {
                for (Entity e : nearby(inst.location(), radius)) {
                    if (e instanceof Player t && isAllyOf(inst.owner(), t)) {
                        t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 2 * 20, 0));
                    }
                }
            }
            case LIGHT_WARD -> {
                double heal = cfgD("installations.light_ward.heal", 2.0);
                for (Entity e : nearby(inst.location(), radius)) {
                    if (e instanceof Player t && isAllyOf(inst.owner(), t)) {
                        plugin.getHpBarService().heal(t, heal);
                    }
                }
            }
            case HERESY_CIRCLE -> {
                // 1.10.4: видимая пентаграмма + эмбиент ада каждую секунду
                drawPentagram(inst.location(), radius, false);
                plugin.getFx().playSound(inst.location(), Sound.ENTITY_BLAZE_AMBIENT, 0.18f, 0.9f);

                double tick = cfgD("installations.heresy_circle.damage-magic", 4.0);
                double corr = cfgD("installations.heresy_circle.corruption-per-sec", 3.0);
                for (Entity e : nearby(inst.location(), radius)) {
                    if (e instanceof Player t && isAllyOf(inst.owner(), t)) {
                        continue;
                    }
                    if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                        double dmg = tick;
                        if (owner != null && owner.getWorld().getEnvironment()
                                == World.Environment.NETHER) {
                            dmg *= plugin.getRaskolConfig().warlockNetherMult();
                        }
                        if (owner != null) {
                            plugin.getCombat().dealDamage(t, owner, DamageProfile.magic(dmg));
                            riseSoul(t.getLocation());
                        }
                    }
                }
                if (owner != null
                        && owner.getWorld().equals(inst.location().getWorld())
                        && owner.getLocation().distanceSquared(inst.location()) <= radius * radius) {
                    plugin.getResources().add(owner.getUniqueId(), corr);
                }
            }
            case BEAR_TRAP -> {
                for (Entity e : nearby(inst.location(), 1.2)) {
                    if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                        if (owner != null) {
                            plugin.getCombat().dealDamage(t, owner,
                                    DamageProfile.physical(cfgD("installations.bear_trap.damage-physical", 3.0)));
                        }
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2 * 20, 5));
                        installations.remove(inst.id());
                        plugin.getFx().impactBurst(inst.location(), Particle.CRIT, 12,
                                Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 1.0f);
                        notifyOwner(inst.owner(), "Капкан сработал!");
                        return;
                    }
                }
            }
            case SMOKE_BOMB -> {
                boolean triggered = false;
                for (Entity e : nearby(inst.location(), radius)) {
                    if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                        triggered = true;
                        break;
                    }
                }
                if (triggered) {
                    for (Entity e : nearby(inst.location(), radius)) {
                        if (e instanceof LivingEntity t && isEnemyOf(inst.owner(), t)) {
                            t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 0));
                        }
                    }
                    if (owner != null) {
                        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 0));
                    }
                    installations.remove(inst.id());
                    plugin.getFx().impactBurst(inst.location(), Particle.SMOKE, 40,
                            Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.8f);
                    notifyOwner(inst.owner(), "Дымовая шашка сработала!");
                }
            }
            default -> {
                // FROST_RUNE обрабатывается отдельно
            }
        }
    }

    private void riseSoul(Location loc) {
        if (loc.getWorld() == null) {
            return;
        }
        try {
            loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0.0, 0.4, 0.0),
                    3, 0.25, 0.6, 0.25, 0.05);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private List<Entity> nearby(Location loc, double radius) {
        return new ArrayList<>(loc.getWorld().getNearbyEntities(loc, radius, radius, radius));
    }

    private boolean isAllyOf(UUID ownerId, Player target) {
        if (target.getUniqueId().equals(ownerId)) {
            return true;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null) {
            return false;
        }
        return !plugin.getCombat().canHit(owner, target);
    }

    private boolean isEnemyOf(UUID ownerId, LivingEntity target) {
        if (target instanceof Player tp) {
            Player owner = plugin.getServer().getPlayer(ownerId);
            return owner != null && plugin.getCombat().canHit(owner, tp);
        }
        return true;
    }

    private void notifyOwner(UUID ownerId, String text) {
        if (!plugin.getConfig().getBoolean("installations.notify-owner", true)) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner != null) {
            owner.sendMessage(Component.text(text, NamedTextColor.YELLOW));
        }
    }

    private double typeRadius(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> cfgD("installations.war_banner.radius", 6.0);
            case LIGHT_WARD -> cfgD("installations.light_ward.radius", 4.0);
            case SMOKE_BOMB -> cfgD("installations.smoke_bomb.radius", 3.0);
            case FROST_RUNE -> cfgD("installations.frost_rune.radius", 8.0);
            case HERESY_CIRCLE -> cfgD("installations.heresy_circle.radius", 6.0);
            default -> 1.2;
        };
    }

    /* ------------------------------ задачи/счётчики ------------------------------ */

    public BukkitTask startSweepTask() {
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Installation inst : new ArrayList<>(installations.values())) {
                tickInstallation(inst);
            }
            for (FrostRune rune : new ArrayList<>(runes.values())) {
                tickRune(rune);
            }
        }, 20L, 20L);
        return sweepTask;
    }

    public void purgeStale() {
        long now = System.currentTimeMillis();
        installations.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        runes.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expiresAt < now;
            if (expired) {
                expireRune(e.getValue());
            }
            return expired;
        });
        placeCooldowns.entrySet().removeIf(e -> e.getValue() < now);
    }

    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        for (FrostRune rune : new ArrayList<>(runes.values())) {
            plugin.getFx().stopAmbient(rune.ambientId);
            plugin.getAttributes().removeModifiersBySource(rune.owner, RUNE_INT_MOD);
        }
        runes.clear();
        installations.clear();
    }

    public int countOf(UUID uuid) {
        int c = 0;
        for (Installation i : installations.values()) {
            if (i.owner().equals(uuid)) c++;
        }
        for (FrostRune r : runes.values()) {
            if (r.owner.equals(uuid)) c++;
        }
        return c;
    }

    public int countGlobal() {
        return installations.size() + runes.size();
    }

    public List<Installation> snapshot() {
        return new ArrayList<>(installations.values());
    }

    public int runeCountOf(UUID uuid) {
        int c = 0;
        for (FrostRune r : runes.values()) {
            if (r.owner.equals(uuid)) c++;
        }
        return c;
    }
}
