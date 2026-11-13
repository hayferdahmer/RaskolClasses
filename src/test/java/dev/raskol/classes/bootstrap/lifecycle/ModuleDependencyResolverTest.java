// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ModuleDependencyResolverTest {

    @Test
    void resolvesDependenciesBeforeDependentsDeterministically() {
        List<PluginModule> ordered = ModuleDependencyResolver.resolve(List.of(
                module("presentation", Set.of("application")),
                module("application", Set.of("domain")),
                module("domain", Set.of()),
                module("independent", Set.of())));

        assertEquals(
                List.of("domain", "application", "independent", "presentation"),
                ordered.stream().map(PluginModule::id).toList());
    }

    @Test
    void rejectsMissingDependency() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleDependencyResolver.resolve(List.of(module("combat", Set.of("character")))));

        assertEquals(
                "Модуль combat зависит от отсутствующего модуля character",
                failure.getMessage());
    }

    @Test
    void rejectsDependencyCycle() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleDependencyResolver.resolve(List.of(
                        module("combat", Set.of("effects")),
                        module("effects", Set.of("combat")))));

        assertEquals(
                "Обнаружен цикл зависимостей модулей: [combat, effects]",
                failure.getMessage());
    }

    @Test
    void cycleReportDoesNotIncludeModulesMerelyBlockedByThatCycle() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleDependencyResolver.resolve(List.of(
                        module("combat", Set.of("effects")),
                        module("effects", Set.of("combat")),
                        module("presentation", Set.of("combat")))));

        assertEquals(
                "Обнаружен цикл зависимостей модулей: [combat, effects]",
                failure.getMessage());
    }

    @Test
    void rejectsSelfDependency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModuleDependencyResolver.resolve(List.of(module("combat", Set.of("combat")))));
    }

    @Test
    void rejectsDuplicateModuleIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModuleDependencyResolver.resolve(List.of(
                        module("combat", Set.of()),
                        module("combat", Set.of()))));
    }

    @Test
    void rejectsBlankModuleId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModuleDependencyResolver.resolve(List.of(module(" ", Set.of()))));
    }

    private static PluginModule module(String id, Set<String> dependencies) {
        return new TestModule(id, dependencies);
    }

    private record TestModule(String id, Set<String> dependencies) implements PluginModule {

        @Override
        public void initialize(ModuleContext context) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void close() {}
    }
}
