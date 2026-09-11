// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * 1.8.1 (S4): Towny-гейт телепортов (hermes_step): нельзя блинковаться в чужой
 * клейм в обход ворот/осад/прав города. Рефлексия (softdepend Towny),
 * статический ленивый init, без изменения RaskolClasses.
 *
 * Правила canBlink:
 *  - Towny отсутствует            → разрешено;
 *  - Towny есть, рефлексия сломана → ЗАПРЕЩЕНО (fail closed, эксплойт не живёт);
 *  - цель в wilderness            → разрешено;
 *  - цель в том же городе         → разрешено;
 *  - цель в городе, где игрок резидент → разрешено;
 *  - чужой город                  → запрещено.
 */
public final class TownyHook {

    private static boolean attempted = false;
    private static boolean townyPresent = false;
    private static boolean available = false;
    private static Object api;
    private static Method getTownBlock;    // TownyAPI.getTownBlock(Location) -> TownBlock | null
    private static Method townOrNull;      // TownBlock.getTownOrNull() -> Town | null
    private static Method townGetName;     // Town.getName() -> String
    private static Method townHasResident; // Town.hasResident(String) -> boolean

    private TownyHook() {
    }

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
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.object.TownyAPI");
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
            available = api != null;
            if (available) {
                plugin.getLogger().info("TownyHook: гейт блинка через клеймы включён");
            } else {
                plugin.getLogger().warning("TownyHook: TownyAPI null — блинк в клеймы ЗАПРЕЩЁН (fail closed)");
            }
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("TownyHook: рефлексия недоступна — блинк в клеймы ЗАПРЕЩЁН (fail closed): " + t);
        }
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
            if (town != null
                    && Boolean.TRUE.equals(townHasResident.invoke(town, player.getName()))) {
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
