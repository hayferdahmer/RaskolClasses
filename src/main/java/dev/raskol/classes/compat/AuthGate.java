// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.compat;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 1.5.8: единый гейт совместимости.
 *  - AuthMe-gate: неаутентифицированные игроки (offline-mode, до /login)
 *    не пользуют свитки и способности. AuthMe дёргается через рефлексию
 *    (fr.xephi.authme.api.v3.AuthMeApi#isAuthenticated) — жёсткой зависимости
 *    нет; если AuthMe отсутствует или рефлексия сломалась — гейт открыт
 *    (fail-open + warning в лог один раз).
 *  - Creative-gate: способности недоступны в creative, кроме
 *    raskolclasses.admin (админ тестирует свободно).
 * Гейты: compat.authme-gate (default true), compat.block-casts-in-creative (default true).
 */
public final class AuthGate {

    private static Object authApi;
    private static Method authenticatedMethod;
    private static boolean reflectionFailed;

    private AuthGate() {
    }

    /** true если игроку разрешено пользоваться классовым UI/свитками. */
    public static boolean allowed(RaskolClasses plugin, Player player) {
        if (!plugin.getConfig().getBoolean("compat.authme-gate", true)) {
            return true;
        }
        if (plugin.getServer().getPluginManager().getPlugin("AuthMe") == null) {
            return true;
        }
        if (reflectionFailed) {
            return true;
        }
        try {
            if (authApi == null) {
                Class<?> clazz = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
                authApi = clazz.getMethod("getInstance").invoke(null);
                authenticatedMethod = clazz.getMethod("isAuthenticated", Player.class);
            }
            Object result = authenticatedMethod.invoke(authApi, player);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            reflectionFailed = true;
            plugin.getLogger().warning("AuthGate: рефлексия AuthMe недоступна — гейт отключён ("
                    + t.getClass().getSimpleName() + ")");
            return true;
        }
    }

    /** true если игроку разрешено КАСТОВАТЬ/СТАВИТЬ (auth + creative-гейт). */
    public static boolean canAct(RaskolClasses plugin, Player player) {
        if (!allowed(plugin, player)) {
            return false;
        }
        return !plugin.getConfig().getBoolean("compat.block-casts-in-creative", true)
                || player.getGameMode() != GameMode.CREATIVE
                || player.hasPermission("raskolclasses.admin");
    }
}
