// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.6.0: сопротивления урону (РЕЗИСТ).
 * Эффективный резист = база класса + временные/постоянные модификаторы
 * (эффекты/спек-пассивки/будущие предметы), всё в процентах, суммарно
 * ограничено капом resist.cap (дефолт 90). Чистый урон игнорирует резисты
 * целиком — он через этот сервис не проходит.
 *
 * Пакет 3: permanent-модификаторы (expiresAt = Long.MAX_VALUE) для спек-пассивок
 * типа Стража +10 физ; removeModifiersBySource() — снятие по источнику (респец,
 * смена класса, истечение бафа).
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

    private final RaskolClasses plugin;
    private final Map<UUID, CopyOnWriteArrayList<Modifier>> modifiers = new ConcurrentHashMap<>();

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

    /** Временной модификатор (эффект-баф, spect-активка). */
    public void addTimedModifier(UUID uuid, String source,
                                 double physicalPct, double magicPct, long millis) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, physicalPct, magicPct,
                        System.currentTimeMillis() + millis));
    }

    /**
     * Постоянный модификатор (спек-пассивка типа Страж +10 физ).
     * expiresAt = Long.MAX_VALUE — снимается только через removeModifiersBySource.
     */
    public void addPermanentModifier(UUID uuid, String source,
                                     double physicalPct, double magicPct) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, physicalPct, magicPct, Long.MAX_VALUE));
    }

    /** Есть ли активный модификатор с данным источником (для idempotent-логики). */
    public boolean hasModifier(UUID uuid, String source) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) return false;
        long now = System.currentTimeMillis();
        return list.stream()
                .anyMatch(m -> m.source().equals(source)
                        && (m.expiresAt() == Long.MAX_VALUE || m.expiresAt() > now));
    }

    /** Снять все модификаторы с данным источником (респец, смена класса, баф-офф). */
    public void removeModifiersBySource(UUID uuid, String source) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) return;
        list.removeIf(m -> m.source().equals(source));
        if (list.isEmpty()) modifiers.remove(uuid);
    }

    public Breakdown breakdown(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        double basePhys = pc != null ? basePhysical(pc) : 0.0;
        double baseMagic = pc != null ? baseMagic(pc) : 0.0;
        List<Modifier> active = activeModifiers(uuid);
        double phys = basePhys;
        double magic = baseMagic;
        for (Modifier m : active) {
            phys += m.physicalPct();
            magic += m.magicPct();
        }
        return new Breakdown(basePhys, baseMagic, active, clamp(phys), clamp(magic));
    }

    public double physicalResist(UUID uuid) { return breakdown(uuid).physicalTotal(); }
    public double magicResist(UUID uuid) { return breakdown(uuid).magicTotal(); }
    public double physicalFactor(UUID uuid) { return 1.0 - physicalResist(uuid) / 100.0; }
    public double magicFactor(UUID uuid) { return 1.0 - magicResist(uuid) / 100.0; }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(cap(), value));
    }

    private List<Modifier> activeModifiers(UUID uuid) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) return List.of();
        long now = System.currentTimeMillis();
        list.removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
        return list;
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        modifiers.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
    }

    public void clear(UUID uuid) { modifiers.remove(uuid); }
}
