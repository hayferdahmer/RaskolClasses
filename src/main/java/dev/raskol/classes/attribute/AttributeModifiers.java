// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P4c): слой модификаторов атрибутов (STR/AGI/INT), вынесенный из AttributeService.
 * Чистое хранилище без инвалидаций: invalidate-логику держит фасад.
 * Источники модификаторов: спеки, таланты, сеты (set-bonus-*), руна (frost_rune_int),
 * эффекты способностей.
 */
public final class AttributeModifiers {

    /** Временный или постоянный модификатор атрибутов с именем источника. */
    public record Modifier(String source, double str, double agi, double intel, long expiresAt) {
        public boolean isPermanent() {
            return expiresAt == Long.MAX_VALUE;
        }
    }

    private final Map<UUID, List<Modifier>> modifiers = new ConcurrentHashMap<>();

    public void addTimedModifier(UUID uuid, String source,
                                 double str, double agi, double intel, long millis) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new ArrayList<>())
                .add(new Modifier(source, str, agi, intel, System.currentTimeMillis() + millis));
    }

    public void addPermanentModifier(UUID uuid, String source,
                                     double str, double agi, double intel) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new ArrayList<>())
                .add(new Modifier(source, str, agi, intel, Long.MAX_VALUE));
    }

    public void removeModifiersBySource(UUID uuid, String source) {
        List<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return;
        }
        synchronized (list) {
            list.removeIf(m -> m.source().equals(source));
        }
        if (list.isEmpty()) {
            modifiers.remove(uuid);
        }
    }

    public boolean hasModifier(UUID uuid, String source) {
        List<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            for (Modifier m : list) {
                if (m.source().equals(source) && (m.isPermanent() || m.expiresAt() > now)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Modifier> activeModifiers(UUID uuid) {
        List<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            List<Modifier> out = new ArrayList<>();
            for (Modifier m : list) {
                if (m.isPermanent() || m.expiresAt() > now) {
                    out.add(m);
                }
            }
            return out;
        }
    }

    /** Чистка протухших модификаторов и пустых списков (вызывает фасад по расписанию). */
    public void purgeExpired(long now) {
        modifiers.entrySet().removeIf(entry -> {
            List<Modifier> list = entry.getValue();
            synchronized (list) {
                list.removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
                return list.isEmpty();
            }
        });
    }

    public void clear(UUID uuid) {
        modifiers.remove(uuid);
    }

    public int trackedPlayers() {
        return modifiers.size();
    }
}
