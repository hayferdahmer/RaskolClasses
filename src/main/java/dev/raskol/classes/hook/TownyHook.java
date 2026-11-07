// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.raskol.classes.RaskolClasses;

/**
 * 1.8.1 (S4): Towny-гейт телепортов (hermes_step, shadowstep): нельзя блинковаться
 * в чужой клейм в обход ворот/осад/прав города. Рефлексия (softdepend Towny),
 * статический ленивый init, без изменения RaskolClasses.
 *
 * Правила canBlink:
 *  - Towny отсутствует            → разрешено;
 *  - Towny есть, рефлексия сломана → ЗАПРЕЩЕНО (fail closed, эксплойт не живёт);
 *  - цель в wilderness            → разрешено;
 *  - цель в том же городе         → разрешено;
 *  - цель в городе, где игрок резидент → разрешено;
 *  - чужой город                  → запрещено.
 *
 * 1.15.6 (Волна 7b, аудит 2.7-1): поддержка двух пакетов TownyAPI:
 *   - Towny 0.98.x и ранее: com.palmergames.bukkit.towny.object.TownyAPI
 *   - Towny 0.99.x+: com.palmergames.bukkit.towny.TownyAPI
 *   init пробует оба варианта; если ни один не резолвится — fail closed.
 *
 * 1.15.7 (Волна 7c, аудит 2.7-9): добавлен canBuild(plugin, player, location)
 *   для инсталляций. Использует TownyAPI.testBuild(Location, Player) если доступен,
 *   иначе fallback: wilderness → разрешено, чужой город без резидентства → запрещено.
 *   Fail closed: если рефлексия сломана — canBuild=false.
 */
public final class TownyHook {

    private static boolean attempted = false;
    private static boolean townyPresent = false;
    private static boolean available = false;
    private static Object api;
    private static Method getTownBlock; // TownyAPI.getTownBlock(Location) -> TownBlock | null
    private static Method townOrNull; // TownBlock.getTownOrNull() -> Town | null
    private static Method townGetName; // Town.getName() -> String
    private static Method townHasResident; // Town.hasResident(String) -> boolean
    /** 1.15.7 (2.7-9): TownyAPI.testBuild(Location, Player) -> boolean (если доступен). */
    private static Method testBuild;

    private TownyHook() {}

    private static synchronized void init(RaskolClasses plugin) {
        if (attempted) {
            return;
        }
        attempted = true;
        Plugin towny = plugin.getServer().getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            townyPresent = false;
            available = false;
            return;
        }
        townyPresent = true;
        try {
            // 1.15.6 (2.7-1): пробуем оба пакета TownyAPI (0.98 vs 0.99+)
            Class<?> apiClass = tryLoadApiClass();
            if (apiClass == null) {
                available = false;
                plugin.getLogger()
                        .warning(
                                "TownyHook: TownyAPI не найден ни в одном пакете — блинк в клеймы ЗАПРЕЩЁН (fail closed)");
                return;
            }
            api = apiClass.getMethod("getInstance").invoke(null);
            getTownBlock = apiClass.getMethod("getTownBlock", Location.class);
            Class<?> tbClass = Class.forName("com.palmergames.bukkit.towny.object.TownBlock");
            Method townGetter;
            try {
                townGetter = tbClass.getMethod("getTownOrNull");
            } catch (NoSuchMethodException e) {
                townGetter = tbClass.getMethod("getTown");
            }
            townOrNull = townGetter;
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            townGetName = townClass.getMethod("getName");
            townHasResident = townClass.getMethod("hasResident", String.class);
            // 1.15.7 (2.7-9): testBuild для canBuild
            try {
                testBuild = apiClass.getMethod("testBuild", Location.class, Player.class);
            } catch (NoSuchMethodException ignored) {
                // Старая версия Towny без testBuild — fallback на резидентство
                testBuild = null;
            }
            available = api != null;
            if (available) {
                plugin.getLogger()
                        .info("TownyHook: гейт блинка через клеймы включён (API=" + apiClass.getName() + ", testBuild="
                                + (testBuild != null) + ")");
            } else {
                plugin.getLogger().warning("TownyHook: TownyAPI null — блинк в клеймы ЗАПРЕЩЁН (fail closed)");
            }
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("TownyHook: рефлексия недоступна — блинк в клеймы ЗАПРЕЩЁН (fail closed): " + t);
        }
    }

    /**
     * 1.15.6 (2.7-1): пробуем загрузить TownyAPI из двух пакетов.
     * Towny 0.98.x: com.palmergames.bukkit.towny.object.TownyAPI
     * Towny 0.99.x+: com.palmergames.bukkit.towny.TownyAPI
     *
     * @return Class TownyAPI или null, если ни один не найден
     */
    private static Class<?> tryLoadApiClass() {
        try {
            return Class.forName("com.palmergames.bukkit.towny.TownyAPI");
        } catch (ClassNotFoundException ignored) {
        }
        try {
            return Class.forName("com.palmergames.bukkit.towny.object.TownyAPI");
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    /** Разрешён ли телепорт игрока из from в to с учётом клеймов Towny. */
    public static boolean canBlink(RaskolClasses plugin, Player player, Location from, Location to) {
        init(plugin);
        if (!townyPresent) {
            return true;
        }
        if (!available) {
            return false; // fail closed
        }
        try {
            String townFrom = townName(getTownBlock.invoke(api, from));
            String townTo = townName(getTownBlock.invoke(api, to));
            if (townTo == null) {
                return true; // wilderness
            }
            if (townTo.equals(townFrom)) {
                return true; // тот же город
            }
            Object tbTo = getTownBlock.invoke(api, to);
            Object town = tbTo == null ? null : townOrNull.invoke(tbTo);
            if (town != null && Boolean.TRUE.equals(townHasResident.invoke(town, player.getName()))) {
                return true; // резидент этого города
            }
            return false; // чужой город
        } catch (Throwable t) {
            return false; // fail closed
        }
    }

    /**
     * 1.15.7 (2.7-9): может ли игрок строить в локации.
     * Использует TownyAPI.testBuild(Location, Player) если доступен,
     * иначе fallback: wilderness → разрешено, резидент города → разрешено,
     * чужой город → запрещено.
     * Fail closed: если Towny недоступен или рефлексия сломана — false.
     */
    public static boolean canBuild(RaskolClasses plugin, Player player, Location location) {
        init(plugin);
        if (!townyPresent) {
            return true; // Towny нет — не ограничиваем
        }
        if (!available) {
            return false; // fail closed
        }
        try {
            // Предпочитаем testBuild (если доступен в этой версии Towny)
            if (testBuild != null) {
                Object result = testBuild.invoke(api, location, player);
                return Boolean.TRUE.equals(result);
            }
            // Fallback: проверяем резидентство в городе
            String townName = townName(getTownBlock.invoke(api, location));
            if (townName == null) {
                return true; // wilderness — строить можно
            }
            Object tb = getTownBlock.invoke(api, location);
            Object town = tb == null ? null : townOrNull.invoke(tb);
            if (town != null && Boolean.TRUE.equals(townHasResident.invoke(town, player.getName()))) {
                return true; // резидент этого города
            }
            return false; // чужой город
        } catch (Throwable t) {
            return false; // fail closed
        }
    }

    private static String townName(Object townBlock) throws Exception {
        if (townBlock == null) {
            return null;
        }
        Object town = townOrNull.invoke(townBlock);
        return town == null ? null : (String) townGetName.invoke(town);
    }
}
