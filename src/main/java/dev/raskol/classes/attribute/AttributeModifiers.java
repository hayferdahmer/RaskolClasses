// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
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
 *
 * 1.15.6 (Волна 5, аудит 2.6-86): add-операции синхронизированы на списке.
 * Раньше `modifiers.computeIfAbsent(...).add(...)` выполнялось БЕЗ `synchronized`,
 * тогда как remove/has/active/purge входили в `synchronized (list)`. Между возвратом
 * ссылки из computeIfAbsent и add() другой поток мог войти в remove и итерировать
 * список, что ломало ConcurrentModificationException-гарантии ArrayList (ArrayList
 * не является thread-safe). Теперь addTimedModifier/addPermanentModifier держат
 * `synchronized (list)` на время removeIf+add — race закрыт, согласованность с
 * читающими методами восстановлена.
 */
public final class AttributeModifiers {

    /** Временный или постоянный модификатор атрибутов с именем источника. */
    public record Modifier(String source, double str, double agi, double intel, long expiresAt) {
        public boolean isPermanent() {
            return expiresAt == Long.MAX_VALUE;
        }
    }

    private final Map<UUID, List<Modifier>> modifiers = new ConcurrentHashMap<>();

    /**
     * 1.15.6 (2.6-86): synchronized на списке. Убирает предыдущий модификатор
     * того же source и добавляет новый — обе операции внутри одной блокировки,
     * что согласуется с читающими hasModifier/activeModifiers.
     */
    public void addTimedModifier(UUID uuid, String source,
                                 double str, double agi, double intel, long millis) {
        List<Modifier> list = modifiers.computeIfAbsent(uuid, k -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(m -> m.source().equals(source));
            list.add(new Modifier(source, str, agi, intel, System.currentTimeMillis() + millis));
        }
    }

    /**
     * 1.15.6 (2.6-86): synchronized на списке (см. addTimedModifier).
     */
    public void addPermanentModifier(UUID uuid, String source,
                                     double str, double agi, double intel) {
        List<Modifier> list = modifiers.computeIfAbsent(uuid, k -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(m -> m.source().equals(source));
            list.add(new Modifier(source, str, agi, intel, Long.MAX_VALUE));
        }
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
