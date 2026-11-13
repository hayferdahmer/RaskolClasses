// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

class BootstrapCoordinatorTest {

    private static final Logger LOGGER = Logger.getLogger(BootstrapCoordinatorTest.class.getName());

    @Test
    void executesGlobalPhasesInDependencyOrderAndStopsInReverseOrder() {
        List<String> events = new ArrayList<>();
        ServiceRegistry registry = new ServiceRegistry();
        BootstrapCoordinator coordinator = coordinator(registry);

        coordinator.start(List.of(
                module("presentation", Set.of("domain"), events),
                module("domain", Set.of(), events)));

        assertEquals(List.of("domain", "presentation"), coordinator.moduleOrder());
        assertEquals(
                List.of(
                        "initialize:domain",
                        "initialize:presentation",
                        "register:domain",
                        "register:presentation",
                        "start:domain",
                        "start:presentation"),
                events);
        assertTrue(registry.isSealed());
        assertFalse(registry.isClosed());

        coordinator.stop();
        coordinator.stop();

        assertEquals(
                List.of(
                        "initialize:domain",
                        "initialize:presentation",
                        "register:domain",
                        "register:presentation",
                        "start:domain",
                        "start:presentation",
                        "stop:presentation",
                        "close:presentation",
                        "stop:domain",
                        "close:domain"),
                events);
        assertTrue(registry.isClosed());
        assertTrue(coordinator.isClosed());
    }

    @Test
    void validatesTheWholeGraphBeforeCallingAnyModule() {
        List<String> events = new ArrayList<>();
        ServiceRegistry registry = new ServiceRegistry();
        BootstrapCoordinator coordinator = coordinator(registry);

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.start(List.of(module("combat", Set.of("missing"), events))));

        assertTrue(events.isEmpty());
        assertFalse(registry.isClosed());

        coordinator.start(List.of(module("combat", Set.of(), events)));
        assertTrue(events.contains("start:combat"));
    }

    @Test
    void failedStartRollsBackAttemptedModuleAndEarlierModulesInReverseOrder() {
        List<String> events = new ArrayList<>();
        ServiceRegistry registry = new ServiceRegistry();
        BootstrapCoordinator coordinator = coordinator(registry);
        PluginModule first = module("a", Set.of(), events);
        PluginModule second = new RecordingModule("b", Set.of("a"), events) {
            @Override
            public void start() {
                events.add("start:b");
                throw new IllegalStateException("start failure");
            }
        };

        BootstrapCoordinator.BootstrapException failure = assertThrows(
                BootstrapCoordinator.BootstrapException.class,
                () -> coordinator.start(List.of(second, first)));

        assertEquals("start failure", failure.getCause().getMessage());
        assertEquals(
                List.of(
                        "initialize:a",
                        "initialize:b",
                        "register:a",
                        "register:b",
                        "start:a",
                        "start:b",
                        "stop:b",
                        "close:b",
                        "stop:a",
                        "close:a"),
                events);
        assertTrue(registry.isClosed());
        assertTrue(coordinator.isClosed());
    }

    @Test
    void failedInitializationClosesPartialModuleWithoutCallingStop() {
        List<String> events = new ArrayList<>();
        ServiceRegistry registry = new ServiceRegistry();
        BootstrapCoordinator coordinator = coordinator(registry);
        PluginModule broken = new RecordingModule("broken", Set.of(), events) {
            @Override
            public void initialize(ModuleContext context) {
                events.add("initialize:broken");
                throw new IllegalStateException("initialize failure");
            }
        };

        assertThrows(
                BootstrapCoordinator.BootstrapException.class,
                () -> coordinator.start(List.of(broken)));

        assertEquals(List.of("initialize:broken", "close:broken"), events);
        assertTrue(registry.isClosed());
    }

    @Test
    void cleanupContinuesAfterCloseFailureAndStillClosesTaskScope() {
        List<String> events = new ArrayList<>();
        List<TaskScope> scopes = new ArrayList<>();
        ServiceRegistry registry = new ServiceRegistry();
        BootstrapCoordinator coordinator = coordinator(registry);
        PluginModule first = new RecordingModule("a", Set.of(), events) {
            @Override
            public void initialize(ModuleContext context) {
                super.initialize(context);
                scopes.add(context.taskScope());
            }
        };
        PluginModule second = new RecordingModule("b", Set.of("a"), events) {
            @Override
            public void initialize(ModuleContext context) {
                super.initialize(context);
                scopes.add(context.taskScope());
            }

            @Override
            public void close() {
                events.add("close:b");
                throw new IllegalStateException("close failure");
            }
        };

        coordinator.start(List.of(first, second));

        assertThrows(RuntimeException.class, coordinator::stop);

        assertTrue(events.contains("stop:a"));
        assertTrue(events.contains("close:a"));
        assertTrue(scopes.stream().allMatch(TaskScope::isClosed));
        assertTrue(registry.isClosed());
    }

    @Test
    void coordinatorCannotBeStartedTwiceAndCloseIsIdempotent() {
        ServiceRegistry registry = new ServiceRegistry();
        BootstrapCoordinator coordinator = coordinator(registry);
        List<String> events = new ArrayList<>();

        coordinator.start(List.of(module("only", Set.of(), events)));

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.start(List.of(module("other", Set.of(), events))));

        coordinator.close();
        coordinator.close();

        assertTrue(coordinator.isClosed());
        assertTrue(registry.isClosed());
    }

    private static BootstrapCoordinator coordinator(ServiceRegistry registry) {
        return new BootstrapCoordinator("RaskolClasses", LOGGER, Path.of("."), registry);
    }

    private static PluginModule module(String id, Set<String> dependencies, List<String> events) {
        return new RecordingModule(id, dependencies, events);
    }

    private static class RecordingModule implements PluginModule {

        private final String id;
        private final Set<String> dependencies;
        private final List<String> events;

        private RecordingModule(String id, Set<String> dependencies, List<String> events) {
            this.id = id;
            this.dependencies = dependencies;
            this.events = events;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Set<String> dependencies() {
            return dependencies;
        }

        @Override
        public void initialize(ModuleContext context) {
            events.add("initialize:" + id);
        }

        @Override
        public void register() {
            events.add("register:" + id);
        }

        @Override
        public void start() {
            events.add("start:" + id);
        }

        @Override
        public void stop() {
            events.add("stop:" + id);
        }

        @Override
        public void close() {
            events.add("close:" + id);
        }
    }
}
