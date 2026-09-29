// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.school;

import dev.raskol.classes.RaskolClasses;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.12.2 (Блок 1): стихийный слой резистов поверх резиста канала (ТЗ п.2–3).
 * Хранит source-модификаторы вида «источник → школа → %», суммирует активные
 * по школе и клампит в schools.elemental.resist-cap (дефолт 60%).
 *
 * Источники модификаторов (подключаются следующими блоками 1.12.2):
 *  - Блок 2: gear-трейты RaskolGear (PDC-ключи raskolgear:*_resist);
 *  - Блок 3: таланты (effect kind elem_resist) и спеки (passive-трейты);
 *  - уже сейчас: админ/ивенты через addPermanent/addTimed.
 *
 * Рубильник: schools.elemental.enabled=false → resistOf() всегда 0
 * (слой прозрачен, поведение 1.12.1).
 * Self-purge протухших таймерных модификаторов раз в 600 тиков (30 с).
 */
public final class ElementalResistService {

    /** Таймерный или постоянный стихийный модификатор с именем источника. */
    public record ElemMod(String source, School school, double pct, long expiresAt) {
        public boolean isPermanent() {
            return expiresAt == Long.MAX_VALUE;
        }
    }

    private static final long PURGE_TICKS = 600L;

    private final SchoolConfig schoolConfig;
    private final Map<UUID, CopyOnWriteArrayList<ElemMod>> mods = new ConcurrentHashMap<>();

    public ElementalResistService(RaskolClasses plugin, SchoolConfig schoolConfig) {
        this.schoolConfig = schoolConfig;
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::purgeExpired, PURGE_TICKS, PURGE_TICKS);
    }

    /** Рубильник слоя (schools.elemental.enabled). */
    public boolean enabled() {
        return schoolConfig.elementalEnabled();
    }

    public void addTimed(UUID uuid, String source, School school, double pct, long millis) {
        removeBySource(uuid, source);
        mods.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new ElemMod(source, school, sanitize(pct),
                        System.currentTimeMillis() + millis));
    }

    public void addPermanent(UUID uuid, String source, School school, double pct) {
        removeBySource(uuid, source);
        mods.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new ElemMod(source, school, sanitize(pct), Long.MAX_VALUE));
    }

    public void removeBySource(UUID uuid, String source) {
        CopyOnWriteArrayList<ElemMod> list = mods.get(uuid);
        if (list == null) {
            return;
        }
        list.removeIf(m -> m.source().equals(source));
        if (list.isEmpty()) {
            mods.remove(uuid);
        }
    }

    public void removeAll(UUID uuid) {
        mods.remove(uuid);
    }

    public boolean has(UUID uuid, String source) {
        CopyOnWriteArrayList<ElemMod> list = mods.get(uuid);
        if (list == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (ElemMod m : list) {
            if (m.source().equals(source) && (m.isPermanent() || m.expiresAt() > now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Суммарный стихийный резист школы для игрока, [0, resist-cap].
     * Выключенный слой → 0 (прозрачность к 1.12.1).
     */
    public double resistOf(UUID uuid, School school) {
        if (!enabled() || uuid == null || school == null) {
            return 0.0;
        }
        CopyOnWriteArrayList<ElemMod> list = mods.get(uuid);
        if (list == null || list.isEmpty()) {
            return 0.0;
        }
        long now = System.currentTimeMillis();
        double sum = 0.0;
        for (ElemMod m : list) {
            if (m.school() == school && (m.isPermanent() || m.expiresAt() > now)) {
                sum += m.pct();
            }
        }
        return Math.max(0.0, Math.min(sum, schoolConfig.elementalResistCap()));
    }

    /** Доля поглощения [0,1] для подстановки в SchoolMitigation.mitigationFor. */
    public double mitigationOf(UUID uuid, School school) {
        return resistOf(uuid, school) / 100.0;
    }

    /** Чистка протухших таймерных модификаторов и пустых списков. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        mods.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
    }

    public int trackedPlayers() {
        return mods.size();
    }

    private double sanitize(double pct) {
        return Double.isFinite(pct) && pct >= 0.0 ? pct : 0.0;
    }
}
