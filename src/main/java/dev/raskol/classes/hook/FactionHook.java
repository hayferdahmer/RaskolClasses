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
 *
 * 1.15.6 (Волна 6 часть 2, аудит 2.6-104): retry при позднем RaskolCore.
 * Раньше флаг attempted ставился в true при первой ошибке (Core ещё не загрузился),
 * и дальнейшие вызовы возвращали "" навсегда даже после успешной загрузки Core.
 * Теперь attempted сбрасывается при ошибке — каждый cache miss retry'ит reflection,
 * и после загрузки Core фракция резолвится штатно.
 */
public final class FactionHook {

    private static final long CACHE_TTL_MILLIS = 5_000L;

    private record CacheEntry(String faction, long expiresAt) { }

    private final RaskolClasses plugin;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private boolean attempted;
    private Method passportOf;
    private Method factionGetter;
    private boolean warnedMissing = false;

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
                // 1.15.6 (2.6-104): сброс attempted — retry при следующем cache miss
                attempted = false;
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
            // 1.15.6 (2.6-104): сброс attempted — retry при следующем cache miss
            attempted = false;
            passportOf = null;
            factionGetter = null;
            if (!warnedMissing) {
                warnedMissing = true;
                plugin.getLogger().warning("FactionHook: RaskolCore недоступен — "
                        + "фракция будет резолвиться при каждом cache miss (5 с)");
            }
            return "";
        }
    }
}
