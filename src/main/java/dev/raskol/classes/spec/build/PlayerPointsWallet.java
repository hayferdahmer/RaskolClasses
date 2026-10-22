// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 1.14.15: reflection-обёртка над PlayerPoints API.
 *
 * Класс PlayerPoints: org.black_ixx.playerpoints.PlayerPoints
 * API-объект: PlayerPoints.getAPI() возвращает org.black_ixx.playerpoints.PlayerPointsAPI
 * Методы API:
 *   - int look(UUID) — баланс
 *   - boolean take(UUID, int) — списание
 *   - boolean give(UUID, int) — начисление
 *
 * Reflection защищает от compile-зависимости от PlayerPoints: если плагин
 * отсутствует или версии несовместимы, BuildWalletFactory вернёт NullWallet.
 *
 * Ошибки reflection логируются один раз при инициализации и при каждом сбое
 * (чтобы видеть деградацию в рантайме).
 */
public final class PlayerPointsWallet implements ImperialWallet {

    private static final Logger LOG = Logger.getLogger("RaskolClasses");

    private final Object api;
    private final Method lookMethod;
    private final Method takeMethod;
    private final Method giveMethod;

    private PlayerPointsWallet(Object api, Method look, Method take, Method give) {
        this.api = api;
        this.lookMethod = look;
        this.takeMethod = take;
        this.giveMethod = give;
    }

    /**
     * Попытка построить кошелёк. Возвращает null, если PlayerPoints недоступен
     * или методы API не совпадают с ожидаемыми сигнатурами.
     */
    public static PlayerPointsWallet tryCreate() {
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp == null || !pp.isEnabled()) {
            return null;
        }
        try {
            Class<?> mainClass = pp.getClass();
            Method getApi = mainClass.getMethod("getAPI");
            Object api = getApi.invoke(pp);
            if (api == null) {
                LOG.warning("raskolspec+: PlayerPoints.getAPI() вернул null");
                return null;
            }
            Class<?> apiClass = api.getClass();
            Method look = apiClass.getMethod("look", UUID.class);
            Method take = apiClass.getMethod("take", UUID.class, int.class);
            Method give = apiClass.getMethod("give", UUID.class, int.class);
            LOG.info("raskolspec+: PlayerPoints API подключён");
            return new PlayerPointsWallet(api, look, take, give);
        } catch (ReflectiveOperationException ex) {
            LOG.warning("raskolspec+: PlayerPoints API несовместим: " + ex.getMessage());
            return null;
        }
    }

    @Override
    public int balance(UUID uuid) {
        if (uuid == null) return 0;
        try {
            Object v = lookMethod.invoke(api, uuid);
            return v instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException ex) {
            LOG.warning("raskolspec+: PlayerPoints.look сбой: " + ex.getMessage());
            return 0;
        }
    }

    @Override
    public boolean take(UUID uuid, int amount) {
        if (uuid == null || amount < 0) return false;
        if (amount == 0) return true;
        try {
            Object v = takeMethod.invoke(api, uuid, amount);
            return Boolean.TRUE.equals(v);
        } catch (ReflectiveOperationException ex) {
            LOG.warning("raskolspec+: PlayerPoints.take сбой: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean give(UUID uuid, int amount) {
        if (uuid == null || amount < 0) return false;
        if (amount == 0) return true;
        try {
            return Boolean.TRUE.equals(giveMethod.invoke(api, uuid, amount));
        } catch (ReflectiveOperationException ex) {
            LOG.warning("raskolspec+: PlayerPoints.give сбой: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String implementationName() {
        return "playerpoints";
    }
}
