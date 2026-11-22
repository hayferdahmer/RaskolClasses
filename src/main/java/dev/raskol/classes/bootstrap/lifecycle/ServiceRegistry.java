// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Типизированный реестр зависимостей этапа bootstrap.
 *
 * <p>Сервисы регистрируются во время сборки графа. Чтение разрешено только
 * после seal(), чтобы потребители не получали частично собранный набор.</p>
 *
 * <p>Реестр не является глобальным Service Locator: его следует использовать
 * в координаторе сборки и передавать модулям только необходимые контракты.</p>
 */
public final class ServiceRegistry implements AutoCloseable {

    private final Map<Class<?>, Object> services = new HashMap<>();
    private boolean sealed;
    private boolean closed;

    /**
     * Регистрирует экземпляр под его явным контрактным типом.
     *
     * @throws IllegalStateException если реестр запечатан/закрыт или тип уже зарегистрирован
     */
    public synchronized <T> void register(Class<T> serviceType, T service) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(service, "service");

        ensureOpen();
        if (sealed) {
            throw new IllegalStateException("Реестр сервисов уже запечатан");
        }
        if (!serviceType.isInstance(service)) {
            throw new IllegalArgumentException(
                    "Экземпляр " + service.getClass().getName() + " не реализует контракт " + serviceType.getName());
        }
        if (services.containsKey(serviceType)) {
            throw new IllegalStateException("Сервис уже зарегистрирован: " + serviceType.getName());
        }

        services.put(serviceType, service);
    }

    /** Завершает регистрацию. Повторный вызов безопасен, пока реестр открыт. */
    public synchronized void seal() {
        ensureOpen();
        sealed = true;
    }

    public synchronized boolean isSealed() {
        return sealed;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Разрешает обязательную зависимость после завершения регистрации.
     *
     * @throws IllegalStateException если реестр не запечатан, закрыт или сервис отсутствует
     */
    public synchronized <T> T require(Class<T> serviceType) {
        ensureSealed();
        Objects.requireNonNull(serviceType, "serviceType");

        Object service = services.get(serviceType);
        if (service == null) {
            throw new IllegalStateException("Обязательный сервис не зарегистрирован: " + serviceType.getName());
        }
        return serviceType.cast(service);
    }

    /** Разрешает необязательную зависимость после завершения регистрации. */
    public synchronized <T> Optional<T> find(Class<T> serviceType) {
        ensureSealed();
        Objects.requireNonNull(serviceType, "serviceType");

        Object service = services.get(serviceType);
        return Optional.ofNullable(serviceType.cast(service));
    }

    public synchronized int size() {
        return services.size();
    }

    /** Закрывает реестр и освобождает ссылки на зарегистрированные сервисы. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        services.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Реестр сервисов закрыт");
        }
    }

    private void ensureSealed() {
        ensureOpen();
        if (!sealed) {
            throw new IllegalStateException("Нельзя разрешать сервисы до завершения регистрации");
        }
    }
}
