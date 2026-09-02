// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Читает фракцию (rassvet/valradis/"") из паспорта RaskolCore (1.4.0, Пакет 4).
 * Рефлексия — как EconomyHook: нет raskol-core в pom, классы видны через softdepend.
 * Кэш 5 с, чтобы не дёргать паспорт на каждом тике ауры.
 */
public final class FactionHook {

    private static final long CACHE_TTL_MILLIS = 5_000L;

    private record CacheEntry(String faction, long expiresAt) { }

    private final RaskolClasses plugin;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private boolean attempted;
    private Method passportOf;
    private Method factionGetter;

    public FactionHook(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Фракция игрока: "rassvet" / "valradis" / "" (вне короны или Core недоступен). */
    public String factionOf(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(uuid);
        if (entry != null && entry.expiresAt() > now) {
            return entry.faction();
        }
        String faction = resolve(uuid);
        cache.put(uuid, new CacheEntry(faction, now + CACHE_TTL_MILLIS));
        return faction;
    }

    private String resolve(UUID uuid) {
        try {
            if (!attempted) {
                attempted = true;
                Class<?> api = Class.forName("dev.raskol.core.RaskolCoreAPI");
                passportOf = api.getMethod("passportOf", UUID.class);
            }
            if (passportOf == null) {
                return "";
            }
            Object passport = passportOf.invoke(null, uuid);
            if (passport == null) {
                return "";
            }
            if (factionGetter == null) {
                factionGetter = passport.getClass().getMethod("faction");
            }
            Object value = factionGetter.invoke(passport);
            return value == null ? "" : value.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
