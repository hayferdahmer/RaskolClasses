// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;

import java.util.UUID;

/**
 * Мост в economy-контракт RaskolCore (1.4.0, Пакет 3).
 * Рефлексия по двум причинам:
 *  1) не тащим raskol-core в pom (нет публичного Maven-репо);
 *  2) на рантайме классы Core видны через softdepend в plugin.yml.
 * Без Core/без провайдера — available() == false, респец блокируется.
 *
 * FIX: ленивый резолв реестра — balance()/withdraw() сами инициируют
 * подключение, иначе первый вызов до available() видел registry == null
 * и возвращал 0 при живом балансе.
 */
public final class EconomyHook {

    private final RaskolClasses plugin;
    private Object registry;
    private boolean attempted;

    public EconomyHook(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** Контракт жив и провайдер зарегистрирован. */
    public boolean available() {
        return provider() != null;
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
                plugin.getLogger().info("Economy: контракт RaskolCore подключён");
            }
            return reg;
        } catch (Throwable t) {
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
