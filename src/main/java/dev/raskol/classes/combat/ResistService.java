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
 * Эффективный резист = база класса (конфиг resist.classes.*) + временные
 * модификаторы (эффекты/спек-пассивки/будущие предметы), всё в процентах,
 * суммарно ограничено капом resist.cap (дефолт 90). Чистый урон игнорирует
 * резисты целиком — он через этот сервис не проходит.
 * Базы по ТЗ 1.6.0: Воин 12/27, Разбойник 12/14, Маг 26/12, Жрец 30/16,
 * Охотник 12/16 (маг/физ).
 */
public final class ResistService {

    /** Временной модификатор резиста (проценты, может быть отрицательным). */
    public record Modifier(String source, double physicalPct, double magicPct, long expiresAt) {
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

    /** Кап суммарного резиста, % (дефолт 90). */
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

    /** Добавить временной модификатор (эффект, спек-пассивка, будущий предмет). */
    public void addTimedModifier(UUID uuid, String source,
                                 double physicalPct, double magicPct, long millis) {
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, physicalPct, magicPct,
                        System.currentTimeMillis() + millis));
    }

    /** Полный расчёт с разбивкой (для /rc debug и боевых вызовов). */
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

    /** Итоговый физрезист игрока, % (0..cap). */
    public double physicalResist(UUID uuid) {
        return breakdown(uuid).physicalTotal();
    }

    /** Итоговый магрезист игрока, % (0..cap). */
    public double magicResist(UUID uuid) {
        return breakdown(uuid).magicTotal();
    }

    /** Множитель входящего физического урона (0..1). */
    public double physicalFactor(UUID uuid) {
        return 1.0 - physicalResist(uuid) / 100.0;
    }

    /** Множитель входящего магического урона (0..1). */
    public double magicFactor(UUID uuid) {
        return 1.0 - magicResist(uuid) / 100.0;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(cap(), value));
    }

    private List<Modifier> activeModifiers(UUID uuid) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        list.removeIf(m -> m.expiresAt() <= now);
        return list;
    }

    /** Чистка истёкших модификаторов (общий purge-таск). */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        modifiers.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> m.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
    }

    /** Полная чистка игрока (PlayerQuit). */
    public void clear(UUID uuid) {
        modifiers.remove(uuid);
    }
}
