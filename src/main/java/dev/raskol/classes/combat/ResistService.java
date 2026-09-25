// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.6.0: сопротивления урону (РЕЗИСТ).
 * 1.6.3: кэш факторов на тик. 1.6.9: pvp-cap / disabled-worlds.
 * 1.6.11: NaN-защита. 1.6.12: метрики размеров карт для /rc health.
 * 1.10.0-fix: defaultPhysical/defaultMagic покрывают WARLOCK (10 / 26).
 * 1.11.1: stripOneTimedModifier(uuid) — снятие одного timed-модификатора
 *         (для «Небытия» Чёрного Мага).
 */
public final class ResistService {

    public record Modifier(String source, double physicalPct, double magicPct, long expiresAt) {
        public boolean isPermanent() { return expiresAt == Long.MAX_VALUE; }
    }

    public record Breakdown(double basePhysical, double baseMagic,
                            List<Modifier> active,
                            double physicalTotal, double magicTotal) {
    }

    private static final class FactorCache {
        long tick = -1;
        double phys = 1.0;
        double magic = 1.0;
    }

    private final RaskolClasses plugin;
    private final Map<UUID, CopyOnWriteArrayList<Modifier>> modifiers = new ConcurrentHashMap<>();
    private final Map<UUID, FactorCache> factorCache = new ConcurrentHashMap<>();

    public ResistService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public double cap() {
        double v = plugin.getConfig().getDouble("resist.cap", 90.0);
        return Double.isFinite(v) && v >= 0.0 && v <= 100.0 ? v : 90.0;
    }

    public double pvpCap() {
        double v = plugin.getConfig().getDouble("resist.pvp-cap", cap());
        return Double.isFinite(v) && v >= 0.0 && v <= 100.0 ? v : cap();
    }

    public boolean disabledIn(World world) {
        if (world == null) {
            return false;
        }
        return plugin.getConfig().getStringList("resist.disabled-worlds")
                .contains(world.getName());
    }

    public double basePhysical(PlayerClass pc) {
        double v = plugin.getConfig().getDouble(
                "resist.classes." + pc.name() + ".physical", defaultPhysical(pc));
        return Double.isFinite(v) ? v : defaultPhysical(pc);
    }

    public double baseMagic(PlayerClass pc) {
        double v = plugin.getConfig().getDouble(
                "resist.classes." + pc.name() + ".magic", defaultMagic(pc));
        return Double.isFinite(v) ? v : defaultMagic(pc);
    }

    private static double defaultPhysical(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 27.0;
            case ROGUE -> 14.0;
            case MAGE -> 12.0;
            case PRIEST -> 16.0;
            case HUNTER -> 16.0;
            case WARLOCK -> 10.0;
        };
    }

    private static double defaultMagic(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 12.0;
            case ROGUE -> 12.0;
            case MAGE -> 26.0;
            case PRIEST -> 30.0;
            case HUNTER -> 12.0;
            case WARLOCK -> 26.0;
        };
    }

    public void addTimedModifier(UUID uuid, String source,
                                 double physicalPct, double magicPct, long millis) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source,
                        Double.isFinite(physicalPct) ? physicalPct : 0.0,
                        Double.isFinite(magicPct) ? magicPct : 0.0,
                        System.currentTimeMillis() + millis));
        invalidate(uuid);
    }

    public void addPermanentModifier(UUID uuid, String source,
                                     double physicalPct, double magicPct) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source,
                        Double.isFinite(physicalPct) ? physicalPct : 0.0,
                        Double.isFinite(magicPct) ? magicPct : 0.0,
                        Long.MAX_VALUE));
        invalidate(uuid);
    }

    public boolean hasModifier(UUID uuid, String source) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Modifier m : list) {
            if (m.source().equals(source)
                    && (m.isPermanent() || m.expiresAt() > now)) {
                return true;
            }
        }
        return false;
    }

    public void removeModifiersBySource(UUID uuid, String source) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return;
        }
        list.removeIf(m -> m.source().equals(source));
        if (list.isEmpty()) {
            modifiers.remove(uuid);
        }
        invalidate(uuid);
    }

    /**
     * 1.11.1: снять ОДИН ближайший к истечению timed-модификатор (не permanent).
     * Возвращает source снятого модификатора или null, если timed-модификаторов нет.
     * Используется «Небытием» Чёрного Мага.
     */
    public String stripOneTimedModifier(UUID uuid) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null || list.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Modifier victim = null;
        for (Modifier m : list) {
            if (!m.isPermanent() && m.expiresAt() > now) {
                if (victim == null || m.expiresAt() < victim.expiresAt()) {
                    victim = m;
                }
            }
        }
        if (victim == null) {
            return null;
        }
        list.remove(victim);
        if (list.isEmpty()) {
            modifiers.remove(uuid);
        }
        invalidate(uuid);
        return victim.source();
    }

    public int trackedPlayers() {
        return modifiers.size();
    }

    public int totalModifiers() {
        int total = 0;
        for (CopyOnWriteArrayList<Modifier> list : modifiers.values()) {
            total += list.size();
        }
        return total;
    }

    public int factorCacheSize() {
        return factorCache.size();
    }

    public Breakdown breakdown(UUID uuid) {
        return breakdown(uuid, cap());
    }

    public Breakdown breakdown(UUID uuid, double cap) {
        Player player = plugin.getServer().getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        double basePhys = pc != null ? basePhysical(pc) : 0.0;
        double baseMagic = pc != null ? baseMagic(pc) : 0.0;
        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        List<Modifier> active = list == null ? List.of() : list.stream()
                .filter(m -> m.isPermanent() || m.expiresAt() > now)
                .toList();
        double phys = basePhys;
        double magic = baseMagic;
        for (Modifier m : active) {
            phys += m.physicalPct();
            magic += m.magicPct();
        }
        return new Breakdown(basePhys, baseMagic, active,
                clamp(phys, cap), clamp(magic, cap));
    }

    public double physicalResist(UUID uuid) { return breakdown(uuid).physicalTotal(); }
    public double magicResist(UUID uuid) { return breakdown(uuid).magicTotal(); }
    public double physicalResist(UUID uuid, double cap) { return breakdown(uuid, cap).physicalTotal(); }
    public double magicResist(UUID uuid, double cap) { return breakdown(uuid, cap).magicTotal(); }

    public double physicalFactor(UUID uuid) { return factors(uuid).phys; }
    public double magicFactor(UUID uuid) { return factors(uuid).magic; }

    public double physicalFactor(UUID uuid, double cap) {
        double factor = 1.0 - physicalResist(uuid, cap) / 100.0;
        if (!Double.isFinite(factor)) return 1.0;
        return Math.max(0.0, Math.min(1.0, factor));
    }

    public double magicFactor(UUID uuid, double cap) {
        double factor = 1.0 - magicResist(uuid, cap) / 100.0;
        if (!Double.isFinite(factor)) return 1.0;
        return Math.max(0.0, Math.min(1.0, factor));
    }

    private FactorCache factors(UUID uuid) {
        FactorCache cache = factorCache.computeIfAbsent(uuid, k -> new FactorCache());
        long tick = Bukkit.getCurrentTick();
        if (cache.tick != tick) {
            Breakdown b = breakdown(uuid);
            cache.phys = 1.0 - b.physicalTotal() / 100.0;
            cache.magic = 1.0 - b.magicTotal() / 100.0;
            cache.tick = tick;
        }
        return cache;
    }

    public void invalidate(UUID uuid) {
        FactorCache cache = factorCache.get(uuid);
        if (cache != null) {
            cache.tick = -1;
        }
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        modifiers.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
        factorCache.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null
                && !modifiers.containsKey(uuid));
    }

    public void clear(UUID uuid) {
        modifiers.remove(uuid);
        factorCache.remove(uuid);
    }

    private double clamp(double value, double cap) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        double safeCap = Double.isFinite(cap) ? cap : 90.0;
        return Math.max(0.0, Math.min(safeCap, value));
    }
}
