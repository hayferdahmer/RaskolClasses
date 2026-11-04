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
 *    (fr.xephi.authme.api.v3.AuthMeApi#isAuthenticated) — жёсткой зависимости нет.
 *  - Creative-gate: способности недоступны в creative, кроме raskolclasses.admin.
 *
 * 1.15.5 (аудит 2.7-3): FAIL-CLOSED. Ранее сломанная рефлексия отключала гейт
 * (fail-open) — неаутентифицированный игрок получал полный доступ к кастам/свиткам.
 * Теперь при ошибке рефлексии доступ ЗАПРЕЩЁН (return false) + SEVERE-лог один раз.
 * Аварийный люк для ops: compat.authme-fail-open=true возвращает старое поведение
 * (использовать только если рефлексия ломается на легитимном билде AuthMe и нужно
 * временно открыть сервер; по умолчанию false).
 * Отсутствие самого плагина AuthMe по-прежнему = гейт не нужен (return true).
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
            return true; // плагина нет — аутентификацию контролировать нечему
        }
        if (reflectionFailed) {
            return plugin.getConfig().getBoolean("compat.authme-fail-open", false);
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
            plugin.getLogger().severe("AuthGate: рефлексия AuthMe недоступна — гейт ЗАКРЫТ (fail-closed, "
                    + t.getClass().getSimpleName() + "). Аварийный люк: compat.authme-fail-open=true");
            return plugin.getConfig().getBoolean("compat.authme-fail-open", false);
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
