// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.6.0: сопротивления урону (РЕЗИСТ).
 * Эффективный резист = база класса + временные/постоянные модификаторы,
 * суммарно не выше кап resist.cap. Чистый урон игнорирует резисты.
 *
 * 1.6.3 (производительность):
 *  - кэш резист-факторов на игрока на тик (factorCache): повторные вызовы
 *    physicalFactor/magicFactor в одном тике не пересчитывают breakdown;
 *    кэш инвалидируется любым изменением модификаторов (add/remove/clear/purge),
 *    поэтому каст бафа и удар в том же тике считаются корректно;
 *  - фильтрация истёкших модификаторов БЕЗ мутации списка (skip при чтении):
 *    removeIf по CopyOnWriteArrayList больше не дёргается на каждый урон,
 *    мутация и чистка памяти — только в purgeExpired() (purge-таск, 1200 тиков).
 */
public final class ResistService {

    /** Временной или постоянный модификатор резиста (проценты). */
    public record Modifier(String source, double physicalPct, double magicPct, long expiresAt) {
        public boolean isPermanent() { return expiresAt == Long.MAX_VALUE; }
    }

    /** Разбивка для /rc debug: база + активные модификаторы + итог. */
    public record Breakdown(double basePhysical, double baseMagic,
                            List<Modifier> active,
                            double physicalTotal, double magicTotal) {
    }

    /** Кэш факторов на тик; main-thread only. */
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
        return plugin.getConfig().getDouble("resist.cap", 90.0);
    }

    public double basePhysical(PlayerClass pc) {
        return plugin.getConfig().getDouble(
                "resist.classes." + pc.name() + ".physical", defaultPhysical(pc));
    }

    public double baseMagic(PlayerClass pc) {
        return plugin.getConfig().getDouble(
                "resist.classes." + pc.name() + ".magic", defaultMagic(pc));
    }

    private static double defaultPhysical(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 27.0;
            case ROGUE -> 14.0;
            case MAGE -> 12.0;
            case PRIEST -> 16.0;
            case HUNTER -> 16.0;
        };
    }

    private static double defaultMagic(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 12.0;
            case ROGUE -> 12.0;
            case MAGE -> 26.0;
            case PRIEST -> 30.0;
            case HUNTER -> 12.0;
        };
    }

    /** Временной модификатор (эффект-баф). Идемпотентно: источник перезаписывается. */
    public void addTimedModifier(UUID uuid, String source,
                                 double physicalPct, double magicPct, long millis) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, physicalPct, magicPct,
                        System.currentTimeMillis() + millis));
        invalidate(uuid);
    }

    /** Постоянный модификатор (спек-пассивки): expiresAt = Long.MAX_VALUE. */
    public void addPermanentModifier(UUID uuid, String source,
                                     double physicalPct, double magicPct) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, physicalPct, magicPct, Long.MAX_VALUE));
        invalidate(uuid);
    }

    /** Есть ли активный (не истёкший) модификатор с данным источником. Без мутации. */
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

    /** Снять все модификаторы источника (респец, смена класса, баф-офф). */
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

    /** Разбивка база+модификаторы (для /rc debug и пересчёта кэша). Без мутации. */
    public Breakdown breakdown(UUID uuid) {
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
        return new Breakdown(basePhys, baseMagic, active, clamp(phys), clamp(magic));
    }

    /** Итоговый физрезист, % (без кэша — для отображения). */
    public double physicalResist(UUID uuid) { return breakdown(uuid).physicalTotal(); }

    /** Итоговый магрезист, % (без кэша — для отображения). */
    public double magicResist(UUID uuid) { return breakdown(uuid).magicTotal(); }

    /** Множитель входящего физического урона (0..1), кэш на тик. */
    public double physicalFactor(UUID uuid) { return factors(uuid).phys; }

    /** Множитель входящего магического урона (0..1), кэш на тик. */
    public double magicFactor(UUID uuid) { return factors(uuid).magic; }

    /** 1.6.3: пересчёт не чаще раза в тик на игрока; инвалидация сбрасывает кэш. */
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

    /** Сброс кэша факторов игрока (любое изменение модификаторов). */
    public void invalidate(UUID uuid) {
        FactorCache cache = factorCache.get(uuid);
        if (cache != null) {
            cache.tick = -1;
        }
    }

    /** Чистка истёкших модификаторов и кэшей (purge-таск, 1200 тиков). */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        modifiers.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
        // память: кэши оффлайн-игроков не держим
        factorCache.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null
                && !modifiers.containsKey(uuid));
    }

    /** Полная чистка игрока (PlayerQuit). */
    public void clear(UUID uuid) {
        modifiers.remove(uuid);
        factorCache.remove(uuid);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(cap(), value));
    }
}
