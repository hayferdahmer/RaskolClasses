// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Координирует полный запуск и остановку набора модулей плагина.
 *
 * <p>Перед первым вызовом модульного кода проверяется весь граф зависимостей.
 * Затем выполняются общие фазы initialize, register и start. При ошибке
 * координатор откатывает уже затронутые модули в обратном порядке.</p>
 *
 * <p>Координатор является одноразовым bootstrap-компонентом и не предназначен
 * для горячей перезагрузки. Reload будет отдельным протоколом приложения.</p>
 */
public final class BootstrapCoordinator implements AutoCloseable {

    private final String pluginId;
    private final Logger logger;
    private final Path dataDirectory;
    private final ServiceRegistry serviceRegistry;

    private List<ManagedModule> modules = List.of();
    private boolean startAttempted;
    private boolean closed;
    private ManagedModule failingModule;

    public BootstrapCoordinator(String pluginId, Logger logger, Path dataDirectory, ServiceRegistry serviceRegistry) {
        this.pluginId = requireText(pluginId, "pluginId");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "serviceRegistry");
    }

    /**
     * Проверяет граф и запускает все модули фазами.
     *
     * @throws IllegalStateException если координатор уже запускался или закрыт
     * @throws BootstrapException если одна из фаз завершилась ошибкой
     */
    public synchronized void start(Collection<? extends PluginModule> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (closed || startAttempted) {
            throw new IllegalStateException("BootstrapCoordinator уже запускался или закрыт");
        }

        // Проверка графа должна завершиться до любого вызова пользовательского кода.
        List<PluginModule> ordered = ModuleDependencyResolver.resolve(candidates);
        List<ManagedModule> managed = new ArrayList<>(ordered.size());
        for (PluginModule module : ordered) {
            managed.add(new ManagedModule(
                    module,
                    new ModuleContext(pluginId, logger.getName(), dataDirectory, new TaskScope()),
                    new ModuleLifecycle()));
        }

        modules = List.copyOf(managed);
        startAttempted = true;

        try {
            initializeAll();
            registerAll();
            serviceRegistry.seal();
            startAll();
        } catch (Throwable failure) {
            markFailure(failingModule, failure);
            rollback(failure);
            throw new BootstrapException("Не удалось запустить модули плагина " + pluginId, failure);
        } finally {
            failingModule = null;
        }
    }

    private void initializeAll() throws Exception {
        for (ManagedModule managed : modules) {
            failingModule = managed;
            managed.initializeAttempted = true;
            managed.module.initialize(managed.context);
            managed.lifecycle.initialized();
        }
        failingModule = null;
    }

    private void registerAll() throws Exception {
        for (ManagedModule managed : modules) {
            failingModule = managed;
            managed.module.register();
            managed.lifecycle.registered();
        }
        failingModule = null;
    }

    private void startAll() throws Exception {
        for (ManagedModule managed : modules) {
            failingModule = managed;
            managed.startAttempted = true;
            managed.module.start();
            managed.lifecycle.started();
        }
        failingModule = null;
    }

    /**
     * Останавливает модули в обратном порядке и закрывает реестр.
     * Ошибки отдельных модулей агрегируются; очистка остальных продолжается.
     */
    public synchronized void stop() {
        if (closed) {
            return;
        }
        closed = true;

        RuntimeException failure = null;
        for (int index = modules.size() - 1; index >= 0; index--) {
            ManagedModule managed = modules.get(index);
            if (!managed.initializeAttempted) {
                continue;
            }
            for (Throwable cleanupFailure : cleanup(managed)) {
                failure = appendFailure(failure, cleanupFailure);
            }
        }

        try {
            serviceRegistry.close();
        } catch (RuntimeException registryFailure) {
            failure = appendFailure(failure, registryFailure);
        }

        if (failure != null) {
            throw failure;
        }
    }

    private void rollback(Throwable primaryFailure) {
        for (int index = modules.size() - 1; index >= 0; index--) {
            ManagedModule managed = modules.get(index);
            if (!managed.initializeAttempted) {
                continue;
            }
            for (Throwable cleanupFailure : cleanup(managed)) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
        }

        try {
            serviceRegistry.close();
        } catch (RuntimeException registryFailure) {
            primaryFailure.addSuppressed(registryFailure);
        }
        closed = true;
    }

    /**
     * Очищает один модуль, продолжая освобождение ресурсов после каждой ошибки.
     */
    private static List<Throwable> cleanup(ManagedModule managed) {
        List<Throwable> failures = new ArrayList<>();
        try {
            managed.lifecycle.beginStopping();
            if (managed.startAttempted) {
                managed.module.stop();
            }
        } catch (Throwable stopFailure) {
            managed.lifecycle.failed(stopFailure);
            failures.add(stopFailure);
        }

        if (managed.lifecycle.state() == ModuleState.STOPPING) {
            managed.lifecycle.stopped();
        }

        // Сначала отменяем задачи, чтобы они не обращались к уже закрываемым ресурсам модуля.
        try {
            managed.context.taskScope().close();
        } catch (Throwable taskFailure) {
            managed.lifecycle.failed(taskFailure);
            failures.add(taskFailure);
        }

        try {
            managed.module.close();
        } catch (Throwable closeFailure) {
            managed.lifecycle.failed(closeFailure);
            failures.add(closeFailure);
        }

        ModuleState state = managed.lifecycle.state();
        if (state == ModuleState.STOPPED || state == ModuleState.FAILED || state == ModuleState.NEW) {
            managed.lifecycle.closed();
        }
        return failures;
    }

    private void markFailure(ManagedModule managed, Throwable failure) {
        if (managed == null) {
            return;
        }
        if (managed.lifecycle.state() != ModuleState.CLOSED && managed.lifecycle.state() != ModuleState.FAILED) {
            managed.lifecycle.failed(failure);
        }
    }

    private static RuntimeException appendFailure(RuntimeException current, Throwable next) {
        if (current == null) {
            current = new IllegalStateException("Одна или несколько ошибок при остановке модулей");
        }
        current.addSuppressed(next);
        return current;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /** Возвращает фактический детерминированный порядок модулей. */
    public synchronized List<String> moduleOrder() {
        return modules.stream().map(managed -> managed.module.id()).toList();
    }

    @Override
    public void close() {
        stop();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " не может быть пустым");
        }
        return value;
    }

    private static final class ManagedModule {
        private final PluginModule module;
        private final ModuleContext context;
        private final ModuleLifecycle lifecycle;
        private boolean initializeAttempted;
        private boolean startAttempted;

        private ManagedModule(PluginModule module, ModuleContext context, ModuleLifecycle lifecycle) {
            this.module = module;
            this.context = context;
            this.lifecycle = lifecycle;
        }
    }

    /** Ошибка bootstrap с первопричиной и возможными ошибками отката. */
    public static final class BootstrapException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private BootstrapException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
