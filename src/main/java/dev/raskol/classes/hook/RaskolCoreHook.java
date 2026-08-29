// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.config.RaskolConfig;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 1.3.2: изолированный мост к RaskolCore. Работает через отражение по
 * класслоадеру Core-плагина — прямой Class.forName невозможен, т.к. Bukkit
 * изолирует класслоадеры плагинов. Без RaskolCore: available() == false,
 * resolveClass() == null, плагин живёт через LP-фолбэк.
 */
public final class RaskolCoreHook {

    private static final String CORE_PLUGIN = "RaskolCore";
    private static final String API_FQN = "dev.raskol.core.RaskolCoreAPI";
    private static final String PASSPORT_FQN = "dev.raskol.core.passport.RaskolPassport";

    private final Plugin plugin;
    private final RaskolConfig config;

    private ClassLoader coreClassLoader;
    private Method apiIsAvailable;
    private Method apiPassportOf;
    private Method passportPlayerClass;
    private boolean wired = false;
    private boolean warnedOnce = false;

    public RaskolCoreHook(Plugin plugin, RaskolConfig config) {
        this.plugin = plugin;
        this.config = config;
        wire();
    }

    private void wire() {
        if (!config.raskolCoreEnabled()) {
            plugin.getLogger().info("RaskolCore-хук выключен конфигом (hooks.raskolcore.enabled=false)");
            return;
        }
        PluginManager pm = plugin.getServer().getPluginManager();
        Plugin core = pm.getPlugin(CORE_PLUGIN);
        if (core == null || !core.isEnabled()) {
            plugin.getLogger().info("RaskolCore не найден или не включён — класс читается из LuckPerms");
            return;
        }
        try {
            this.coreClassLoader = core.getClass().getClassLoader();
            Class<?> apiCls = Class.forName(API_FQN, false, coreClassLoader);
            Class<?> passportCls = Class.forName(PASSPORT_FQN, false, coreClassLoader);
            this.apiIsAvailable = apiCls.getMethod("isAvailable");
            this.apiPassportOf = apiCls.getMethod("passportOf", UUID.class);
            this.passportPlayerClass = passportCls.getMethod("playerClass");
            this.wired = true;
            plugin.getLogger().info("RaskolCore-хук: подключён (класс читается из паспорта Core, LP — фолбэк)");
        } catch (Throwable t) {
            if (!warnedOnce) {
                plugin.getLogger().warning("RaskolCore обнаружен, но API несовместим: " + t.getMessage()
                        + " — переключаемся на LuckPerms-фолбэк");
                warnedOnce = true;
            }
            this.wired = false;
        }
    }

    /** Core подключён, доступен и API-вызовы работают. */
    public boolean isAvailable() {
        if (!wired) {
            return false;
        }
        try {
            Object result = apiIsAvailable.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Читает класс из паспорта Core. null = класс не задан (паспорт EMPTY). */
    public PlayerClass resolveClass(UUID uuid) {
        if (!wired || uuid == null) {
            return null;
        }
        try {
            Object passport = apiPassportOf.invoke(null, uuid);
            if (passport == null) {
                return null;
            }
            Object coreId = passportPlayerClass.invoke(passport);
            if (!(coreId instanceof String id)) {
                return null;
            }
            return PlayerClass.fromCoreId(id);
        } catch (Throwable t) {
            return null;
        }
    }
}
