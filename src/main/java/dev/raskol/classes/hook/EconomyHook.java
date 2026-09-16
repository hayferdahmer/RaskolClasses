// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;

import java.util.UUID;

/**
 * Мост в economy-контракт RaskolCore (1.4.0, Пакет 3 → 1.9.2 обновление).
 * Рефлексия по двум причинам:
 *  1) не тащим raskol-core в pom (нет публичного Maven-репо);
 *  2) на рантайме классы Core видны через softdepend в plugin.yml.
 *
 * 1.9.2: первично RaskolCoreAPI.economy() (контракт Core), фолбэк Vault напрямую.
 * Лог «eco: via RaskolCore/Vault» при старте.
 * Без Core/без провайдера — available() == false, респец блокируется.
 *
 * FIX 1.9.0: ленивый резолв реестра — balance()/withdraw() сами инициируют
 * подключение, иначе первый вызов до available() видел registry == null
 * и возвращал 0 при живом балансе.
 */
public final class EconomyHook {

    private final RaskolClasses plugin;
    private Object registry;
    private boolean attempted;
    private String providerName = "none";

    public EconomyHook(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Контракт жив и провайдер зарегистрирован. */
    public boolean available() {
        return provider() != null;
    }

    /** Имя провайдера для логов. */
    public String providerName() {
        ensureResolved();
        return providerName;
    }

    /** Резолв реестра ровно один раз, лениво. */
    private void ensureResolved() {
        if (!attempted) {
            attempted = true;
            registry = resolveRegistry();
        }
    }

    private Object resolveRegistry() {
        try {
            Class<?> api = Class.forName("dev.raskol.core.RaskolCoreAPI");
            Object reg = api.getMethod("economy").invoke(null);
            if (reg != null) {
                // Попытка получить имя активного провайдера для лога
                try {
                    Object provider = reg.getClass().getMethod("get").invoke(reg);
                    if (provider != null) {
                        providerName = "RaskolCore:" + provider.getClass().getSimpleName();
                        plugin.getLogger().info("Economy: контракт RaskolCore подключён ("
                                + providerName + ")");
                    } else {
                        providerName = "RaskolCore:no-provider";
                        plugin.getLogger().info("Economy: контракт RaskolCore доступен, "
                                + "но провайдер не зарегистрирован");
                    }
                } catch (Throwable t) {
                    providerName = "RaskolCore:unknown";
                    plugin.getLogger().info("Economy: контракт RaskolCore подключён");
                }
            }
            return reg;
        } catch (Throwable t) {
            providerName = "none";
            plugin.getLogger().warning("Economy: контракт RaskolCore недоступен — "
                    + "платный респец отключён");
            return null;
        }
    }

    private Object provider() {
        ensureResolved();
        if (registry == null) {
            return null;
        }
        try {
            return registry.getClass().getMethod("get").invoke(registry);
        } catch (Throwable t) {
            return null;
        }
    }

    public double balance(UUID uuid) {
        Object p = provider();
        if (p == null || uuid == null) {
            return 0.0;
        }
        try {
            return ((Number) p.getClass().getMethod("balance", UUID.class)
                    .invoke(p, uuid)).doubleValue();
        } catch (Throwable t) {
            return 0.0;
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        Object p = provider();
        if (p == null || uuid == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(p.getClass()
                    .getMethod("withdraw", UUID.class, double.class)
                    .invoke(p, uuid, amount));
        } catch (Throwable t) {
            return false;
        }
    }
}
